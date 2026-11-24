package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.photomap.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class Draft(
    val placeId: String?,
    val recordId: String?,
    initialName: String,
    latitude: Double,
    longitude: Double,
    title: String = "",
    note: String = "",
    date: String = LocalDate.now().toString(),
    val existing: List<Photo> = emptyList()
) {
    var name by mutableStateOf(initialName)
    var latitude by mutableDoubleStateOf(latitude)
    var longitude by mutableDoubleStateOf(longitude)
    var title by mutableStateOf(title)
    var note by mutableStateOf(note)
    var date by mutableStateOf(date)
    val added = mutableStateListOf<Photo>()
    var message by mutableStateOf("")
    val isUntouchedNewDraft: Boolean
        get() = recordId == null && existing.isEmpty() && added.isEmpty() && title.isBlank() && note.isBlank()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit
) {
    val initialMillis = remember(initialDate) {
        val parsed = runCatching { LocalDate.parse(initialDate) }.getOrNull() ?: LocalDate.now()
        parsed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(picked.toString())
                }
                onDismiss()
            }) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun RecordEditor(
    draft: Draft,
    library: Library,
    busy: Boolean,
    onCancel: () -> Unit,
    onPick: () -> Unit,
    onMove: () -> Unit,
    onRemove: (Photo) -> Unit,
    onSave: () -> Unit
) {
    val hasPhotos = draft.added.isNotEmpty() || draft.existing.isNotEmpty()
    val validDate = runCatching { LocalDate.parse(draft.date) }.isSuccess
    val valid = validDate && draft.name.length <= 200 && draft.title.length <= 500 && draft.note.length <= 100000 && hasPhotos
    val fieldShape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    var showDatePicker by remember { mutableStateOf(false) }

    FullScreenDialog(onDismiss = { if (!busy) onCancel() }, compactTop = true) {
        EditorPanel {
            Column(Modifier.fillMaxSize().imePadding()) {
                // Clean unified top bar
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrostedPanel(shape = RoundedCornerShape(20.dp), elevation = 4.dp) {
                        TextButton(
                            onClick = onCancel,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 42.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            AppIcon(AppIconType.ARROW_LEFT, tint = colors.primary, size = 16.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("取消", fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(
                        if (draft.recordId == null) "新建记录" else "编辑记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Button(
                        onClick = onSave,
                        enabled = valid && !busy,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.heightIn(min = 42.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        AppIcon(AppIconType.CHECK, tint = if (valid && !busy) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f), size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                }

                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
                ) {
                    // Step 1: Photos (put photos first for a photography app!)
                    item {
                        LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), selected = !hasPhotos) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("拍摄照片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (hasPhotos) "已选 ${draft.existing.size + draft.added.size} 张" else "至少选择 1 张",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (hasPhotos) colors.primary else colors.onSurfaceVariant
                                    )
                                }
                                if (hasPhotos) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(draft.existing + draft.added, key = { it.id }) { photo ->
                                            val thumbShape = RoundedCornerShape(16.dp)
                                            Box(Modifier.size(108.dp)) {
                                                AsyncImage(
                                                    library.file(photo.thumbnail),
                                                    contentDescription = "已选照片",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(thumbShape)
                                                        .border(0.8.dp, colors.outlineVariant.copy(alpha = 0.5f), thumbShape)
                                                )
                                                if (photo in draft.added) {
                                                    Box(
                                                        Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(6.dp)
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.65f))
                                                            .clickable(enabled = !busy) { onRemove(photo) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        AppIcon(AppIconType.CLOSE, tint = Color.White, size = 13.dp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                FilledTonalButton(
                                    onClick = onPick,
                                    enabled = !busy,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    AppIcon(AppIconType.CAMERA_PLUS, tint = colors.primary, size = 17.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (hasPhotos) "继续添加照片" else "从系统相册选择照片", fontWeight = FontWeight.SemiBold)
                                }
                                if (draft.message.isNotBlank()) {
                                    Text(draft.message, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Step 2: Location
                    item {
                        LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    draft.name,
                                    { draft.name = it.take(200) },
                                    label = { Text("拍摄地点名称") },
                                    leadingIcon = { AppIcon(AppIconType.MAP_PIN, tint = colors.primary, size = 18.dp) },
                                    singleLine = true,
                                    shape = fieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !busy
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "%.5f, %.5f · WGS84".format(draft.latitude, draft.longitude),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant
                                    )
                                    TextButton(onClick = onMove, enabled = !busy) {
                                        Text("地图微调位置", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // Step 3: Details & Date with native Calendar Picker
                    item {
                        LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    draft.date,
                                    { draft.date = it.take(10) },
                                    label = { Text("拍摄日期") },
                                    singleLine = true,
                                    shape = fieldShape,
                                    isError = !validDate,
                                    supportingText = if (!validDate) {
                                        { Text("请使用 YYYY-MM-DD 格式或点右侧日历选择") }
                                    } else null,
                                    trailingIcon = {
                                        IconButton(onClick = { showDatePicker = true }, enabled = !busy) {
                                            AppIcon(AppIconType.CALENDAR, tint = colors.primary, size = 18.dp)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !busy
                                )
                                OutlinedTextField(
                                    draft.title,
                                    { draft.title = it.take(500) },
                                    label = { Text("记录标题（可选）") },
                                    singleLine = true,
                                    shape = fieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !busy
                                )
                                OutlinedTextField(
                                    draft.note,
                                    { draft.note = it.take(100000) },
                                    label = { Text("摄影心得 / 故事（可选）") },
                                    minLines = 4,
                                    shape = fieldShape,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !busy
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "原图与 EXIF 均保留在本机私有目录，不改动系统相册。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    if (showDatePicker) {
        PhotoDatePickerDialog(
            initialDate = draft.date,
            onDismiss = { showDatePicker = false },
            onPicked = { draft.date = it }
        )
    }
}
