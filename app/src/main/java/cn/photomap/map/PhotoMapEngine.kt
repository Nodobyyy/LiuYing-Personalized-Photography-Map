package cn.photomap.map

import android.content.Context
import android.graphics.*
import cn.photomap.LocalMapBundle
import cn.photomap.BuildConfig
import cn.photomap.PhotoMapApplication
import cn.photomap.core.PhotoCluster
import cn.photomap.core.PhotoClustering
import cn.photomap.core.PhotoPoint
import cn.photomap.core.MapViewport
import cn.photomap.data.Library
import cn.photomap.data.Place
import kotlinx.coroutines.*
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*
import java.security.MessageDigest
import kotlin.math.abs

class PhotoMapEngine(private val context: Context, private val scope: CoroutineScope, private val library: Library,
    private val packages: MapPackages, private val onSelect: (List<Place>) -> Unit, private val onStatus: (String) -> Unit,
    private val onPickContext: (String,Boolean)->Unit={_,_->},private val onMapInfo: (String)->Unit={}) {
    val view: MapView
    private var map: MapLibreMap?=null
    private var style: Style?=null
    private var base: LocalMapBundle.Loaded?=null
    private var satellite: JSONObject?=null
    private var activeSatellite: JSONObject?=null
    private var fineSatelliteLoaded=false
    private var contextStyle: JSONObject?=null
    private val appearancePrefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
    var basemapMode=runCatching {BasemapMode.valueOf(appearancePrefs.getString("basemap","STREETS")!!)}.getOrDefault(BasemapMode.STREETS);private set
    var satelliteLabels=appearancePrefs.getBoolean("satellite-labels",false);private set
    private var regional=emptyList<RegionalMap>()
    private val loadedPackages=mutableMapOf<String,LocalMapBundle.Loaded>()
    private val china=ChinaView(context)
    private val urban by lazy {UrbanCoverage(context)}
    private var selectedMap=""
    private var dark=false
    private var applying=false
    private var job: Job?=null
    private var items=emptyList<Place>()
    private var icons=emptySet<String>()
    private var displayed=emptyList<PhotoCluster>()
    private val seed=kotlin.random.Random.nextInt()
    private var selection: String?=null
    private var revision=0
    private var fitFirstView=false
    var suggestedName="";private set
    val canPick get()=MapViewport.inCoverage(center.latitude,center.longitude)
    var picking=false
        set(value) {
            field=value
            style?.getLayer("user-photo-covers")?.setProperties(visibility(if(value) Property.NONE else Property.VISIBLE))
            if(value) refreshPickHint() else refresh()
        }
    val center: LatLng get()=map?.cameraPosition?.target ?: LatLng(35.0,105.0)
    init { MapLibre.getInstance(context);if(BuildConfig.ONLINE_MAPS) MapNetwork.configure(context); view=MapView(context,MapLibreMapOptions.createFromAttributes(context).localIdeographFontFamilyEnabled(true).localIdeographFontFamily("sans-serif"));view.setBackgroundColor(Color.rgb(233,236,231)) }
    fun create(saved: android.os.Bundle?) {
        if(BuildConfig.ONLINE_MAPS) MapNetwork.configureCache(context)
        view.onCreate(saved)
        view.getMapAsync { ready ->
            map=ready
            ready.uiSettings.isRotateGesturesEnabled=false; ready.uiSettings.isTiltGesturesEnabled=false
            ready.uiSettings.isScrollGesturesEnabled=true;ready.uiSettings.isZoomGesturesEnabled=true;ready.uiSettings.isDoubleTapGesturesEnabled=true
            ready.uiSettings.isLogoEnabled=false;ready.uiSettings.isAttributionEnabled=false
            ready.setMaxZoomPreference(17.0)
            // Native gesture constraint is applied while panning. Never move the
            // camera from idle callbacks: that caused a visible jump after release.
            ready.setLatLngBoundsForCameraTarget(LatLngBounds.from(60.0,144.0,-2.0,66.0))
            val prefs=context.getSharedPreferences("map-camera",Context.MODE_PRIVATE)
            fitFirstView=!prefs.contains("zoom")
            ready.cameraPosition=CameraPosition.Builder().target(LatLng(prefs.getFloat("lat",35f).toDouble(),prefs.getFloat("lon",105f).toDouble())).zoom(prefs.getFloat("zoom",2f).toDouble()).build()
            ready.addOnCameraIdleListener {
                ready.cameraPosition.let { c->c.target?.let { p->prefs.edit().putFloat("lat",p.latitude.toFloat()).putFloat("lon",p.longitude.toFloat()).putFloat("zoom",c.zoom.toFloat()).apply() } }
                chooseMap(); refresh();refreshPickHint();refreshMapInfo()
            }
            ready.addOnMapClickListener { coordinate ->
                if(picking) {ready.animateCamera(CameraUpdateFactory.newLatLng(coordinate));true} else {
                    val p=ready.projection.toScreenLocation(coordinate)
                    // Project the geographic anchor again so hit testing remains correct during camera animation.
                    val lookup=items.associateBy { it.id }
                    val hit=displayed.minByOrNull { c->val a=lookup[c.members.first().id]?.let { ready.projection.toScreenLocation(LatLng(it.latitude,it.longitude)) }; if(a==null) Double.MAX_VALUE else (a.x-p.x).toDouble()*(a.x-p.x)+(a.y-p.y).toDouble()*(a.y-p.y) }
                    val anchor=hit?.members?.firstOrNull()?.id?.let { lookup[it] }?.let { ready.projection.toScreenLocation(LatLng(it.latitude,it.longitude)) }
                    val hitDp=maxOf(22.0,MapViewport.markerDp(ready.cameraPosition.zoom)*144/128/2+4)
                    if(hit!=null && anchor!=null && abs(anchor.x-p.x)<hitDp*context.resources.displayMetrics.density && abs(anchor.y-p.y)<hitDp*context.resources.displayMetrics.density) {
                        val members=hit.members.mapNotNull { lookup[it.id] }; val positions=members.map { LatLng(it.latitude,it.longitude) }
                        if(members.size==1 || positions.distinct().size<2 || ready.cameraPosition.zoom>=16) onSelect(members)
                        else ready.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(positions).build(),100))
                        true
                    } else false
                }
            }
            scope.launch {
                val result=withContext(Dispatchers.IO) { runCatching { if(BuildConfig.ONLINE_MAPS) OnlineMapAssets.load(context) else LocalMapBundle.load(context) } }
                base=result.getOrNull(); regional=if(BuildConfig.ONLINE_MAPS)emptyList() else withContext(Dispatchers.IO) { packages.list() }
                satellite=if(BuildConfig.ONLINE_MAPS) JSONObject(context.assets.open("maps/satellite-online.json").bufferedReader().use {it.readText()}) else base?.directory?.let {dir->withContext(Dispatchers.IO) {runCatching {JSONObject(LocalMapBundle.loadDirectory(dir,verifyFiles=false,styleName="style-satellite.json").style)}.getOrNull()}}
                activeSatellite=satellite
                contextStyle=if(BuildConfig.ONLINE_MAPS)null else base?.directory?.let {dir->withContext(Dispatchers.IO) {runCatching {JSONObject(LocalMapBundle.loadDirectory(dir,verifyFiles=false,styleName="style-context.json").style)}.getOrNull()}}
                if(result.isFailure) onStatus("底图校验失败，请检查应用资源")
                applyStyle(base, if(BuildConfig.ONLINE_MAPS) "online" else "base")
            }
            view.post {updateViewportLimits()}
        }
        view.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->updateViewportLimits()}
    }
    fun update(places: List<Place>, selected: String?) { items=places; selection=selected; revision++; refresh() }
    fun basemap(mode: BasemapMode) {if(basemapMode!=mode) {basemapMode=mode;appearancePrefs.edit().putString("basemap",mode.name).apply();selectedMap="";chooseMap(true);refreshMapInfo()}}
    fun satelliteLabels(show:Boolean) {if(satelliteLabels!=show){satelliteLabels=show;appearancePrefs.edit().putBoolean("satellite-labels",show).apply();selectedMap="";chooseMap(true)}}
    fun theme(isDark: Boolean) { if(dark!=isDark) { dark=isDark; selectedMap=""; chooseMap(force=true) } }
    fun reloadPackages() { scope.launch { regional=withContext(Dispatchers.IO) { packages.list() };loadedPackages.clear(); selectedMap=""; chooseMap(true) } }
    private fun nationalCamera()=CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().include(LatLng(54.0,136.0)).include(LatLng(3.0,73.0)).build(),36)
    fun national() { map?.animateCamera(nationalCamera()) }
    fun connectivityChanged() {refreshMapInfo()}
    fun focus(latitude: Double, longitude: Double) { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude,longitude),maxOf(10.0,map?.cameraPosition?.zoom ?: 10.0))) }
    private fun chooseMap(force: Boolean=false) {
        val ready=map ?: return; if(applying) return
        if(BuildConfig.ONLINE_MAPS) {if(selectedMap!="online" || force){activeSatellite=satellite;fineSatelliteLoaded=true;applyStyle(base,"online")};return}
        val bounds=ready.projection.visibleRegion.latLngBounds; val zoom=ready.cameraPosition.zoom
        val target=center
        val candidate=regional.filter { zoom>=it.minZoom && target.longitude in it.west..it.east && target.latitude in it.south..it.north }.minByOrNull { (it.east-it.west)*(it.north-it.south) }
        val key=candidate?.directory?.name ?: "base"
        if(key==selectedMap && !force) return
        applying=true
        scope.launch {
            val loaded=if(candidate==null) base else loadedPackages[key] ?: withContext(Dispatchers.IO) {
                // Private packages already pass streamed SHA-256 verification
                // before their atomic import. Reopening must not rehash 16GB.
                runCatching {LocalMapBundle.loadDirectory(candidate.directory,verifyFiles=false)}.getOrNull()
            }?.also {loadedPackages[key]=it}
            val fine=if(candidate==null) null else loadedPackages["sat:$key"] ?: withContext(Dispatchers.IO) {
                runCatching {LocalMapBundle.loadDirectory(candidate.directory,verifyFiles=false,styleName="style-satellite.json")}.getOrNull()
            }?.also {loadedPackages["sat:$key"]=it}
            activeSatellite=satellite?.let {JSONObject(it.toString())}
            fineSatelliteLoaded=fine!=null
            if(fine!=null) {
                val extra=JSONObject(fine.style)
                if(activeSatellite==null) activeSatellite=extra else {
                    val sources=extra.getJSONObject("sources");sources.keys().forEach {activeSatellite!!.getJSONObject("sources").put(it,sources.get(it))}
                    val layers=extra.getJSONArray("layers");for(i in 0 until layers.length())activeSatellite!!.getJSONArray("layers").put(layers.get(i))
                }
            }
            applyStyle(loaded,key)
        }
    }
    private fun applyStyle(bundle: LocalMapBundle.Loaded?, key: String) {
        val ready=map ?: return
        job?.cancel(); style=null; icons=emptySet(); selectedMap=key; applying=true
        val json=JSONObject(bundle?.style ?: """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#e9ece7"}}]}""")
        if(bundle!=null) {MapAppearance.apply(json,basemapMode,activeSatellite,contextStyle);ChinaMapLabels.apply(json,china)}
        if(dark) {
            val layers=json.getJSONArray("layers")
            for(i in 0 until layers.length()) {
                val layer=layers.getJSONObject(i)
                val paint=layer.optJSONObject("paint") ?: continue
                paint.keys().asSequence().toList().forEach { property ->
                    val value=paint.opt(property)
                    if(property.endsWith("color") && value is String && value.startsWith('#')) runCatching {
                        val hsv=FloatArray(3); Color.colorToHSV(Color.parseColor(value),hsv); hsv[1]*=0.45f; hsv[2]=0.12f+(1-hsv[2])*0.55f
                        val color=when(property) {
                            "background-color"->"#172a32"
                            "text-color"->"#d7e2da"
                            "text-halo-color"->"#1d2922"
                            "line-color"->when(layer.optString("source-layer")) {"roads"->"#5d7465";"boundaries"->"#8a9a8f";else->"#%06x".format(Color.HSVToColor(hsv) and 0xffffff)}
                            "fill-color"->if(layer.optString("id")=="china-display-mask") "#172a32" else when(layer.optString("source-layer")) {"water"->"#172f3b";"earth"->"#202d25";"landcover","landuse"->"#2b3b2e";else->"#%06x".format(Color.HSVToColor(hsv) and 0xffffff)}
                            else->"#%06x".format(Color.HSVToColor(hsv) and 0xffffff)
                        }
                        paint.put(property,color)
                    }
                }
            }
        }
        if(basemapMode==BasemapMode.SATELLITE) {
            val layers=json.getJSONArray("layers")
            for(i in 0 until layers.length()) {
                val layer=layers.getJSONObject(i);val paint=layer.optJSONObject("paint") ?: continue
                if(layer.optString("id")=="china-display-mask") paint.put("fill-color","#19303d")
                if(layer.optString("type")=="symbol") paint.put("text-color","#ffffff").put("text-halo-color","#172b32").put("text-halo-width",1.0)
                if(!satelliteLabels && (layer.optString("type")=="symbol" || layer.optString("source")=="base")) layer.put("layout",(layer.optJSONObject("layout")?:JSONObject()).put("visibility","none"))
                if(layer.optString("id").startsWith("china-") && layer.optString("type")=="line") paint.put("line-color","#dbe4cc")
            }
        }
        ready.setStyle(Style.Builder().fromJson(json.toString())) { loaded ->
            style=loaded; applying=false
            loaded.addSource(GeoJsonSource("user-photos",FeatureCollection.fromFeatures(emptyArray<Feature>())))
            val density=context.resources.displayMetrics.density
            loaded.addLayer(SymbolLayer("user-photo-covers","user-photos").withProperties(iconImage(get("icon")),iconAllowOverlap(true),iconIgnorePlacement(true),
                visibility(if(picking) Property.NONE else Property.VISIBLE),
                iconSize(interpolate(linear(),zoom(),stop(3,28f/128*density),stop(8,32f/128*density),stop(13,40f/128*density),stop(16,48f/128*density)))))
            val maxZoom=json.optJSONObject("sources")?.let { sources->sources.keys().asSequence().map {sources.getJSONObject(it)}.filter {it.optString("type")=="vector"}.map {it.optDouble("maxzoom",12.0)}.maxOrNull() } ?: 0.0
            (context.applicationContext as? PhotoMapApplication)?.startupDiagnostics?.mapReady()
            onStatus(if(bundle==null) "未加载底图；摄影记录仍保存在本机" else if(BuildConfig.ONLINE_MAPS) "OpenFreeMap 在线街道 · EOX 2016 卫星 · 地图缓存上限 128 MiB · 照片与作品全部本地保存" else "${bundle.attribution} · ${if(key=="base") "内置全国概览" else "已导入精细图"} · 街道数据最高 Z${maxZoom.toInt()}${if(fineSatelliteLoaded) " · 含 ${urban.count} 个城区精细影像" else ""}")
            if(fitFirstView) {fitFirstView=false;view.post {ready.moveCamera(nationalCamera())}}
            refresh();refreshPickHint();refreshMapInfo();chooseMap()
        }
    }
    private fun refresh() {
        val ready=map ?: return; val currentStyle=style ?: return
        job?.cancel(); val snapshotItems=items.associateBy { it.id }
        val points=items.filter {MapViewport.inCoverage(it.latitude,it.longitude)}.map { p->ready.projection.toScreenLocation(LatLng(p.latitude,p.longitude)).let { PhotoPoint(p.id,it.x.toDouble(),it.y.toDouble(),p.photoCount) } }
        val density=context.resources.displayMetrics.density
        val clusterRadius=MapViewport.clusterRadiusDp(ready.cameraPosition.zoom)*density
        val markerDp=kotlin.math.round(MapViewport.markerDp(ready.cameraPosition.zoom)).toFloat()
        job=scope.launch {
            val clusters=withContext(Dispatchers.Default) { PhotoClustering.cluster(points,clusterRadius,seed) }
            val visible=clusters.filter { it.x in -80.0*density..view.width+80.0*density && it.y in -80.0*density..view.height+80.0*density }
            fun id(c: PhotoCluster): String {
                val cover=snapshotItems.getValue(c.coverId).cover
                val signature=c.key+":"+cover.id+":"+cover.hash+":"+c.photoCount+":"+c.members.any {it.id==selection}+":"+markerDp
                return "u-"+MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
            }
            val names=visible.map { id(it) }.toSet()
            val images=withContext(Dispatchers.IO) { visible.filter { id(it) !in icons }.associate { c->
                val place=snapshotItems.getValue(c.coverId); val file=library.file(place.cover.thumbnail)
                val cover=BitmapFactory.decodeFile(file.path)
                id(c) to marker(cover,c.photoCount,c.members.any { it.id==selection },markerDp).also { cover?.recycle() }
            } }
            ensureActive(); images.forEach { (key,image)->currentStyle.addImage(key,image) }
            currentStyle.getSourceAs<GeoJsonSource>("user-photos")?.setGeoJson(FeatureCollection.fromFeatures(visible.map { c->
                val place=snapshotItems.getValue(c.members.first().id)
                Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(place.longitude,place.latitude)).apply { addStringProperty("icon",id(c)) }
            }))
            (icons-names).forEach { currentStyle.removeImage(it) }; icons=names; displayed=visible
        }
    }
    private fun updateViewportLimits() {
        val ready=map ?: return
        if(view.width==0 || view.height==0) return
        val density=context.resources.displayMetrics.density.toDouble();val width=view.width/density;val height=view.height/density
        val minimum=MapViewport.minimumZoom(width,height);ready.setMinZoomPreference(minimum)
    }
    private fun refreshPickHint() {
        if(!picking) return
        val ready=map ?: return;val target=center
        val allowed=MapViewport.inCoverage(target.latitude,target.longitude)
        if(!allowed) {suggestedName="";onPickContext("当前位置超出地图数据范围",false);return}
        val anchor=ready.projection.toScreenLocation(target);val radius=24*context.resources.displayMetrics.density
        val layers=listOf("urban-poi-labels","urban-road-labels","urban-roads","poi-labels","road-labels","roads","county-labels","place-labels","locality-labels").filter {style?.getLayer(it)!=null}.toTypedArray()
        val features=if(layers.isEmpty()) emptyList() else ready.queryRenderedFeatures(RectF(anchor.x-radius,anchor.y-radius,anchor.x+radius,anchor.y+radius),*layers)
        suggestedName=features.firstNotNullOfOrNull {feature->listOf("name:zh-Hans","name:zh","name","name:en").firstNotNullOfOrNull {key->runCatching {feature.getStringProperty(key)}.getOrNull()?.takeIf {it.isNotBlank()}}} ?: ""
        val coordinates="%.6f, %.6f".format(target.latitude,target.longitude)
        val detail=if(BuildConfig.ONLINE_MAPS) {if(basemapMode==BasemapMode.SATELLITE) "\n2016 年卫星影像；精细选点可切换街道图" else ""} else if(basemapMode==BasemapMode.SATELLITE) "\n"+if(fineSatelliteLoaded && urban.contains(target.latitude,target.longitude)) "2016 年城区影像；精细选点可切换街道图" else "当前为概览影像，精细选点请切换街道图" else if(selectedMap=="base" && ready.cameraPosition.zoom>10) "\n尚未导入精细包，当前为概览图" else ""
        onPickContext((if(suggestedName.isBlank()) coordinates else "附近：$suggestedName\n$coordinates")+detail,true)
    }
    private fun refreshMapInfo() {
        val ready=map ?: return
        if(BuildConfig.ONLINE_MAPS) {
            onMapInfo(if(!MapNetwork.connected(context)) "当前无网络，仅显示已缓存的地图\n照片和作品仍可在本机查看与记录" else if(basemapMode==BasemapMode.SATELLITE) "联网卫星影像：2016 年 · 约 10 米\n${if(ready.cameraPosition.zoom>14) "已超过影像清晰度；可切换街道图" else "仅加载地图，照片与作品不上传"}" else "")
            return
        }
        val citySatellite=urban.contains(center.latitude,center.longitude)
        val text=when {
            basemapMode==BasemapMode.SATELLITE && activeSatellite==null->"卫星影像尚未加载"
            basemapMode==BasemapMode.SATELLITE && !fineSatelliteLoaded->"尚未导入精细包，当前为概览影像\n导入全国离线包后可查看城区精细影像"
            basemapMode==BasemapMode.SATELLITE && citySatellite->"卫星影像：2016 年 · 城区约 10 米\n${if(ready.cameraPosition.zoom>14) "已超过影像清晰度；选点可切换街道图" else if(satelliteLabels) "已叠加离线道路和地名" else "可在底图选项中叠加道路和地名"}"
            basemapMode==BasemapMode.SATELLITE->"卫星影像：2016 年 · 全国概览\n${if(ready.cameraPosition.zoom>7) "此处无精细影像，放大不会增加细节" else "适合查看山川地貌；精细选点请用街道图"}"
            selectedMap=="base" && ready.cameraPosition.zoom>10->"尚未导入精细包，当前为概览图\n导入全国离线包可显示更多街道与景点"
            else->""
        }
        onMapInfo(text)
    }
    private fun marker(photo: Bitmap?, count: Long, selected: Boolean,sizeDp: Float): Bitmap {
        val result=Bitmap.createBitmap(128,144,Bitmap.Config.ARGB_8888); val c=Canvas(result); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=if(selected) Color.rgb(218,170,92) else Color.WHITE; c.drawRoundRect(RectF(2f,2f,126f,130f),16f,16f,p)
        c.save(); val path=Path().apply { addRoundRect(RectF(8f,8f,120f,124f),12f,12f,Path.Direction.CW) }; c.clipPath(path)
        if(photo!=null) { val scale=maxOf(112f/photo.width,116f/photo.height); val w=photo.width*scale; val h=photo.height*scale; c.drawBitmap(photo,null,RectF(64-w/2,66-h/2,64+w/2,66+h/2),p) }
        c.restore()
        val label=when {count<1000->count.toString();count<1_000_000->"${count/1000}k";count<1_000_000_000->"${count/1_000_000}m";else->">1b"}
        val pixelPerDp=128f/sizeDp;p.textSize=11*pixelPerDp
        val padding=8*pixelPerDp
        if(p.measureText(label)>124-padding) p.textSize*=((124-padding)/p.measureText(label))
        val width=maxOf(18*pixelPerDp,p.measureText(label)+padding).coerceAtMost(124f)
        val top=140-14*pixelPerDp
        p.color=Color.rgb(36,59,50);c.drawRoundRect(RectF(126-width,top,126f,140f),6*pixelPerDp,6*pixelPerDp,p)
        p.color=Color.WHITE;p.textAlign=Paint.Align.CENTER;c.drawText(label,126-width/2,top+(140-top-(p.descent()+p.ascent()))/2,p);return result
    }
    fun destroy() { job?.cancel(); view.onDestroy() }
}
