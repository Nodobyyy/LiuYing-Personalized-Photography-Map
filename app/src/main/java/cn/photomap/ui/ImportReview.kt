package cn.photomap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    FullScreenDialog(onDismiss = { if (!busy) onDismiss() }, dark = dark) {
        EditorPanel {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("返回") }
                    Text("补填导入信息", style = MaterialTheme.typography.headlineSmall)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("照片 ${index + 1} / ${pending.size} · $ready 张可保存", style = MaterialTheme.typography.labelLarge)
                    AsyncImage(
                        library.file(item.photo.thumbnail), item.photo.filename, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(18.dp))
                    )
                    Text(item.photo.filename, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { onSelect(index - 1) }, enabled = index > 0 && !busy) { Text("上一张") }
                        OutlinedButton(onClick = { onSelect(index + 1) }, enabled = index < pending.lastIndex && !busy) { Text("下一张") }
                    }
                    OutlinedTextField(
                        item.date, { onChange(item.copy(date = it.take(10))) }, Modifier.fillMaxWidth(),
                        label = { Text("拍摄日期 YYYY-MM-DD") }, singleLine = true, enabled = !busy,
                        isError = item.date.isNotBlank() && !item.hasDate
                    )
                    if (pending.size > 1 && item.hasDate) {
                        TextButton(onClick = onApplyDate, enabled = !busy) { Text("为其他缺日期的照片使用此日期") }
                    }
                    Text(
                        item.point?.let { "拍摄位置 %.5f, %.5f".format(it.latitude, it.longitude) }
                            ?: "没有可用的 GPS，请在地图上选择位置。"
                    )
                    OutlinedButton(onClick = onPick, enabled = !busy) { Text("地图选点") }
                    if (pending.size > 1 && item.point != null) {
                        TextButton(onClick = onApplyPoint, enabled = !busy) { Text("为其他缺位置的照片使用此位置") }
                    }
                    TextButton(onClick = { showCamera = !showCamera }, enabled = !busy) {
                        Text(if (showCamera) "收起相机参数" else "补填或更正相机参数")
                    }
                    if (showCamera) CameraFields(item.photo, item.camera, !busy) { onChange(item.copy(camera = it)) }
                    Text("原图保持原样。待补填照片可在本次运行中继续处理。", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(onClick = onDiscard, enabled = !busy) { Text("放弃待补填") }
                    Button(onClick = onSave, enabled = ready > 0 && !busy, modifier = Modifier.weight(1f)) { Text("保存 $ready 张") }
                }
            }
        }
    }
}
