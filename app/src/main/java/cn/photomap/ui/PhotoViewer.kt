package cn.photomap.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
                    TextButton(onClick=onClose, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("返回",color=Color.White, fontWeight = FontWeight.Medium)
                    }
                }
                FrostedPanel(shape = pill, elevation = 4.dp) {
                    Text("${pager.currentPage+1} / ${entries.size}", color=Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
                FrostedPanel(shape = pill, elevation = 4.dp) {
                    TextButton(onClick={info=!info}, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("信息",color=Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
            HorizontalPager(pager,modifier=Modifier.weight(1f),beyondViewportPageCount=0) { index ->
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
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(26.dp),
                    elevation = 8.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val recordTitle = entry.record.title.takeUnless { BuildConfig.ONLINE_MAPS && it == "照片导入" }.orEmpty()
                        Text(p.title.ifBlank {recordTitle}.ifBlank {p.filename},color=Color.White,style=MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines=2)
                        Text(p.date.ifBlank {entry.record.date},color=Color.LightGray, style = MaterialTheme.typography.labelMedium)
                        AnimatedVisibility(
                            visible = info,
                            enter = fadeIn(tween(200)) + expandVertically(),
                            exit = fadeOut(tween(150)) + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                val noteText = p.note.ifBlank {entry.record.note}
                                if (noteText.isNotBlank()) Text(noteText,color=Color.White,maxLines=5, style = MaterialTheme.typography.bodyMedium)
                                val meta=runCatching {JSONObject(p.exif)}.getOrDefault(JSONObject())
                                val overrides = if (BuildConfig.ONLINE_MAPS) p.cameraOverrides() else emptyMap()
                                val keys = cameraFields + listOf("gps_latitude" to "GPS纬度", "gps_longitude" to "GPS经度")
                                Text("${p.width} × ${p.height}" + (if(p.shotAt.isNotBlank()) " · ${p.shotAt}" else "") + keys.mapNotNull { (key,label)->(overrides[key] ?: meta.optString(key)).takeIf {it.isNotBlank()}?.let {"$label $it"} }.joinToString(" · ",prefix="\n"),color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
                                meta.optString("manual_parameters").takeIf {it.isNotBlank()}?.let {Text("参数补充 / 更正：$it",color=Color.LightGray,style=MaterialTheme.typography.bodySmall,maxLines=3)}
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick={onEdit(p)}) {Text("编辑文字与参数")}
                                    TextButton(onClick={onDelete(p)}) {Text("删除应用内副本", color = Color(0xffffb4ab))}
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

