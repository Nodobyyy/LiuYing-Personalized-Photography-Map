package cn.photomap.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
@Composable
fun PhotoViewer(
    entries: List<ViewedPhoto>,
    initial: Int,
    library: Library,
    onPage: (Int) -> Unit,
    onClose: () -> Unit,
    onEdit: (Photo) -> Unit,
    onDelete: (Photo) -> Unit
) {
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, maxOf(0, entries.lastIndex))) { entries.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(entries.map { it.photo.id }) { if (entries.isNotEmpty()) pager.scrollToPage(initial.coerceIn(0, entries.lastIndex)) }
    LaunchedEffect(pager.currentPage) { onPage(pager.currentPage) }

    FullScreenPage(title = "照片", background = Color.Black, onClose = onClose) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xff6ee7b7),
                onPrimary = Color(0xff06281e),
                surface = Color(0xff10171a),
                onSurface = Color(0xfff1f5f9),
                surfaceVariant = Color(0xff1a2428),
                onSurfaceVariant = Color(0xff94a3b8),
                outlineVariant = Color(0xff2c3b40)
            )
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val infoMaxHeight = minOf(320.dp, maxHeight * 0.45f)
                // 1. Full-bleed interactive photo pager
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { index ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var translation by remember { mutableStateOf(Offset.Zero) }
                    val transform = rememberTransformableState { zoom, pan, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        translation = if (scale == 1f) Offset.Zero else translation + pan
                    }
                    LaunchedEffect(pager.currentPage) {
                        if (pager.currentPage != index) {
                            scale = 1f
                            translation = Offset.Zero
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .transformable(transform, canPan = { scale > 1f })
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (infoVisible) infoVisible = false
                                        else chromeVisible = !chromeVisible
                                    },
                                    onDoubleTap = {
                                        scale = if (scale > 1.2f) 1f else 2.5f
                                        translation = Offset.Zero
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(library.file(entries[index].photo.original))
                                .size(if (scale > 2f) 4096 else 2048)
                                .build(),
                            contentDescription = entries[index].photo.filename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = translation.x.coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                                    translationY = translation.y.coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2)
                                }
                        )
                    }
                }

                // 2. Top floating overlay bar (fades smoothly on single tap)
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ViewerIconPill(
                                icon = AppIconType.ARROW_LEFT,
                                description = "返回",
                                onClick = onClose
                            )
                            FrostedPanel(
                                shape = RoundedCornerShape(20.dp),
                                elevation = 0.dp,
                                tintColor = Color(0x99121A1D)
                            ) {
                                Text(
                                    "${pager.currentPage + 1} / ${entries.size}",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            ViewerIconPill(
                                icon = if (infoVisible) AppIconType.CLOSE else AppIconType.INFO,
                                description = if (infoVisible) "关闭信息" else "照片信息",
                                active = infoVisible,
                                onClick = { infoVisible = !infoVisible }
                            )
                        }
                    }
                }

                // 3. Bottom caption + quick actions (Edit / Delete / Info)
                val currentEntry = entries.getOrNull(pager.currentPage)
                AnimatedVisibility(
                    visible = chromeVisible && currentEntry != null,
                    enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    currentEntry?.let { entry ->
                        val p = entry.photo
                        val recordTitle = entry.record.title.takeUnless { BuildConfig.ONLINE_MAPS && it == "照片导入" }.orEmpty()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Slide-up glass EXIF & notes panel
                            AnimatedVisibility(
                                visible = infoVisible,
                                enter = fadeIn(tween(200)) + slideInVertically { it / 3 },
                                exit = fadeOut(tween(150)) + slideOutVertically { it / 3 }
                            ) {
                                FrostedPanel(
                                    Modifier.fillMaxWidth().heightIn(max = infoMaxHeight),
                                    shape = RoundedCornerShape(24.dp),
                                    elevation = 10.dp,
                                    tintColor = Color(0xE610171A)
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val noteText = p.note.ifBlank { entry.record.note }
                                        if (noteText.isNotBlank()) {
                                            Text(noteText, color = Color(0xFFF1F5F9), style = MaterialTheme.typography.bodyMedium)
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                                        }
                                        val meta = remember(p.exif) { runCatching { JSONObject(p.exif) }.getOrDefault(JSONObject()) }
                                        val overrides = remember(p.exif) {
                                            if (BuildConfig.ONLINE_MAPS) runCatching { p.cameraOverrides() }.getOrDefault(emptyMap()) else emptyMap()
                                        }
                                        val keys = cameraFields + listOf("gps_latitude" to "GPS 纬度", "gps_longitude" to "GPS 经度")
                                        PhotoInfoRow("尺寸", "${p.width} × ${p.height}")
                                        if (p.shotAt.isNotBlank()) PhotoInfoRow("拍摄时间", p.shotAt)
                                        for ((key, label) in keys) {
                                            val value = overrides[key] ?: meta.optString(key)
                                            if (value.isNotBlank()) PhotoInfoRow(label, formatExifValue(key, value))
                                        }
                                        meta.optString("manual_parameters").takeIf { it.isNotBlank() }?.let {
                                            PhotoInfoRow("补充参数", it)
                                        }
                                    }
                                }
                            }

                            // Compact bottom bar: Title & Date on left, Edit & Delete icons on right
                            FrostedPanel(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                elevation = 8.dp,
                                tintColor = Color(0xCC121A1D)
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            p.title.ifBlank { recordTitle }.ifBlank { p.filename },
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            p.date.ifBlank { entry.record.date }.ifBlank { "未记录日期" },
                                            color = Color(0xFF94A3B8),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    ViewerActionButton(
                                        icon = AppIconType.EDIT,
                                        tint = Color(0xFF6EE7B7),
                                        description = "编辑文字与参数",
                                        onClick = { onEdit(p) }
                                    )
                                    ViewerActionButton(
                                        icon = AppIconType.TRASH,
                                        tint = Color(0xFFFCA5A5),
                                        description = "删除照片",
                                        onClick = { onDelete(p) }
                                    )
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
private fun ViewerIconPill(
    icon: AppIconType,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    FrostedPanel(
        modifier = Modifier
            .size(44.dp)
            .liquidPress(interactionSource, 0.92f),
        shape = CircleShape,
        elevation = 0.dp,
        tintColor = if (active) Color(0xCC1C4A3B) else Color(0x99121A1D)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(icon, tint = if (active) Color(0xFF6EE7B7) else Color.White, size = 18.dp)
        }
    }
}

@Composable
private fun ViewerActionButton(
    icon: AppIconType,
    tint: Color,
    description: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .liquidPress(interactionSource, 0.92f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, tint = tint, size = 17.dp)
    }
}

@Composable
private fun PhotoInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(.34f), color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(.66f), color = Color(0xFFF1F5F9), style = MaterialTheme.typography.bodySmall)
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
