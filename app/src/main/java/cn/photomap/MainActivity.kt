package cn.photomap

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import cn.photomap.data.*
import cn.photomap.map.*
import cn.photomap.ui.*
import kotlinx.coroutines.*
import java.time.LocalDate

@OptIn(coil.annotation.ExperimentalCoilApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var library: Library
    private lateinit var engine: PhotoMapEngine
    private lateinit var packages: MapPackages
    private var places by mutableStateOf<List<Place>>(emptyList())
    private var selected by mutableStateOf<Place?>(null)
    private var records by mutableStateOf<List<Record>>(emptyList())
    private var overlapping by mutableStateOf<List<Place>>(emptyList())
    private var draft by mutableStateOf<Draft?>(null)
    private var picking by mutableStateOf(false)
    private var pickHint by mutableStateOf("")
    private var pickAllowed by mutableStateOf(false)
    private var adjusting by mutableStateOf(false)
    private var settings by mutableStateOf(false)
    private var licenseText by mutableStateOf("")
    private var maps by mutableStateOf<List<RegionalMap>>(emptyList())
    private var themeChoice by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var progress by mutableStateOf("")
    private var lastMessage by mutableStateOf("")
    private var mapStatus by mutableStateOf(if (BuildConfig.ONLINE_MAPS) "正在打开地图" else "正在打开离线地图")
    private var mapInfo by mutableStateOf("")
    private var mapOptions by mutableStateOf(false)
    private var basemapMode by mutableStateOf(BasemapMode.STREETS)
    private var satelliteLabels by mutableStateOf(false)
    private val diagnostics get()=(application as PhotoMapApplication).startupDiagnostics
    private var startupError by mutableStateOf(false)
    private var diagnosticMessage by mutableStateOf("")
    private val diagnosticExport=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri->
        if(uri!=null) lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {contentResolver.openOutputStream(uri)!!.bufferedWriter().use {it.write(diagnostics.report())}}}
            diagnosticMessage=if(result.isSuccess) "故障报告已导出" else "报告导出失败，请检查目标文件夹与可用空间。"
            if(!startupError) message(diagnosticMessage)
        }
    }
    private var viewer by mutableStateOf<List<ViewedPhoto>>(emptyList())
    private var viewerPage by mutableIntStateOf(0)
    private var memoryViewer by mutableStateOf(false)
    private var memoryDay: LocalDate? = null
    private var memoryDays by mutableStateOf<List<PhotoDay>?>(null)
    private var memoryToday = LocalDate.now()
    private var pending by mutableStateOf<List<PendingPhoto>>(emptyList())
    private var pendingIndex by mutableIntStateOf(0)
    private var reviewingImport by mutableStateOf(false)
    private var pickingImportId: String? = null
    private var searchedPlace: Pair<PhotoPoint, String>? = null
    private var editingPhoto by mutableStateOf<Photo?>(null)
    private var confirmation by mutableStateOf<Pair<String,()->Unit>?>(null)
    private val snack=SnackbarHostState()
    private val directPhotos = if (BuildConfig.ONLINE_MAPS) {
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::importDirectPhotos)
    } else null
    private val photos=registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        val editing=draft
        if(uris.isNotEmpty() && editing!=null) work("正在导入照片") {
            val imported=library.import(uris) { done,total->runOnUiThread {progress="导入 $done / $total";editing.message=progress} }
            editing.added.addAll(imported.photos)
            if(editing.existing.isEmpty() && editing.added.size==imported.photos.size) imported.photos.firstOrNull()?.shotAt?.takeIf {it.length>=10}?.take(10)?.replace(':','-')?.takeIf {runCatching {LocalDate.parse(it)}.isSuccess}?.let {editing.date=it}
            editing.message="已导入 ${imported.photos.size} 张" + (if(imported.failed>0) "，${imported.failed} 张无法读取，请换用 JPEG、PNG、WebP 或设备可解码格式" else "") + (if(imported.duplicates>0) "，其中 ${imported.duplicates} 张为重复照片（已保留）" else "")
        }
    }
    private val backup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri->if(uri!=null) work("正在完整备份") {library.backup(uri);message("备份完成，包含数据库与全部原图；离线地图可重建，不重复备份") } }
    private val restore=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null) confirmation="恢复会替换当前作品，请先导出备份。只有全部校验通过后才会切换数据。" to {work("正在校验并恢复") { library.restore(uri);pending=emptyList();pendingIndex=0;reviewingImport=false;coil.Coil.imageLoader(this).memoryCache?.clear();withContext(Dispatchers.IO) {coil.Coil.imageLoader(this@MainActivity).diskCache?.clear()};selected=null;records=emptyList();viewer=emptyList();reload();message("恢复完成") }} }
    private val importMap=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null) work("正在导入离线地图包") {
        packages.import(uri) {done,total,verifying->runOnUiThread {progress=if(verifying) "正在校验地图文件" else if(total>0) "正在导入地图 ${(done*100/total).coerceIn(0,100)}%" else "正在导入地图 ${done/1048576} MB"}}
        maps=withContext(Dispatchers.IO) {packages.list()};engine.reloadPackages();message("地区地图导入完成，视野完整进入覆盖范围后自动切换")
    } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library=(application as PhotoMapApplication).library; packages=(application as PhotoMapApplication).mapPackages
        themeChoice=getSharedPreferences("settings",MODE_PRIVATE).getInt("theme",0)
        if(diagnostics.previousMapStartupCrashed()) startupError=true else startMap(savedInstanceState)
        setContent {if(startupError) StartupFailure() else Content()}
        work("正在读取作品") { library.cleanupAbandonedFiles();reload();maps=withContext(Dispatchers.IO) {packages.list()} }
    }
    private fun startMap(saved: Bundle?) {
        diagnostics.beginMapStartup()
        try {
            engine=PhotoMapEngine(this,lifecycleScope,library,packages,{members->if(members.size==1) select(members.first()) else overlapping=members},{mapStatus=it},{hint,allowed->pickHint=hint;pickAllowed=allowed},{mapInfo=it})
            basemapMode=engine.basemapMode
            satelliteLabels=engine.satelliteLabels
            engine.create(saved)
            startupError=false
        } catch(error: Exception) {diagnostics.record(error);startupError=true;if(::engine.isInitialized) runCatching {engine.destroy()}}
          catch(error: LinkageError) {diagnostics.record(error);startupError=true;if(::engine.isInitialized) runCatching {engine.destroy()}}
    }
    @Composable private fun StartupFailure() {
        MaterialTheme {Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center) {
            Text("地图未能启动",style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text("检测到地图初始化异常，已暂停自动重试。作品仍保存在本机。可导出设备与异常报告以定位兼容问题。")
            if(diagnosticMessage.isNotBlank()) Text(diagnosticMessage)
            Spacer(Modifier.height(24.dp))
            Button(onClick={launchSystemPicker {diagnosticExport.launch("PhotoMap-startup.txt")}}) {Text("导出故障报告")}
            OutlinedButton(onClick={startMap(null);if(!startupError) {engine.view.onStart();engine.view.onResume()}}) {Text("重新尝试地图")}
        }}}
    }
    private fun message(text: String) {lastMessage=text;lifecycleScope.launch {snack.showSnackbar(text)} }
    private fun launchSystemPicker(action: ()->Unit) {
        try {action()} catch(_: android.content.ActivityNotFoundException) {
            val text="系统照片 / 文件选择器不可用，请启用系统文件管理组件后重试。"
            draft?.message=text;message(text)
        }
    }
    private fun work(text: String, block: suspend ()->Unit) {
        if(busy) {message("请等待当前操作完成");return}
        busy=true;progress=text
        lifecycleScope.launch {
            try {block()} catch(error: CancellationException) {throw error}
            catch(_: Exception) {val text="操作未完成。请检查文件是否有效、可读取，以及设备是否有足够空间后重试。";draft?.message=text;message(text)}
            finally {busy=false;progress=""}
        }
    }
    private suspend fun reload() {
        places=library.places()
        selected=selected?.let {old->places.firstOrNull {it.id==old.id}}
        records=selected?.let {library.records(it.id)} ?: emptyList()
        if(::engine.isInitialized && !startupError) engine.update(places,selected?.id)
        if (memoryDays != null) memoryDays = library.photoDays().filter { it.date <= memoryToday }
        if(viewer.isNotEmpty()) {
            val current=viewer.getOrNull(viewerPage)?.photo?.id
            viewer = if (memoryViewer) {
                val days = memoryDays.orEmpty()
                val day = days.firstOrNull { entry -> entry.photos.any { it.photo.id == current } }
                    ?: days.firstOrNull { it.date == memoryDay }
                memoryDay = day?.date
                day?.photos?.map { ViewedPhoto(it.photo, it.record) }.orEmpty()
            } else records.flatMap {r->r.photos.map {ViewedPhoto(it,r)}}
            val currentIndex=viewer.indexOfFirst {it.photo.id==current}
            viewerPage=if(currentIndex>=0) currentIndex else viewerPage.coerceIn(0,maxOf(0,viewer.lastIndex))
        }
    }
    private fun select(place: Place) {
        selected=place;records=emptyList();overlapping=emptyList();engine.update(places,place.id)
        lifecycleScope.launch { runCatching {library.records(place.id)}.onSuccess {if(selected?.id==place.id) records=it}.onFailure {message("无法读取该地点，请重试")} }
    }
    private fun closePlace() {selected=null;records=emptyList();engine.update(places,null)}
    private fun startPicking() {
        closePlace()
        picking = true
        engine.picking = true
    }
    private fun finishPicking() {
        searchedPlace = null
        if (pickingImportId != null) reviewingImport = true
        pickingImportId = null
        picking = false
        adjusting = false
        engine.picking = false
    }
    private fun beginDirectImport() {
        if (pending.isNotEmpty()) reviewingImport = true
        else launchSystemPicker { directPhotos?.launch(arrayOf("image/*")) }
    }
    private fun importDirectPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        work("正在读取原图和拍摄信息") {
            val imported = library.import(uris.take(100)) { done, total ->
                runOnUiThread { progress = "正在读取原图 $done / $total" }
            }
            pending = imported.photos.map { PendingPhoto(it, it.exifPoint()) }
            pendingIndex = 0
            reviewingImport = pending.isNotEmpty()
            val saved = saveReadyImports()
            message("已保存 $saved 张" +
                (if (pending.isNotEmpty()) " · ${pending.size} 张待补填" else "") +
                (if (imported.failed > 0) " · ${imported.failed} 张无法读取" else "") +
                (if (imported.duplicates > 0) " · 保留 ${imported.duplicates} 张重复照片" else "") +
                (if (uris.size > 100) " · 单批最多读取 100 张" else ""))
        }
    }
    private suspend fun saveReadyImports(): Int {
        val placements = pending.mapNotNull { it.placement() }
        if (placements.isEmpty()) return 0
        library.saveLocatedPhotos(placements)
        val saved = placements.map { it.photo.id }.toSet()
        pending = pending.filterNot { it.photo.id in saved }
        pendingIndex = pendingIndex.coerceAtMost(maxOf(0, pending.lastIndex))
        reviewingImport = pending.isNotEmpty()
        reload()
        return placements.size
    }
    private fun changePending(item: PendingPhoto) {
        pending = pending.map { if (it.photo.id == item.photo.id) item else it }
    }
    private fun openMemories() = work("正在整理拍摄日期") {
        memoryToday = LocalDate.now()
        memoryDays = library.photoDays().filter { it.date <= memoryToday }
    }
    private fun cancelDraft() {
        val old=draft ?: return
        confirmation="放弃尚未保存的修改及新导入照片？已有作品不会改变。" to {
            work("正在清理未保存照片") {library.discard(old.added.toList());draft=null;picking=false;adjusting=false;engine.picking=false}
        }
    }
    private fun saveDraft() {
        val editing=draft ?: return
        work("正在保存记录") {
            val id=library.save(editing.placeId,editing.name,editing.latitude,editing.longitude,editing.recordId,editing.title,editing.note,editing.date,editing.added.toList())
            draft=null;closePlace();reload();places.firstOrNull {it.id==id}?.let {select(it)};message("记录已保存在本机")
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Content() {
        val dark=themeChoice==2 || (themeChoice==0 && isSystemInDarkTheme())
        LaunchedEffect(dark) {
            val bars=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
            enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
            engine.theme(dark)
        }
        val colors = when {
            BuildConfig.ONLINE_MAPS && dark -> darkColorScheme(
                primary = Color(0xffb4d8c7), primaryContainer = Color(0xff244b40),
                secondaryContainer = Color(0xff354640), surface = Color(0xff171f1d), surfaceVariant = Color(0xff26332f)
            )
            BuildConfig.ONLINE_MAPS -> lightColorScheme(
                primary = Color(0xff285749), primaryContainer = Color(0xffc9e8da),
                secondaryContainer = Color(0xffe0ebe5), surface = Color(0xfffbfaf5), surfaceVariant = Color(0xffe9eee7)
            )
            dark -> darkColorScheme(primary = Color(0xffa8c9b7))
            else -> lightColorScheme(primary = Color(0xff315b49))
        }
        val panelOpen = selected != null || settings || mapOptions || picking || reviewingImport || draft != null
        var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(panelOpen) {
            if (panelOpen && android.os.Build.VERSION.SDK_INT >= 31) {
                engine.snapshot { backdrop = it.asImageBitmap() }
            } else backdrop = null
        }
        MaterialTheme(colorScheme=colors) {
            CompositionLocalProvider(LocalMapBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
                AndroidView(factory={engine.view},modifier=Modifier.fillMaxSize())
                if (!BuildConfig.ONLINE_MAPS) {
                FrostedPanel(Modifier.align(Alignment.TopStart).padding(16.dp),shape=RoundedCornerShape(26.dp),elevation=8.dp) {
                    Column(Modifier.padding(horizontal=18.dp,vertical=10.dp)) {Text(if(BuildConfig.ONLINE_MAPS) "留影·联网" else "留影·离线",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("${places.size} 个地点 · ${places.sumOf {it.photoCount}} 张照片",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                Column(Modifier.align(Alignment.TopEnd).padding(16.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(!picking) FrostedPanel(shape=RoundedCornerShape(22.dp),elevation=6.dp) { TextButton(onClick={settings=true},enabled=!busy) {Text("数据",fontWeight=FontWeight.Medium)} }
                    FrostedPanel(shape=RoundedCornerShape(22.dp),elevation=6.dp) { TextButton(onClick={mapOptions=true},enabled=!busy) {Text("底图",fontWeight=FontWeight.Medium)} }
                }
                if(!picking && (mapInfo.isNotBlank() || places.isEmpty())) FrostedPanel(Modifier.align(Alignment.BottomStart).padding(start=12.dp,end=110.dp,bottom=72.dp).clickable {mapOptions=true},shape=RoundedCornerShape(20.dp),elevation=4.dp) {
                    Text(mapInfo.ifBlank {"点 ＋，记录第一处光影。"},Modifier.padding(horizontal=14.dp,vertical=10.dp),style=MaterialTheme.typography.bodySmall)
                }
                }
                if(picking) {
                    if (BuildConfig.ONLINE_MAPS) {
                        val photo = pending.firstOrNull { it.photo.id == pickingImportId }?.photo ?: draft?.added?.firstOrNull()
                        PickLocationPanel(photo?.let { library.file(it.thumbnail) },
                            Modifier.align(Alignment.TopCenter).padding(12.dp).widthIn(max = 480.dp).fillMaxWidth(),
                            original = photo?.let { library.file(it.original) }) { latitude, longitude, label ->
                            searchedPlace = PhotoPoint(latitude, longitude) to label
                            engine.focusSearch(latitude, longitude)
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("＋",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                    }
                    FrostedPanel(Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max=480.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),elevation=10.dp) {
                        Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("移动地图，将拍摄位置对准准星",textAlign=TextAlign.Center,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                            Text(pickHint.ifBlank {"正在读取位置"},textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                OutlinedButton(onClick=::finishPicking,shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f).height(48.dp)) {Text("取消")}
                                Button(onClick={
                                if(!engine.canPick) {message(if (BuildConfig.ONLINE_MAPS) "请选择有效的地图位置" else "请选择中国范围内的位置");return@Button}
                                val p=engine.center
                                val searchedName = searchedPlace?.let { (point, label) ->
                                    val distance = FloatArray(1)
                                    android.location.Location.distanceBetween(point.latitude, point.longitude, p.latitude, p.longitude, distance)
                                    label.takeIf { distance[0] <= 80f }
                                }.orEmpty()
                                val placeName = searchedName.ifBlank { engine.suggestedName }
                                val importId = pickingImportId
                                if (importId != null) {
                                    pending.firstOrNull { it.photo.id == importId }?.let {
                                        changePending(it.copy(point = PhotoPoint(p.latitude, p.longitude), placeName = placeName))
                                    }
                                } else if(adjusting) {draft?.latitude=p.latitude;draft?.longitude=p.longitude}
                                else draft=Draft(null,null,placeName,p.latitude,p.longitude)
                                finishPicking()
                            },enabled=pickAllowed && !busy,shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1.6f).height(48.dp)) {Text("确定位置",fontWeight=FontWeight.SemiBold)} }
                        }
                    }
                } else if (BuildConfig.ONLINE_MAPS) {
                    OnlineMapChrome(
                        places.size, places.sumOf { it.photoCount }, pending.size, mapInfo,
                        basemapMode == BasemapMode.SATELLITE,
                        !busy, { mapOptions = true }, { settings = true }, ::openMapSources, { engine.national() },
                        ::startPicking, ::beginDirectImport, ::openMemories
                    )
                } else {
                    Column(Modifier.align(Alignment.BottomEnd).padding(20.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        FrostedPanel(shape=RoundedCornerShape(22.dp),elevation=6.dp) { TextButton(onClick={engine.national()},enabled=!busy) {Text("全国",fontWeight=FontWeight.Medium)} }
                        FloatingActionButton(onClick={if(!busy) {closePlace();picking=true;engine.picking=true}},shape=RoundedCornerShape(22.dp)) {Text("＋",style=MaterialTheme.typography.headlineMedium)}
                    }
                    val mapCredits = if (BuildConfig.ONLINE_MAPS) {
                        "© OSM contributors · © OpenMapTiles · OpenFreeMap"
                    } else {
                        "© OSM contributors · Protomaps"
                    }
                    val satelliteCredit = if (basemapMode == BasemapMode.SATELLITE) "EOX · Copernicus Sentinel 2016\n" else ""
                    Text(satelliteCredit + mapCredits, Modifier.align(Alignment.BottomStart).fillMaxWidth(0.68f).padding(12.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha=0.82f)).padding(horizontal=8.dp,vertical=4.dp), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurface)
                }
                SnackbarHost(snack,Modifier.align(Alignment.TopCenter).padding(top=90.dp)) { data ->
                    FrostedPanel(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(22.dp), elevation = 8.dp) {
                        Text(data.visuals.message, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if(busy) FrostedPanel(Modifier.align(Alignment.Center).padding(24.dp),shape=RoundedCornerShape(28.dp),elevation=12.dp) {
                    Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {CircularProgressIndicator();Spacer(Modifier.height(14.dp));Text(progress, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)}
                }
                BackHandler(enabled=picking, onBack=::finishPicking)
                if(selected!=null && draft==null && !picking && !settings) {
                    val place=selected!!
                    MapPanelSheet(onDismissRequest={if(!busy) closePlace()},skipPartiallyExpanded=false) {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f,false),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            item {
                                if (BuildConfig.ONLINE_MAPS) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val title = if (place.name.matches(Regex("""拍摄地点 -?\d+\.\d+, -?\d+\.\d+"""))) "拍摄地点" else place.name
                                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                        Text("${place.photoCount} 张照片 · ${records.size} 条记录", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text("%.6f, %.6f".format(place.latitude, place.longitude), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Button(onClick = { draft = Draft(place.id, null, place.name, place.latitude, place.longitude) }, enabled = !busy, shape = RoundedCornerShape(18.dp)) { Text("添加记录", fontWeight = FontWeight.SemiBold) }
                                            if (records.size > 1) DetailMenu("地点选项", !busy) { close ->
                                                DropdownMenuItem(text = { Text("删除整个地点", color = MaterialTheme.colorScheme.error) }, onClick = {
                                                    close()
                                                    confirmation = "删除“${place.name}”及全部应用内照片？系统相册原件不受影响。" to {
                                                        work("正在删除地点") { library.removePlace(place.id); closePlace(); reload() }
                                                    }
                                                })
                                            }
                                        }
                                    }
                                } else {
                                Text(place.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("${place.photoCount} 张照片 · %.6f, %.6f".format(place.latitude,place.longitude),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {TextButton(onClick={draft=Draft(place.id,null,place.name,place.latitude,place.longitude)},enabled=!busy) {Text("添加记录")};TextButton(onClick={confirmation="删除“${place.name}”及全部应用内照片？系统相册原件不受影响。" to {work("正在删除地点") {library.removePlace(place.id);closePlace();reload()}}},enabled=!busy) {Text("删除地点",color=MaterialTheme.colorScheme.error)}}
                                }
                            }
                            items(records,key={it.id}) {record->
                                val edit = { draft = Draft(place.id, record.id, place.name, place.latitude, place.longitude, record.title, record.note, record.date, record.photos) }
                                val remove = {
                                    val prompt = if (BuildConfig.ONLINE_MAPS && records.size == 1)
                                        "删除最后一条记录及其应用内照片？空地点也会一并移除。"
                                    else "删除这条摄影记录及其应用内照片？"
                                    confirmation = prompt to { work("正在删除记录") { library.removeRecord(record.id); reload() } }
                                }
                                LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (BuildConfig.ONLINE_MAPS) Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(record.title.ifBlank { "摄影记录" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                Text(record.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            DetailMenu("记录选项", !busy) { close ->
                                                DropdownMenuItem(text = { Text("编辑记录 / 地点") }, onClick = { close(); edit() })
                                                DropdownMenuItem(text = { Text("删除记录", color = MaterialTheme.colorScheme.error) }, onClick = { close(); remove() })
                                            }
                                        } else {
                                            Text(record.title.ifBlank {"摄影记录"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(record.date,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if(record.note.isNotBlank()) Text(record.note,modifier=Modifier.padding(vertical=4.dp), style = MaterialTheme.typography.bodyMedium)
                                        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {items(record.photos,key={it.id}) {photo->
                                            val thumbShape = RoundedCornerShape(16.dp)
                                            AsyncImage(library.file(photo.thumbnail),contentDescription=photo.title.ifBlank {photo.filename},contentScale=ContentScale.Crop,modifier=Modifier.size(132.dp).clip(thumbShape).border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), thumbShape).clickable {
                                                memoryViewer=false;viewer=records.flatMap {r->r.photos.map {ViewedPhoto(it,r)}};viewerPage=viewer.indexOfFirst {it.photo.id==photo.id}
                                            })
                                        }}
                                        if (!BuildConfig.ONLINE_MAPS) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {TextButton(onClick=edit,enabled=!busy) {Text("编辑记录 / 地点")};TextButton(onClick=remove,enabled=!busy) {Text("删除记录",color=MaterialTheme.colorScheme.error)}}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(overlapping.isNotEmpty()) AlertDialog(onDismissRequest={overlapping=emptyList()},shape=RoundedCornerShape(28.dp),title={Text("选择拍摄地点",fontWeight=FontWeight.SemiBold)},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {items(overlapping,key={it.id}) {p->LiquidGlassCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),onClick={select(p)}) {Text("${p.name} · ${p.photoCount} 张",Modifier.padding(horizontal=14.dp,vertical=10.dp),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)}}}},confirmButton={TextButton(onClick={overlapping=emptyList()}) {Text("关闭")}})
                if(draft!=null && !picking) RecordEditor(draft!!,library,busy,::cancelDraft,{launchSystemPicker {photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}},{adjusting=true;picking=true;engine.picking=true;draft?.let {engine.focus(it.latitude,it.longitude)}},{photo->
                    val current=draft!!; work("正在移除未保存照片") {library.discard(listOf(photo));current.added.remove(photo)}
                },::saveDraft)
                memoryDays?.let { days ->
                    Memories(days, memoryToday.minusYears(1), dark, library, { memoryDays = null }) { day, index ->
                        memoryViewer = true
                        memoryDay = day.date
                        viewer = day.photos.map { ViewedPhoto(it.photo, it.record) }
                        viewerPage = index
                    }
                }
                if (reviewingImport && !picking && pending.isNotEmpty()) {
                    val current = pending[pendingIndex]
                    ImportReview(pending, pendingIndex, busy, dark, library, { pendingIndex = it }, ::changePending,
                        onPick = {
                            pickingImportId = current.photo.id
                            reviewingImport = false
                            startPicking()
                            current.point?.let { engine.focus(it.latitude, it.longitude) }
                        },
                        onApplyDate = { pending = pending.map { if (it.hasDate) it else it.copy(date = current.date) } },
                        onApplyPoint = { pending = pending.map { if (it.point != null) it else it.copy(point = current.point, placeName = current.placeName) } },
                        onSave = { work("正在保存照片") { message("已保存 ${saveReadyImports()} 张照片") } },
                        onDismiss = { reviewingImport = false },
                        onDiscard = {
                            confirmation = "放弃这批尚未保存的照片？已保存的作品和系统原图会保留。" to {
                                work("正在清理待补填照片") {
                                    library.discard(pending.map { it.photo })
                                    pending = emptyList()
                                    pendingIndex = 0
                                    reviewingImport = false
                                }
                            }
                        }
                    )
                }
                if(viewer.isNotEmpty()) PhotoViewer(viewer,viewerPage,library,{viewerPage=it},{viewer=emptyList();memoryViewer=false},{editingPhoto=it},{photo->confirmation="删除这张应用内照片？系统相册中的原件不受影响。最后一张照片删除后，空记录和空地点也会移除。" to {work("正在删除照片") {library.removePhoto(photo.id);reload()}}})
                if(editingPhoto!=null) {
                    val photo=editingPhoto!!
                    var title by remember(photo.id) {mutableStateOf(photo.title)};var note by remember(photo.id) {mutableStateOf(photo.note)};var date by remember(photo.id) {mutableStateOf(photo.date)}
                    var parameters by remember(photo.id) {mutableStateOf(runCatching {org.json.JSONObject(photo.exif).optString("manual_parameters")}.getOrDefault(""))}
                    var camera by remember(photo.id) { mutableStateOf(if (BuildConfig.ONLINE_MAPS) photo.cameraOverrides() else emptyMap()) }
                    var showCamera by remember(photo.id) { mutableStateOf(false) }
                    val fieldShape = RoundedCornerShape(16.dp)
                    AlertDialog(onDismissRequest={if(!busy) editingPhoto=null},shape=RoundedCornerShape(28.dp),title={Text("照片文字与参数",fontWeight=FontWeight.SemiBold)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title,{title=it.take(500)},shape=fieldShape,label={Text("标题（空白使用记录标题）")});OutlinedTextField(date,{date=it},shape=fieldShape,label={Text("日期 YYYY-MM-DD（可空）")});OutlinedTextField(note,{note=it.take(100000)},shape=fieldShape,label={Text("心得（空白使用记录心得）")},maxLines=5)
                        OutlinedTextField(parameters,{parameters=it.take(2500)},shape=fieldShape,label={Text("摄影参数补充 / 更正（可空）")},maxLines=3);Text("只修改应用内说明，不改动原图 EXIF。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if (BuildConfig.ONLINE_MAPS) {
                            TextButton(onClick = { showCamera = !showCamera }, enabled = !busy) {
                                Text(if (showCamera) "收起相机参数" else "编辑各项相机参数")
                            }
                            if (showCamera) CameraFields(photo, camera, !busy) { camera = it }
                        }
                    }},confirmButton={TextButton(onClick={work("正在保存照片文字") {library.editPhoto(photo,title,note,date,parameters,camera.takeIf { BuildConfig.ONLINE_MAPS });editingPhoto=null;reload()}},enabled=!busy && (date.isBlank() || runCatching {LocalDate.parse(date)}.isSuccess)) {Text("保存",fontWeight=FontWeight.SemiBold)}},dismissButton={TextButton(onClick={editingPhoto=null},enabled=!busy) {Text("取消")}})
                }
                if(settings) SettingsSheet(dark)
                if(mapOptions) MapPanelSheet(onDismissRequest={mapOptions=false}) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("选择底图",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                        BasemapChoices()
                        Text(if(BuildConfig.ONLINE_MAPS) {
                            val overview = if (engine.hasOnlineOverview) "全球概览保存在本机。放大后按需加载并缓存细节。" else "按视野加载并缓存地图。"
                            overview + "照片、EXIF 和作品保存在本机。卫星影像为 2016 年数据。"
                        } else "切换不联网。请先在数据中一次性导入全国精细包：全国街道最高 Z15，1995 个建成区配有约 10 米卫星影像；城区索引之外的影像为概览。影像拍摄于 2016 年，不是实时画面。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(mapInfo.isNotBlank()) Text(mapInfo,style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={mapOptions=false;settings=true}) {Text(if(BuildConfig.ONLINE_MAPS) "查看地图来源与缓存" else "查看地图数据与导入增强包")}
                        Button(onClick={mapOptions=false},shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {Text("完成",fontWeight=FontWeight.SemiBold)}
                    }
                }
                if(licenseText.isNotBlank()) AlertDialog(onDismissRequest={licenseText=""},shape=RoundedCornerShape(28.dp),title={Text("地图来源与许可",fontWeight=FontWeight.SemiBold)},text={SelectionContainer {Text(licenseText,Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={licenseText=""}) {Text("关闭")}})
                confirmation?.let { (text,action)->AlertDialog(onDismissRequest={confirmation=null},shape=RoundedCornerShape(28.dp),title={Text("请确认",fontWeight=FontWeight.SemiBold)},text={Text(text)},confirmButton={TextButton(onClick={confirmation=null;action()},enabled=!busy) {Text("确定",fontWeight=FontWeight.SemiBold)}},dismissButton={TextButton(onClick={confirmation=null}) {Text("取消")}}) }
            }
        }
    }
    }
    fun chooseBasemap(mode:BasemapMode) {basemapMode=mode;engine.basemap(mode)}
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun BasemapChoices() {
        BasemapMode.entries.forEach {mode->
            val isSelected = basemapMode == mode
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                selected = isSelected,
                onClick = { chooseBasemap(mode) }
            ) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=isSelected,onClick={chooseBasemap(mode)})
                    Column(Modifier.weight(1f)) {
                        Text(mode.title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        Text(if(BuildConfig.ONLINE_MAPS && mode==BasemapMode.SATELLITE) "按视野加载真实 Sentinel-2 2016 影像，约 10 米；可叠加街道" else mode.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(basemapMode==BasemapMode.SATELLITE) LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("叠加道路与地名",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium);Switch(checked=satelliteLabels,onCheckedChange={satelliteLabels=it;engine.satelliteLabels(it)})
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun SettingsSheet(dark: Boolean) {
        MapPanelSheet(onDismissRequest={if(!busy) settings=false}) {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal=20.dp),contentPadding=PaddingValues(bottom=40.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text("本地数据",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("${places.size} 个地点 / ${places.sumOf {it.photoCount}} 张原图，全部保存在应用私有目录。卸载会清除作品，请先备份。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                item {if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Text(progress)};if(lastMessage.isNotBlank()) Text(lastMessage,style=MaterialTheme.typography.bodySmall)}
                item {Button(onClick={launchSystemPicker {backup.launch("留影-${LocalDate.now()}.zip")}},enabled=!busy,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {Text("导出完整备份",fontWeight=FontWeight.SemiBold)}}
                item {OutlinedButton(onClick={launchSystemPicker {restore.launch(arrayOf("application/zip","application/octet-stream"))}},enabled=!busy,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {Text("从备份恢复")}}
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(4.dp))
                    Text("外观",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("跟随系统","浅色","深色").forEachIndexed {index,label->
                                val selectedTheme = themeChoice == index
                                TextButton(
                                    onClick={themeChoice=index;getSharedPreferences("settings",MODE_PRIVATE).edit().putInt("theme",index).apply()},
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(
                                        if (selectedTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f) else Color.Transparent
                                    )
                                ) {
                                    Text((if(selectedTheme) "✓ " else "")+label, fontWeight = if (selectedTheme) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
                item {HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f));Spacer(Modifier.height(4.dp));Text(if(BuildConfig.ONLINE_MAPS) "地图与缓存" else "离线地图",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Column(verticalArrangement=Arrangement.spacedBy(8.dp)){BasemapChoices()};Spacer(Modifier.height(6.dp));Text(mapStatus,style=MaterialTheme.typography.bodySmall);Text(if(BuildConfig.ONLINE_MAPS) "地图服务可获知请求区域及 IP。地点搜索使用 Android 系统服务或 Photon，仅提交你输入的地名。照片、EXIF、地点记录和备份均保存在本机。已浏览区域自动缓存。" else "APK 内置基础概览。一次导入全国精细包后，全国街道统一最高 Z15；当前地图中心进入覆盖区时自动使用。放大超过原始数据精度不会增加细节。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(!BuildConfig.ONLINE_MAPS) item {OutlinedButton(onClick={launchSystemPicker {importMap.launch(arrayOf("application/zip","application/octet-stream"))}},enabled=!busy,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {Text("导入离线地图包（全国 / 地区）")}}
                if(BuildConfig.ONLINE_MAPS) item {OutlinedButton(onClick={busy=true;progress="正在清理地图缓存";MapNetwork.clearCache(this@MainActivity){busy=false;message(it)}},enabled=!busy,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {Text("清理地图缓存（保留照片）")}}
                item {TextButton(onClick=::openMapSources,enabled=!busy) {Text("查看地图来源与许可（离线）")}}
                items(maps,key={it.directory.name}) { map->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Column(Modifier.weight(1f)) {Text(map.name);Text("Z${map.minZoom.toInt()}–${map.maxZoom.toInt()}",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={confirmation="移除地区地图包？摄影记录不受影响。" to {work("正在移除地图包") {packages.remove(map);maps=withContext(Dispatchers.IO) {packages.list()};engine.reloadPackages()}}},enabled=!busy) {Text("移除")}}}
                item {TextButton(onClick={launchSystemPicker {diagnosticExport.launch("PhotoMap-startup.txt")}}) {Text("导出启动故障报告")}}
                item {Text(if(BuildConfig.ONLINE_MAPS) "本版不需要导入全国地图包，可与离线版同时安装。两版作品分别保存在各自私有目录，可通过完整备份 ZIP 互相迁移。" else "全国精细包同时包含全国 Z15 街道、建筑与景点，以及城区精细卫星影像。全部数据保存在本机；导入前自动检查可用空间并校验完整性，成功后可删除原 ZIP。景点和路名完整度仍受上游数据限制。",style=MaterialTheme.typography.bodySmall)}
                item {Text("留影 ${BuildConfig.VERSION_NAME} · 无账号、无云同步、无广告。地图数据署名请见上方，数据来源与许可随 APK 保留。",style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
    private fun openMapSources() {
        work("正在读取本地许可") {
            licenseText = withContext(Dispatchers.IO) {
                val files = if (BuildConfig.ONLINE_MAPS) {
                    val online = listOf("ONLINE-LICENSE.txt", "FONT-LICENSE.txt", "VIEW-SOURCES.txt", "BOUNDARY-LICENSE.txt")
                    if (engine.hasOnlineOverview) online + listOf("overview/LICENSE.txt", "overview/SATELLITE-LICENSE.txt") else online
                } else listOf("LICENSE.txt", "FONT-LICENSE.txt", "VIEW-SOURCES.txt", "BOUNDARY-LICENSE.txt", "SATELLITE-LICENSE.txt", "URBAN-LICENSE.txt")
                files.joinToString("\n\n") { name -> assets.open("maps/$name").bufferedReader().use { it.readText() } }
            }
        }
    }
    override fun onStart() {super.onStart();if(::engine.isInitialized && !startupError) engine.view.onStart()}
    override fun onResume() {super.onResume();if(::engine.isInitialized && !startupError) {engine.view.onResume();engine.connectivityChanged()}}
    override fun onPause() {if(::engine.isInitialized && !startupError) engine.view.onPause();super.onPause()}
    override fun onStop() {if(::engine.isInitialized && !startupError) engine.view.onStop();super.onStop()}
    override fun onSaveInstanceState(outState: Bundle) {super.onSaveInstanceState(outState);if(::engine.isInitialized && !startupError) engine.view.onSaveInstanceState(outState)}
    override fun onLowMemory() {super.onLowMemory();if(::engine.isInitialized && !startupError) engine.view.onLowMemory()}
    override fun onDestroy() {if(::engine.isInitialized && !startupError) engine.destroy();super.onDestroy()}
}
