package cn.photomap.map

import android.content.Context
import android.graphics.*
import android.widget.FrameLayout
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
import kotlin.math.log2
import kotlin.math.hypot

class PhotoMapEngine(private val context: Context, private val scope: CoroutineScope, private val library: Library,
    private val packages: MapPackages, private val onSelect: (List<Place>) -> Unit, private val onStatus: (String) -> Unit,
    private val onPickContext: (String,Boolean)->Unit={_,_->},private val onMapInfo: (String)->Unit={}) {
    val view: MapView
    val mapContainer: FrameLayout
    private val globeBackdrop = if (BuildConfig.ONLINE_MAPS) GlobeBackdrop(context) else null
    private val globeBorders by lazy { GlobeBorders(context) }
    private var map: MapLibreMap?=null
    private var style: Style?=null
    private var base: LocalMapBundle.Loaded?=null
    private var satellite: JSONObject?=null
    private var activeSatellite: JSONObject?=null
    private var fineSatelliteLoaded=false
    var hasOnlineOverview = false
        private set
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
    private var globeSpin: GlobeSpin? = null
    private var searchSelection: Pair<LatLng, String>? = null
    var suggestedName="";private set
    private fun inCoverage(latitude: Double, longitude: Double) =
        cn.photomap.data.PhotoPoint(latitude, longitude).inCoverage
    val canPick get()=inCoverage(center.latitude,center.longitude)
    var picking=false
        set(value) {
            field=value
            if (value) stopGlobeSpin() else searchSelection = null
            style?.getLayer("user-photo-covers")?.setProperties(visibility(if(value) Property.NONE else Property.VISIBLE))
            if(value) refreshPickHint() else refresh()
        }
    val center: LatLng get()=(map?.cameraPosition?.target ?: LatLng(35.0,105.0)).let { if (BuildConfig.ONLINE_MAPS) it.wrap() else it }
    init {
        MapLibre.getInstance(context)
        if (BuildConfig.ONLINE_MAPS) MapNetwork.configure(context, china)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .localIdeographFontFamilyEnabled(true).localIdeographFontFamily("sans-serif")
        // The transparent texture lets the globe show behind native map labels and markers.
        if (BuildConfig.ONLINE_MAPS) options.textureMode(true).translucentTextureSurface(true)
        view = MapView(context, options)
        view.setBackgroundColor(if (BuildConfig.ONLINE_MAPS) Color.TRANSPARENT else Color.rgb(233,236,231))
        mapContainer = FrameLayout(context).apply {
            if (globeBackdrop != null) {
                setBackgroundColor(Color.rgb(4, 10, 22))
                addView(globeBackdrop)
            }
            addView(view)
        }
    }
    fun create(saved: android.os.Bundle?) {
        if(BuildConfig.ONLINE_MAPS) MapNetwork.configureCache(context)
        view.onCreate(saved)
        view.getMapAsync { ready ->
            map=ready
            ready.uiSettings.isRotateGesturesEnabled=false; ready.uiSettings.isTiltGesturesEnabled=false
            ready.uiSettings.isScrollGesturesEnabled=true;ready.uiSettings.isZoomGesturesEnabled=true;ready.uiSettings.isDoubleTapGesturesEnabled=true
            ready.uiSettings.isLogoEnabled=false;ready.uiSettings.isAttributionEnabled=false
            if (BuildConfig.ONLINE_MAPS) ready.uiSettings.isCompassEnabled = false
            ready.setMaxZoomPreference(17.0)
            if (BuildConfig.ONLINE_MAPS) {
                ready.setTileCacheEnabled(true)
                // Load a coarse parent first so a zoom change can keep drawing
                // a complete globe while the new visible tiles are prepared.
                ready.setPrefetchZoomDelta(4)
                ready.addOnCameraMoveListener { globeBackdrop?.cameraChanged() }
                globeSpin = GlobeSpin(view, ready, {
                    val camera = ready.cameraPosition
                    val latitude = camera.target?.latitude ?: 0.0
                    !picking && style != null && GlobeCamera.equatorialZoom(camera.zoom, latitude) <= globeMinimum() + .1
                }, ::refresh, { cameraSettled(ready) })
            }
            // Native gesture constraint is applied while panning. Never move the
            // camera from idle callbacks: that caused a visible jump after release.
            if (!BuildConfig.ONLINE_MAPS) ready.setLatLngBoundsForCameraTarget(LatLngBounds.from(60.0,144.0,-2.0,66.0))
            val prefs=context.getSharedPreferences("map-camera",Context.MODE_PRIVATE)
            fitFirstView=!prefs.contains("zoom")
            ready.cameraPosition=CameraPosition.Builder().target(LatLng(prefs.getFloat("lat",35f).toDouble(),prefs.getFloat("lon",105f).toDouble())).zoom(prefs.getFloat("zoom",2f).toDouble()).bearing(0.0).tilt(0.0).build()
            ready.addOnCameraIdleListener {
                globeBackdrop?.cameraChanged()
                if (globeSpin?.inMotion == true) return@addOnCameraIdleListener
                cameraSettled(ready)
            }
            ready.addOnMapClickListener { coordinate ->
                if(picking) {ready.animateCamera(CameraUpdateFactory.newLatLng(coordinate));true} else {
                    val p=ready.projection.toScreenLocation(coordinate)
                    // Project the geographic anchor again so hit testing remains correct during camera animation.
                    val lookup=items.associateBy { it.id }
                    val hit=displayed.minByOrNull { c->val a=lookup[c.members.first().id]?.let { ready.projection.toScreenLocation(LatLng(it.latitude,it.longitude)) }; if(a==null) Double.MAX_VALUE else (a.x-p.x).toDouble()*(a.x-p.x)+(a.y-p.y).toDouble()*(a.y-p.y) }
                    val anchor=hit?.members?.firstOrNull()?.id?.let { lookup[it] }?.let { ready.projection.toScreenLocation(LatLng(it.latitude,it.longitude)) }
                    val hitDp=maxOf(22.0,photoMarkerDp(ready.cameraPosition.zoom)*144/128/2+4)
                    if(hit!=null && anchor!=null && abs(anchor.x-p.x)<hitDp*context.resources.displayMetrics.density && abs(anchor.y-p.y)<hitDp*context.resources.displayMetrics.density) {
                        val members=hit.members.mapNotNull { lookup[it.id] }; val positions=members.map { LatLng(it.latitude,it.longitude) }
                        if(members.size==1 || positions.distinct().size<2 || ready.cameraPosition.zoom>=16) onSelect(members)
                        else ready.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(positions).build(),100))
                        true
                    } else false
                }
            }
            scope.launch {
                if (BuildConfig.ONLINE_MAPS) {
                    val result = withContext(Dispatchers.IO) { runCatching { OnlineMapAssets.load(context) } }
                    val loaded = result.getOrNull()
                    base = loaded?.streets
                    satellite = loaded?.satellite
                    hasOnlineOverview = loaded?.hasOverview == true
                    activeSatellite = satellite
                    if (result.isFailure) onStatus("底图校验失败，请检查应用资源")
                    applyStyle(base, "online")
                    return@launch
                }
                val result=withContext(Dispatchers.IO) { runCatching { LocalMapBundle.load(context) } }
                base=result.getOrNull(); regional=withContext(Dispatchers.IO) { packages.list() }
                satellite=base?.directory?.let {dir->withContext(Dispatchers.IO) {runCatching {JSONObject(LocalMapBundle.loadDirectory(dir,verifyFiles=false,styleName="style-satellite.json").style)}.getOrNull()}}
                activeSatellite=satellite
                contextStyle=base?.directory?.let {dir->withContext(Dispatchers.IO) {runCatching {JSONObject(LocalMapBundle.loadDirectory(dir,verifyFiles=false,styleName="style-context.json").style)}.getOrNull()}}
                if(result.isFailure) onStatus("底图校验失败，请检查应用资源")
                applyStyle(base, "base")
            }
            view.post {
                updateViewportLimits()
                if (BuildConfig.ONLINE_MAPS && fitFirstView) {
                    fitFirstView = false
                    ready.moveCamera(overviewCamera())
                }
            }
        }
        view.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->updateViewportLimits()}
    }
    private fun cameraSettled(ready: MapLibreMap) {
        val camera = ready.cameraPosition
        camera.target?.let { raw ->
            val point = if (BuildConfig.ONLINE_MAPS) raw.wrap() else raw
            context.getSharedPreferences("map-camera", Context.MODE_PRIVATE).edit()
                .putFloat("lat", point.latitude.toFloat()).putFloat("lon", point.longitude.toFloat())
                .putFloat("zoom", camera.zoom.toFloat()).apply()
        }
        chooseMap()
        refresh()
        refreshPickHint()
        refreshMapInfo()
    }
    fun update(places: List<Place>, selected: String?) { items=places; selection=selected; revision++; refresh() }
    fun basemap(mode: BasemapMode) {if(basemapMode!=mode) {basemapMode=mode;appearancePrefs.edit().putString("basemap",mode.name).apply();selectedMap="";chooseMap(true);refreshMapInfo()}}
    fun satelliteLabels(show:Boolean) {if(satelliteLabels!=show){satelliteLabels=show;appearancePrefs.edit().putBoolean("satellite-labels",show).apply();selectedMap="";chooseMap(true)}}
    fun theme(isDark: Boolean) { if(dark!=isDark) { dark=isDark; selectedMap=""; chooseMap(force=true) } }
    fun reloadPackages() { scope.launch { regional=withContext(Dispatchers.IO) { packages.list() };loadedPackages.clear(); selectedMap=""; chooseMap(true) } }
    private fun nationalCamera()=CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().include(LatLng(54.0,136.0)).include(LatLng(3.0,73.0)).build(),36)
    fun stopGlobeSpin() { globeSpin?.stop() }
    fun national() { stopGlobeSpin(); map?.animateCamera(overviewCamera()) }
    private fun globeMinimum(): Double {
        val width = minOf(view.width, view.height) / context.resources.displayMetrics.density
        return log2((width * .94 * Math.PI / 512).coerceAtLeast(1.0))
    }
    private fun overviewCamera() = if (BuildConfig.ONLINE_MAPS) {
        CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
            .target(LatLng(22.0, 104.0)).zoom(GlobeCamera.zoomAtLatitude(globeMinimum(), 22.0)).bearing(0.0).tilt(0.0).build())
    } else nationalCamera()
    private val occlusionMethod by lazy {
        if (BuildConfig.ONLINE_MAPS) Projection::class.java.getMethod("isLocationOccluded", LatLng::class.java) else null
    }
    fun connectivityChanged() {refreshMapInfo()}
    fun snapshot(onReady: (android.graphics.Bitmap) -> Unit) {
        map?.snapshot { bitmap -> onReady(bitmap) }
    }
    fun focusSearch(latitude: Double, longitude: Double, label: String) {
        stopGlobeSpin()
        val target = LatLng(latitude, longitude)
        searchSelection = target to label
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
        showPickHint(target, label)
    }
    fun focus(latitude: Double, longitude: Double) {
        stopGlobeSpin()
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude,longitude),maxOf(10.0,map?.cameraPosition?.zoom ?: 10.0)))
    }
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
        stopGlobeSpin()
        job?.cancel(); style=null; icons=emptySet(); selectedMap=key; applying=true
        val json=JSONObject(bundle?.style ?: """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#e9ece7"}}]}""")
        if(bundle!=null) {MapAppearance.apply(json,basemapMode,activeSatellite,contextStyle);ChinaMapLabels.apply(json,china,global=BuildConfig.ONLINE_MAPS)}
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
                            "background-color"->"#0a1929"
                            "text-color"->"#d9e9f2"
                            "text-halo-color"->"#0c1b29"
                            "line-color"->when(layer.optString("source-layer")) {"roads"->"#3b5d6e";"boundaries"->"#688d9e";else->"#%06x".format(Color.HSVToColor(hsv) and 0xffffff)}
                            "fill-color"->if(layer.optString("id")=="china-display-mask") "#0a1929" else when(layer.optString("source-layer")) {"water"->"#0a1929";"earth"->"#182c36";"landcover","landuse"->"#1d343e";else->"#%06x".format(Color.HSVToColor(hsv) and 0xffffff)}
                            else->"#%06x".format(Color.HSVToColor(hsv) and 0xffffff)
                        }
                        paint.put(property,color)
                    }
                }
            }
        }
        if(basemapMode==BasemapMode.SATELLITE) {
            // In online mode, remove the overseas OpenFreeMap vector source ("base") when viewing satellite imagery
            // so that MapLibre never wastes bandwidth or connections fetching slow foreign vector tiles.
            // Bundled offline PMTiles ("overview" & "context*") provide instant local roads and place labels!
            if (BuildConfig.ONLINE_MAPS && hasOnlineOverview) {
                json.optJSONObject("sources")?.remove("base")
            }
            val layers=json.getJSONArray("layers")
            val retainedLayers = org.json.JSONArray()
            for(i in 0 until layers.length()) {
                val layer=layers.getJSONObject(i)
                val source = layer.optString("source")
                if (BuildConfig.ONLINE_MAPS && hasOnlineOverview && source == "base") continue
                val paint=layer.optJSONObject("paint")
                if (paint != null) {
                    if(layer.optString("id")=="china-display-mask") paint.put("fill-color","#19303d")
                    if(layer.optString("type")=="symbol") paint.put("text-color","#ffffff").put("text-halo-color","#172b32").put("text-halo-width",1.0)
                    if(layer.optString("id").startsWith("china-") && layer.optString("type")=="line") paint.put("line-color","#dbe4cc")
                }
                val vectorSource = source == "base" || (BuildConfig.ONLINE_MAPS && (source == "overview" || source.startsWith("context")))
                if(!satelliteLabels && (layer.optString("type")=="symbol" || vectorSource)) layer.put("layout",(layer.optJSONObject("layout")?:JSONObject()).put("visibility","none"))
                retainedLayers.put(layer)
            }
            json.put("layers", retainedLayers)
        }
        val isSatellite = basemapMode == BasemapMode.SATELLITE
        if (BuildConfig.ONLINE_MAPS) ready.setPrefetchZoomDelta(if (isSatellite) 0 else 4)
        val borderColor = Color.parseColor(OnlineBoundaries.color(dark, isSatellite))
        val globeOverview = if (BuildConfig.ONLINE_MAPS && bundle != null) {
            OnlineBoundaries.apply(json, dark, isSatellite)
            OnlineMapAssets.prepareGlobeOverview(json, basemapMode, dark)
        } else null
        ready.setStyle(Style.Builder().fromJson(json.toString())) { loaded ->
            style=loaded; applying=false
            if (globeOverview != null) scope.launch {
                val (bitmap, borders) = withContext(Dispatchers.IO) {
                    context.assets.open(globeOverview).use { BitmapFactory.decodeStream(it) } to
                        runCatching { globeBorders }.getOrNull()
                }
                if (style === loaded && bitmap != null) globeBackdrop?.show(ready, bitmap, isSatellite, dark, borders, borderColor)
            }
            loaded.addSource(GeoJsonSource("user-photos",FeatureCollection.fromFeatures(emptyArray<Feature>())))
            val density=context.resources.displayMetrics.density
            loaded.addLayer(SymbolLayer("user-photo-covers","user-photos").withProperties(iconImage(get("icon")),iconAllowOverlap(true),iconIgnorePlacement(true),
                visibility(if(picking) Property.NONE else Property.VISIBLE),
                iconSize(interpolate(linear(),zoom(),stop(3,photoMarkerDp(3.0).toFloat()/128*density),
                    stop(8,photoMarkerDp(8.0).toFloat()/128*density),stop(13,40f/128*density),stop(16,48f/128*density)))))
            val maxZoom=json.optJSONObject("sources")?.let { sources->sources.keys().asSequence().map {sources.getJSONObject(it)}.filter {it.optString("type")=="vector"}.map {it.optDouble("maxzoom",12.0)}.maxOrNull() } ?: 0.0
            (context.applicationContext as? PhotoMapApplication)?.startupDiagnostics?.mapReady()
            onStatus(if(bundle==null) "未加载底图；摄影记录仍保存在本机" else if(BuildConfig.ONLINE_MAPS) {
                val worldZoom = json.optJSONObject("sources")?.optJSONObject("context-detail")?.optInt("maxzoom") ?: 0
                val overview = if (hasOnlineOverview) "内置全球道路 Z$worldZoom、中国街道 Z10 和卫星 Z7 · " else ""
                "${overview}OpenFreeMap 在线街道 · 高德/必应国内节点卫星 · 地图缓存上限 ${MapNetwork.CACHE_MIB} MiB"
            } else "${bundle.attribution} · ${if(key=="base") "内置全国概览" else "已导入精细图"} · 街道数据最高 Z${maxZoom.toInt()}${if(fineSatelliteLoaded) " · 含 ${urban.count} 个城区精细影像" else ""}")
            if(fitFirstView) {fitFirstView=false;view.post {ready.moveCamera(overviewCamera())}}
            refresh();refreshPickHint();refreshMapInfo();chooseMap()
        }
    }
    private fun refresh() {
        val ready=map ?: return; val currentStyle=style ?: return
        job?.cancel()
        val candidates = if (BuildConfig.ONLINE_MAPS && ready.cameraPosition.zoom >= 6 && items.isNotEmpty() && view.width > 0 && view.height > 0) {
            val bounds = ready.projection.visibleRegion.latLngBounds
            // Include a margin so photos just outside the screen still cluster with edge markers.
            val longitudePad = (bounds.longitudeEast - bounds.longitudeWest) / 2
            val latitudePad = (bounds.latitudeNorth - bounds.latitudeSouth) / 2
            val latitudes = (bounds.latitudeSouth - latitudePad)..(bounds.latitudeNorth + latitudePad)
            val west = bounds.longitudeWest - longitudePad
            val east = bounds.longitudeEast + longitudePad
            items.filter { it.latitude in latitudes &&
                (it.longitude in west..east || it.longitude + 360 in west..east || it.longitude - 360 in west..east) }
        } else items
        val snapshotItems = candidates.associateBy { it.id }
        val points = candidates.mapNotNull { place ->
            if (!inCoverage(place.latitude, place.longitude)) return@mapNotNull null
            val coordinate = LatLng(place.latitude, place.longitude)
            if (BuildConfig.ONLINE_MAPS && ready.cameraPosition.zoom < 6 &&
                occlusionMethod?.invoke(ready.projection, coordinate) == true) return@mapNotNull null
            val point = ready.projection.toScreenLocation(coordinate)
            PhotoPoint(place.id, point.x.toDouble(), point.y.toDouble(), place.photoCount)
        }
        val density=context.resources.displayMetrics.density
        val size = photoMarkerDp(ready.cameraPosition.zoom)
        val clusterRadius=(hypot(size, size * 144 / 128) + 8)*density
        val markerDp=kotlin.math.round(size).toFloat()
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
        val minimum=if(BuildConfig.ONLINE_MAPS) globeMinimum() else MapViewport.minimumZoom(width,height)
        ready.setMinZoomPreference(minimum)
    }
    private fun refreshPickHint() {
        if(!picking) return
        val ready=map ?: return;val target=center
        val allowed=inCoverage(target.latitude,target.longitude)
        if(!allowed) {suggestedName="";onPickContext("当前位置超出地图数据范围",false);return}
        searchSelection?.let { (selected, label) ->
            // A search already resolved this point. Querying native features
            // immediately after a large jump can block while tiles render.
            if (selected.distanceTo(target) <= 80) {
                showPickHint(target, label)
                return
            }
        }
        val anchor=ready.projection.toScreenLocation(target);val radius=24*context.resources.displayMetrics.density
        val names=listOf("urban-poi-labels","urban-road-labels","urban-roads","poi-labels","road-labels","roads","county-labels","place-labels","locality-labels")
        val layers=(if (hasOnlineOverview) names + names.map { "embedded-$it" } +
            names.map { "context-$it" } + names.map { "context-detail-$it" } else names).filter {style?.getLayer(it)!=null}.toTypedArray()
        val features=if(layers.isEmpty()) emptyList() else ready.queryRenderedFeatures(RectF(anchor.x-radius,anchor.y-radius,anchor.x+radius,anchor.y+radius),*layers)
        val name=features.firstNotNullOfOrNull {feature->listOf("name:zh-Hans","name:zh","name","name:en").firstNotNullOfOrNull {key->runCatching {feature.getStringProperty(key)}.getOrNull()?.takeIf {it.isNotBlank()}}}.orEmpty()
        showPickHint(target, name)
    }

    private fun showPickHint(target: LatLng, name: String) {
        val ready = map ?: return
        suggestedName = name
        val coordinates="%.6f, %.6f".format(target.latitude,target.longitude)
        val detail=if(BuildConfig.ONLINE_MAPS) {if(basemapMode==BasemapMode.SATELLITE) "\n高清卫星影像；精细选点也可切换街道图" else ""} else if(basemapMode==BasemapMode.SATELLITE) "\n"+if(fineSatelliteLoaded && urban.contains(target.latitude,target.longitude)) "2016 年城区影像；精细选点可切换街道图" else "当前为概览影像，精细选点请切换街道图" else if(selectedMap=="base" && ready.cameraPosition.zoom>10) "\n尚未导入精细包，当前为概览图" else ""
        onPickContext((if(suggestedName.isBlank()) coordinates else "附近：$suggestedName\n$coordinates")+detail,true)
    }
    private fun refreshMapInfo() {
        val ready=map ?: return
        if(BuildConfig.ONLINE_MAPS) {
            val zoom = ready.cameraPosition.zoom
            val info = when {
                !MapNetwork.connected(context) && hasOnlineOverview -> "无网络 · 显示内置地图和已缓存细节"
                !MapNetwork.connected(context) -> "无网络 · 显示已缓存的地图"
                basemapMode==BasemapMode.SATELLITE && hasOnlineOverview && zoom<OnlineMapAssets.SATELLITE_DETAIL_ZOOM ->
                    "内置卫星概览 · 放大自动加载高清卫星"
                basemapMode==BasemapMode.SATELLITE -> "高清卫星影像 · 国内直连"
                else -> ""
            }
            onMapInfo(info)
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
    private fun photoMarkerDp(zoom: Double): Double {
        if (!BuildConfig.ONLINE_MAPS) return MapViewport.markerDp(zoom)
        return when {
            zoom <= 8 -> 38.0
            zoom < 13 -> 38 + (zoom - 8) * .4
            else -> MapViewport.markerDp(zoom)
        }
    }
    fun destroy() { stopGlobeSpin(); job?.cancel(); view.onDestroy() }
}
