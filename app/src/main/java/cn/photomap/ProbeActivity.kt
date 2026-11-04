package cn.photomap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import cn.photomap.core.PhotoCluster
import cn.photomap.core.PhotoClustering
import cn.photomap.core.PhotoPoint
import kotlinx.coroutines.*
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.random.Random

class ProbeActivity : ComponentActivity() {
    private lateinit var view: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var job: Job? = null
    private var count by mutableIntStateOf(1000)
    private var overlap by mutableStateOf(false)
    private var picking by mutableStateOf(false)
    private var status by mutableStateOf("正在初始化离线渲染器")
    private var mapStatus by mutableStateOf("无底图 · 仅测试点，不是全国地图")
    private var selected by mutableStateOf<List<String>>(emptyList())
    private var covers = emptyList<Bitmap>()
    private var coverVersion = 0
    private val seed = Random.nextInt()
    private var icons = emptySet<String>()
    private var visible = emptyList<PhotoCluster>()
    private var locations = emptyMap<String, LatLng>()
    private val frames = mutableListOf<Long>()
    private val frameThread = HandlerThread("frame-probe")
    private val startedAt = android.os.SystemClock.elapsedRealtime()
    private var styleReadyMs: Long? = null
    private var clusterMs = 0.0
    private var coverIsPhoto = false
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            val report = report()
            status = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(report) }; "测试报告已导出" }
                    .getOrDefault("报告导出失败，请检查可用空间后重试")
            }
        }
    }
    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    val options = BitmapFactory.Options().apply {
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                        inSampleSize = sample
                    }
                    contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
                }.getOrNull() }
            }
            if (bitmaps.isEmpty()) status = "图片无法解码，请选择 JPEG、PNG 或 WebP" else {
                covers = bitmaps; coverVersion++; coverIsPhoto = true; refresh()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        view = MapView(this)
        view.onCreate(savedInstanceState)
        frameThread.start()
        window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            synchronized(frames) { if (frames.size < 100000) frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) }
        }, Handler(frameThread.looper))
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                    Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
                        Column(Modifier.padding(12.dp)) {
                            Text("留影 / 阶段 0 技术验证", style = MaterialTheme.typography.titleMedium)
                            Text(mapStatus, style = MaterialTheme.typography.bodySmall)
                            Text(status, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (picking) Text("＋", Modifier.align(Alignment.Center), style = MaterialTheme.typography.displaySmall)
                    Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
                        Column(Modifier.padding(10.dp)) {
                            Text("模拟地点与照片计数；最多选 20 张临时封面", style = MaterialTheme.typography.bodySmall)
                            Row {
                                listOf(100, 500, 1000).forEach { n -> TextButton(onClick = { count = n; refresh() }) { Text("$n 点") } }
                                TextButton(onClick = { overlap = !overlap; refresh() }) { Text(if (overlap) "分散" else "重叠") }
                            }
                            Row {
                                TextButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("选照片封面") }
                                TextButton(onClick = { national() }) { Text("全国视角") }
                                TextButton(onClick = {
                                    if (picking) map?.cameraPosition?.target?.let { status = "WGS84 %.6f, %.6f（未保存）".format(it.latitude, it.longitude) }
                                    picking = !picking
                                }) { Text(if (picking) "确认坐标" else "选点") }
                            }
                            TextButton(onClick = { export.launch("photomap-probe.json") }) { Text("导出当前测试报告") }
                        }
                    }
                    if (selected.isNotEmpty()) AlertDialog(onDismissRequest = { selected = emptyList() },
                        title = { Text("测试地点（${selected.size}）") },
                        text = { LazyColumn { items(selected) { Text(it, Modifier.padding(8.dp)) } } },
                        confirmButton = { TextButton(onClick = { selected = emptyList() }) { Text("关闭") } })
                }
            }
        }
        view.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isTiltGesturesEnabled = false
            ready.setMaxZoomPreference(17.0)
            val prefs = getPreferences(MODE_PRIVATE)
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(prefs.getFloat("lat", 35f).toDouble(), prefs.getFloat("lon", 105f).toDouble())).zoom(prefs.getFloat("zoom", 3.5f).toDouble()).build()
            ready.addOnCameraIdleListener {
                ready.cameraPosition.let { camera -> camera.target?.let { p -> prefs.edit().putFloat("lat", p.latitude.toFloat()).putFloat("lon", p.longitude.toFloat()).putFloat("zoom", camera.zoom.toFloat()).apply() } }
                refresh()
            }
            ready.addOnMapClickListener { coordinate ->
                val p = ready.projection.toScreenLocation(coordinate)
                val hit = visible.minByOrNull { (it.x-p.x)*(it.x-p.x)+(it.y-p.y)*(it.y-p.y) }
                if (!picking && hit != null && abs(hit.x-p.x)<40*resources.displayMetrics.density && abs(hit.y-p.y)<40*resources.displayMetrics.density) {
                    val positions = hit.members.mapNotNull { locations[it.id] }
                    if (positions.distinct().size < 2 || ready.cameraPosition.zoom >= 16) selected = hit.members.map { "${it.id} · ${it.photoCount} 张模拟照片" }
                    else ready.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(positions).build(), 90))
                    true
                } else false
            }
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { LocalMapBundle.load(this@ProbeActivity) } }
                val bundle = result.getOrNull()
                mapStatus = if (result.isFailure) "地图包校验失败，请重新打包；当前无底图" else bundle?.attribution ?: "无底图 · 仅测试点，不是全国地图"
                ready.setStyle(Style.Builder().fromJson(bundle?.style ?: """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#e9ece7"}}]}""")) { loaded ->
                    style = loaded
                    styleReadyMs = android.os.SystemClock.elapsedRealtime() - startedAt
                    loaded.addSource(GeoJsonSource("photos", FeatureCollection.fromFeatures(emptyArray<Feature>())))
                    loaded.addLayer(SymbolLayer("photo-covers", "photos").withProperties(iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(0.5f)))
                    refresh()
                }
            }
        }
    }
    private fun national() { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 105.0), 3.5)) }
    private fun refresh() {
        val currentMap = map ?: return
        val currentStyle = style ?: return
        job?.cancel()
        val random = Random(20261102)
        locations = (0 until count).associate { i -> "test-%04d".format(i) to if (overlap) LatLng(30.0, 110.0) else LatLng(20 + random.nextDouble()*25, 85 + random.nextDouble()*40) }
        val snapshot = locations.map { (id, latLng) -> currentMap.projection.toScreenLocation(latLng).let { PhotoPoint(id, it.x.toDouble(), it.y.toDouble(), 10) } }
        val density = resources.displayMetrics.density
        val photos = covers
        job = lifecycleScope.launch {
            val start = System.nanoTime()
            val clusters = withContext(Dispatchers.Default) { PhotoClustering.cluster(snapshot, 88.0 * density, seed) }
            clusterMs = (System.nanoTime()-start)/1e6
            val viewport = clusters.filter { it.x >= -80*density && it.x <= view.width+80*density && it.y >= -80*density && it.y <= view.height+80*density }
            val nextIds = viewport.map { iconId(it) }.toSet()
            val images = withContext(Dispatchers.Default) {
                viewport.filter { iconId(it) !in icons }.associate { cluster ->
                    val photo = if (photos.isEmpty()) null else photos[Math.floorMod(cluster.coverId.hashCode(), photos.size)]
                    iconId(cluster) to makeIcon(cluster.photoCount, photo)
                }
            }
            ensureActive()
            images.forEach { (id, bitmap) -> currentStyle.addImage(id, bitmap) }
            val features = viewport.map { cluster ->
                val position = locations.getValue(cluster.members.first().id)
                Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude)).apply { addStringProperty("icon", iconId(cluster)) }
            }
            currentStyle.getSourceAs<GeoJsonSource>("photos")?.setGeoJson(FeatureCollection.fromFeatures(features))
            (icons - nextIds).forEach { currentStyle.removeImage(it) }
            icons = nextIds
            visible = viewport
            status = "$count 地点 / ${count * 10} 模拟照片 · ${viewport.size} 可见聚合 · %.1f ms".format(clusterMs)
        }
    }
    private fun iconId(cluster: PhotoCluster): String = "photo-$coverVersion-" + MessageDigest.getInstance("SHA-256").digest(cluster.key.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    private fun makeIcon(count: Long, photo: Bitmap?): Bitmap {
        val result = Bitmap.createBitmap(128, 144, Bitmap.Config.ARGB_8888)
        val c = Canvas(result)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        c.drawRoundRect(RectF(2f, 2f, 126f, 130f), 16f, 16f, p)
        c.save()
        val path = android.graphics.Path().apply { addRoundRect(RectF(8f, 8f, 120f, 124f), 12f, 12f, android.graphics.Path.Direction.CW) }
        c.clipPath(path)
        if (photo != null) {
            val scale = maxOf(112f/photo.width, 116f/photo.height)
            val w = photo.width*scale; val h = photo.height*scale
            c.drawBitmap(photo, null, RectF(64-w/2, 66-h/2, 64+w/2, 66+h/2), p)
        } else {
            p.color = android.graphics.Color.rgb(73, 107, 100); c.drawRect(8f, 8f, 120f, 124f, p)
            p.color = android.graphics.Color.WHITE; p.textSize = 22f; c.drawText("TEST", 32f, 67f, p)
        }
        c.restore()
        p.color = android.graphics.Color.rgb(30, 44, 40); c.drawRoundRect(RectF(49f, 100f, 126f, 136f), 12f, 12f, p)
        p.color = android.graphics.Color.WHITE; p.textSize = 24f; p.textAlign = Paint.Align.CENTER
        c.drawText(count.toString(), 87f, 126f, p)
        return result
    }
    private fun report(): String {
        val values = synchronized(frames) { frames.sorted() }
        return JSONObject().apply {
            put("kind", "phase0-diagnostic-not-acceptance")
            put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("androidApi", android.os.Build.VERSION.SDK_INT)
            put("mapStatus", mapStatus); put("syntheticLocations", count); put("syntheticPhotoCount", count*10)
            put("realCoverSelected", coverIsPhoto); put("allLocationsOverlap", overlap)
            put("mapStyleReadyMs", styleReadyMs); put("lastClusterMs", clusterMs)
            put("frameSamples", values.size)
            if (values.isNotEmpty()) { put("frameP95Ms", values[((values.size-1)*0.95).toInt()]/1e6); put("framesOver32ms", values.count { it > 32_000_000 }) }
            put("javaHeapBytes", Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            put("note", "帧数据含静止与界面操作，Java 堆不含全部 GPU/原生内存。需另用 Perfetto/Profiler；模拟计数不等同真实万张照片测试。")
        }.toString(2)
    }
    override fun onStart() { super.onStart(); view.onStart() }
    override fun onResume() { super.onResume(); view.onResume() }
    override fun onPause() { view.onPause(); super.onPause() }
    override fun onStop() { view.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); view.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); view.onSaveInstanceState(outState) }
    override fun onDestroy() { job?.cancel(); view.onDestroy(); frameThread.quitSafely(); super.onDestroy() }
}
