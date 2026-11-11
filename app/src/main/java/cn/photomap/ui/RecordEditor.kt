package cn.photomap.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.photomap.data.*
import java.time.LocalDate

class Draft(val placeId: String?, val recordId: String?, initialName: String, latitude: Double, longitude: Double, title: String="", note: String="", date: String=LocalDate.now().toString(), val existing: List<Photo> = emptyList()) {
    var name by mutableStateOf(initialName)
    var latitude by mutableDoubleStateOf(latitude)
    var longitude by mutableDoubleStateOf(longitude)
    var title by mutableStateOf(title)
    var note by mutableStateOf(note)
    var date by mutableStateOf(date)
    val added=mutableStateListOf<Photo>()
    var message by mutableStateOf("")
}
@Composable fun RecordEditor(draft: Draft, library: Library, busy: Boolean, onCancel: ()->Unit, onPick: ()->Unit, onMove: ()->Unit, onRemove: (Photo)->Unit, onSave: ()->Unit) {
    val valid=runCatching { LocalDate.parse(draft.date) }.isSuccess && draft.name.length<=200 && draft.title.length<=500 && draft.note.length<=100000 && (draft.added.isNotEmpty() || draft.existing.isNotEmpty())
    val fieldShape = RoundedCornerShape(18.dp)
    FullScreenDialog(onDismiss={ if(!busy) onCancel() }) {
        EditorPanel {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=8.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    FrostedPanel(shape = RoundedCornerShape(20.dp), elevation = 4.dp) {
                        TextButton(onClick=onCancel,enabled=!busy, modifier = Modifier.heightIn(min = 42.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                            AppIcon(AppIconType.ARROW_LEFT, tint = MaterialTheme.colorScheme.primary, size = 16.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("取消", fontWeight = FontWeight.Medium)
                        }
                    }
                    Button(
                        onClick=onSave,
                        enabled=valid && !busy,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.heightIn(min = 42.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        AppIcon(AppIconType.CHECK, tint = MaterialTheme.colorScheme.onPrimary, size = 16.dp)
                        Spacer(Modifier.width(5.dp))
                        Text("保存记录", fontWeight = FontWeight.SemiBold)
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=32.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if(draft.recordId==null) "留下此刻" else "编辑记录",style=MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            Text("照片与文字只保存在这部手机。请定期导出完整备份。",style=MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(draft.name,{ draft.name=it.take(200) },label={Text("拍摄地点")},singleLine=true,shape=fieldShape,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("%.6f, %.6f · WGS84".format(draft.latitude,draft.longitude), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick=onMove,enabled=!busy) { Text("在地图上调整位置") }
                                }
                            }
                        }
                    }
                    item {
                        LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if ((draft.existing + draft.added).isNotEmpty()) {
                                    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        items(draft.existing+draft.added,key={it.id}) { photo ->
                                            val thumbShape = RoundedCornerShape(16.dp)
                                            Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                AsyncImage(
                                                    library.file(photo.thumbnail),
                                                    contentDescription="已选照片",
                                                    contentScale=ContentScale.Crop,
                                                    modifier=Modifier.size(112.dp).clip(thumbShape).border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thumbShape)
                                                )
                                                if(photo in draft.added) TextButton(onClick={onRemove(photo)},enabled=!busy) {Text("移除")}
                                            }
                                        }
                                    }
                                }
                                OutlinedButton(onClick=onPick,enabled=!busy,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(top=4.dp)) { Text("从系统相册导入照片") }
                                if(draft.message.isNotBlank()) Text(draft.message,style=MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        LiquidGlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(draft.title,{draft.title=it.take(500)},label={Text("记录标题（可选）")},shape=fieldShape,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                OutlinedTextField(draft.date,{draft.date=it},label={Text("拍摄日期 YYYY-MM-DD")},singleLine=true,shape=fieldShape,isError=runCatching {LocalDate.parse(draft.date)}.isFailure,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                                OutlinedTextField(draft.note,{draft.note=it.take(100000)},label={Text("摄影心得（可选）")},minLines=5,shape=fieldShape,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                            }
                        }
                    }
                    item { Text("保留导入的原图，不修改系统相册。可用 EXIF 会随每张照片保存。",style=MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

