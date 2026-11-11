package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cn.photomap.BuildConfig
import cn.photomap.data.*
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

data class ViewedPhoto(val photo: Photo, val record: Record)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun PhotoViewer(entries: List<ViewedPhoto>, initial: Int, library: Library, onPage: (Int)->Unit, onClose: ()->Unit, onEdit: (Photo)->Unit, onDelete: (Photo)->Unit) {
    val pager=rememberPagerState(initialPage=initial.coerceIn(0,maxOf(0,entries.lastIndex))) {entries.size}
    var info by remember {mutableStateOf(false)}
    LaunchedEffect(entries.map {it.photo.id}) {if(entries.isNotEmpty()) pager.scrollToPage(initial.coerceIn(0,entries.lastIndex))}
    LaunchedEffect(pager.currentPage) {onPage(pager.currentPage)}
    val pill = RoundedCornerShape(22.dp)
    FullScreenDialog(onDismiss=onClose,dark=true) {
        MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xffa8c9b7), surface=Color(0xff141c1a), surfaceVariant=Color(0xff202c29))) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                FrostedPanel(shape = pill, elevation = 4.dp) {
                    TextButton(onClick=onClose, modifier = Modifier.heightIn(min = 42.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        AppIcon(AppIconType.ARROW_LEFT, tint = Color.White, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("返回",color=Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                FrostedPanel(shape = pill, elevation = 4.dp) {
                    Text("${pager.currentPage+1} / ${entries.size}", color=Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                FrostedPanel(shape = pill, elevation = 4.dp) {
                    TextButton(onClick={info=!info}, modifier = Modifier.heightIn(min = 42.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        AppIcon(AppIconType.INFO, tint = Color.White, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("信息",color=Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            HorizontalPager(pager,modifier=Modifier.weight(if (info) 0.8f else 1f),beyondViewportPageCount=0) { index ->
                var scale by remember {mutableFloatStateOf(1f)}
                var translation by remember {mutableStateOf(Offset.Zero)}
                val transform=rememberTransformableState { zoom,pan,_->scale=(scale*zoom).coerceIn(1f,6f); translation=if(scale==1f) Offset.Zero else translation+pan }
                LaunchedEffect(pager.currentPage) {if(pager.currentPage!=index) {scale=1f;translation=Offset.Zero}}
                Box(Modifier.fillMaxSize().transformable(transform,canPan={scale>1f}).pointerInput(Unit) {detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2.5f;translation=Offset.Zero})},contentAlignment=Alignment.Center) {
                    AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(library.file(entries[index].photo.original)).size(if(scale>2f)4096 else 2048).build(),contentDescription=entries[index].photo.filename,contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxSize().graphicsLayer {scaleX=scale;scaleY=scale;translationX=translation.x.coerceIn(-size.width*(scale-1)/2,size.width*(scale-1)/2);translationY=translation.y.coerceIn(-size.height*(scale-1)/2,size.height*(scale-1)/2)})
                }
            }
            entries.getOrNull(pager.currentPage)?.let { entry ->
                val p=entry.photo
                FrostedPanel(
                    Modifier.fillMaxWidth().then(if (info) Modifier.weight(1f, fill = false) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(26.dp),
                    elevation = 8.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (info) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val recordTitle = entry.record.title.takeUnless { BuildConfig.ONLINE_MAPS && it == "照片导入" }.orEmpty()
                        Text(p.title.ifBlank {recordTitle}.ifBlank {p.filename},color=Color.White,style=MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines=2)
                        Text(p.date.ifBlank {entry.record.date},color=Color.LightGray, style = MaterialTheme.typography.labelMedium)
                        if (info) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                val noteText = p.note.ifBlank {entry.record.note}
                                if (noteText.isNotBlank()) Text(noteText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                val meta = remember(p.exif) { runCatching { JSONObject(p.exif) }.getOrDefault(JSONObject()) }
                                val overrides = remember(p.exif) {
                                    if (BuildConfig.ONLINE_MAPS) runCatching { p.cameraOverrides() }.getOrDefault(emptyMap()) else emptyMap()
                                }
                                val keys = cameraFields + listOf("gps_latitude" to "GPS纬度", "gps_longitude" to "GPS经度")
                                PhotoInfoRow("尺寸", "${p.width} × ${p.height}")
                                if (p.shotAt.isNotBlank()) PhotoInfoRow("拍摄时间", p.shotAt)
                                for ((key, label) in keys) {
                                    val value = overrides[key] ?: meta.optString(key)
                                    if (value.isNotBlank()) PhotoInfoRow(label, formatExifValue(key, value))
                                }
                                meta.optString("manual_parameters").takeIf { it.isNotBlank() }?.let { PhotoInfoRow("补充参数", it) }
                                HorizontalDivider(color = Color.White.copy(alpha = .16f))
                                TextButton(onClick = { onEdit(p) }, modifier = Modifier.fillMaxWidth()) { Text("编辑文字与参数") }
                                TextButton(onClick = { onDelete(p) }, modifier = Modifier.fillMaxWidth()) { Text("删除应用内副本", color = Color(0xffffb4ab)) }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PhotoInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(.36f), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(.64f), color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatExifValue(key: String, value: String): String {
    val parts = value.split('/', limit = 2)
    val numerator = parts[0].toDoubleOrNull() ?: return value
    val denominator = if (parts.size == 2) parts[1].toDoubleOrNull() ?: return value else 1.0
    val number = (numerator / denominator).takeIf { it.isFinite() } ?: return value
    val decimal = "%.3f".format(Locale.ROOT, number).trimEnd('0').trimEnd('.')
    return when (key) {
        "gps_latitude", "gps_longitude" -> "%.6f".format(Locale.ROOT, number)
        "focal_length" -> "$decimal mm"
        "aperture" -> "f/$decimal"
        "shutter_speed" -> if (number in 0.00001..0.5) "1/${(1.0 / number).roundToInt()} s" else "$decimal s"
        else -> value
    }
}

