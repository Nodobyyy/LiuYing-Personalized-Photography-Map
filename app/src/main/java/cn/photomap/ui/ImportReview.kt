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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.photomap.data.Library
import cn.photomap.data.Photo
import cn.photomap.data.PhotoPlacement
import cn.photomap.data.PhotoPoint
import cn.photomap.data.withCameraOverrides
import coil.compose.AsyncImage
import org.json.JSONObject
import java.time.LocalDate

data class PendingPhoto(
    val photo: Photo,
    val point: PhotoPoint?,
    val date: String = photo.date,
    val camera: Map<String, String> = emptyMap(),
    val placeName: String = ""
) {
    val hasDate get() = runCatching { LocalDate.parse(date) }.isSuccess
    val isReady get() = hasDate && point?.inCoverage == true
    fun placement(): PhotoPlacement? {
        if (!isReady) return null
        val exif = if (camera.isEmpty()) photo.exif else JSONObject(photo.exif).withCameraOverrides(camera).toString()
        return PhotoPlacement(photo.copy(date = date, exif = exif), point!!, placeName)
    }
}

@Composable
fun ImportReview(
    pending: List<PendingPhoto>,
    index: Int,
    busy: Boolean,
    dark: Boolean,
    library: Library,
    onSelect: (Int) -> Unit,
    onChange: (PendingPhoto) -> Unit,
    onPick: () -> Unit,
    onApplyDate: () -> Unit,
    onApplyPoint: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    val item = pending.getOrNull(index) ?: return
    var showCamera by remember(item.photo.id) { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val ready = pending.count { it.isReady }
    val colors = MaterialTheme.colorScheme

    FullScreenDialog(onDismiss = { if (!busy) onDismiss() }, dark = dark, background = colors.surface) {
        EditorPanel {
            Column(Modifier.fillMaxSize().imePadding()) {
                // Top Bar
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FrostedPanel(shape = RoundedCornerShape(20.dp), elevation = 4.dp) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 42.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            AppIcon(AppIconType.ARROW_LEFT, tint = colors.primary, size = 16.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("返回", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("补填照片信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "共 ${pending.size} 张 · $ready 张已就绪",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                    }
                }

                // Filmstrip thumbnail selector for instant switching between pending photos
                if (pending.size > 1) {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pending.size, key = { pending[it].photo.id }) { i ->
                            val entry = pending[i]
                            val isCurrent = i == index
                            val shape = RoundedCornerShape(14.dp)
                            Box(
                                Modifier
                                    .size(58.dp)
                                    .clip(shape)
                                    .border(
                                        width = if (isCurrent) 2.2.dp else 1.dp,
                                        color = if (isCurrent) colors.primary else colors.outlineVariant.copy(alpha = 0.45f),
                                        shape = shape
                                    )
                                    .clickable(enabled = !busy) { onSelect(i) }
                            ) {
                                AsyncImage(
                                    library.file(entry.photo.thumbnail),
                                    contentDescription = entry.photo.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Ready / Pending indicator dot
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (entry.isReady) Color(0xFF10B981) else Color(0xFFF59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (entry.isReady) {
                                        AppIcon(AppIconType.CHECK, tint = Color.White, size = 10.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Photo Preview Card
                    LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val previewShape = RoundedCornerShape(18.dp)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(172.dp)
                                    .clip(previewShape)
                                    .background(Color(0xff141c1e))
                                    .border(0.8.dp, colors.outlineVariant.copy(alpha = 0.45f), previewShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    library.file(item.photo.thumbnail), null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().blur(24.dp)
                                )
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                                AsyncImage(
                                    library.file(item.photo.thumbnail), item.photo.filename, contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(6.dp)
                                )
                            }
                            Text(
                                item.photo.filename,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Form Card: Date + Location + Camera EXIF
                    LiquidGlassCard(
                        Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                item.date,
                                { onChange(item.copy(date = it.take(10))) },
                                Modifier.fillMaxWidth(),
                                label = { Text("拍摄日期") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(16.dp),
                                isError = item.date.isNotBlank() && !item.hasDate,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }, enabled = !busy) {
                                        AppIcon(AppIconType.CALENDAR, tint = colors.primary, size = 18.dp)
                                    }
                                }
                            )
                            if (pending.size > 1 && item.hasDate) {
                                FilledTonalButton(
                                    onClick = onApplyDate,
                                    enabled = !busy,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("补齐其他照片的日期", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        if (item.point != null) item.placeName.ifBlank { "已选择位置" } else "缺少拍摄位置",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.point != null) colors.onSurface else colors.error
                                    )
                                    Text(
                                        item.point?.let { "%.5f, %.5f".format(it.latitude, it.longitude) }
                                            ?: "点击右侧按钮在地图上标记拍摄点",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onPick,
                                    enabled = !busy,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    AppIcon(AppIconType.CROSSHAIR, tint = colors.primary, size = 16.dp)
                                    Spacer(Modifier.width(5.dp))
                                    Text("选点", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (pending.size > 1 && item.point != null) {
                                FilledTonalButton(
                                    onClick = onApplyPoint,
                                    enabled = !busy,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("补齐其他照片的位置", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

                            TextButton(onClick = { showCamera = !showCamera }, enabled = !busy) {
                                Text(if (showCamera) "收起相机参数" else "编辑相机参数")
                            }
                            AnimatedVisibility(
                                visible = showCamera,
                                enter = fadeIn(tween(180)) + expandVertically(),
                                exit = fadeOut(tween(140)) + shrinkVertically()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CameraFields(item.photo, item.camera, !busy) { onChange(item.copy(camera = it)) }
                                }
                            }
                        }
                    }
                }

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                // Bottom Action Bar
                FrostedPanel(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = 6.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.weight(0.9f)) {
                            Text("放弃", color = colors.error)
                        }
                        Button(
                            onClick = onSave,
                            enabled = ready > 0 && !busy,
                            modifier = Modifier.weight(1.4f).height(46.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("保存 $ready 张", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        PhotoDatePickerDialog(
            initialDate = item.date,
            onDismiss = { showDatePicker = false },
            onPicked = { onChange(item.copy(date = it)) }
        )
    }
}
