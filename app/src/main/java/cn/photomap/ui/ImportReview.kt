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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    fun placement(): PhotoPlacement? {
        if (!hasDate || point?.inCoverage != true) return null
        val exif = if (camera.isEmpty()) photo.exif else JSONObject(photo.exif).withCameraOverrides(camera).toString()
        return PhotoPlacement(photo.copy(date = date, exif = exif), point, placeName)
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
    val ready = pending.count { it.hasDate && it.point?.inCoverage == true }
    val colors = MaterialTheme.colorScheme
    FullScreenDialog(onDismiss = { if (!busy) onDismiss() }, dark = dark) {
        EditorPanel {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FrostedPanel(shape = RoundedCornerShape(20.dp), elevation = 4.dp) {
                        TextButton(onClick = onDismiss, enabled = !busy, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                            Text("返回", fontWeight = FontWeight.Medium)
                        }
                    }
                    Text("补填导入信息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("照片 ${index + 1} / ${pending.size} · $ready 张可保存", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            val previewShape = RoundedCornerShape(20.dp)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(176.dp)
                                    .clip(previewShape)
                                    .background(Color(0xff1c2423))
                                    .border(0.8.dp, colors.outlineVariant.copy(alpha = 0.5f), previewShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    library.file(item.photo.thumbnail), null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().blur(24.dp)
                                )
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                                AsyncImage(
                                    library.file(item.photo.thumbnail), item.photo.filename, contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(8.dp)
                                )
                            }
                            Text(item.photo.filename, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(onClick = { onSelect(index - 1) }, enabled = index > 0 && !busy, shape = RoundedCornerShape(16.dp)) { Text("上一张") }
                                OutlinedButton(onClick = { onSelect(index + 1) }, enabled = index < pending.lastIndex && !busy, shape = RoundedCornerShape(16.dp)) { Text("下一张") }
                            }
                        }
                    }
                    LiquidGlassCard(
                        Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                item.date, { onChange(item.copy(date = it.take(10))) }, Modifier.fillMaxWidth(),
                                label = { Text("拍摄日期 YYYY-MM-DD") }, singleLine = true, enabled = !busy,
                                shape = RoundedCornerShape(18.dp),
                                isError = item.date.isNotBlank() && !item.hasDate
                            )
                            if (pending.size > 1 && item.hasDate) {
                                TextButton(onClick = onApplyDate, enabled = !busy) { Text("为其他缺日期的照片使用此日期") }
                            }
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                            Text(
                                item.point?.let { "拍摄位置 %.5f, %.5f".format(it.latitude, it.longitude) }
                                    ?: "没有可用的 GPS，请在地图上选择位置。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(onClick = onPick, enabled = !busy, shape = RoundedCornerShape(18.dp)) { Text("地图选点") }
                            if (pending.size > 1 && item.point != null) {
                                TextButton(onClick = onApplyPoint, enabled = !busy) { Text("为其他缺位置的照片使用此位置") }
                            }
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                            TextButton(onClick = { showCamera = !showCamera }, enabled = !busy) {
                                Text(if (showCamera) "收起相机参数" else "补填或更正相机参数")
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
                    Text("原图保持原样。待补填照片可在本次运行中继续处理。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                FrostedPanel(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = 6.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDiscard, enabled = !busy) { Text("放弃待补填") }
                        Button(
                            onClick = onSave,
                            enabled = ready > 0 && !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("保存 $ready 张", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

