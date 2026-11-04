package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnlineMapChrome(
    placeCount: Int,
    photoCount: Int,
    pendingCount: Int,
    detail: String,
    credits: String,
    enabled: Boolean,
    onMap: () -> Unit,
    onData: () -> Unit,
    onNational: () -> Unit,
    onPick: () -> Unit,
    onImport: () -> Unit,
    onMemories: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val panel = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) {
              FrostedPanel(shape = panel) {
                Column(Modifier.padding(horizontal = 17.dp, vertical = 10.dp)) {
                    Text("留影", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("$placeCount 个地点 · $photoCount 张照片", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FrostedPanel(shape = panel) { TextButton(onClick = onMap, enabled = enabled) { Text("底图") } }
                FrostedPanel(shape = panel) { TextButton(onClick = onData, enabled = enabled) { Text("数据") } }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                if (detail.isNotBlank()) {
                    Box(Modifier.weight(1f)) {
                        Surface(color = colors.surface.copy(alpha = .9f), shape = panel) {
                            Text(detail.substringBefore('\n'), Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else Spacer(Modifier.weight(1f))
                FrostedPanel(shape = panel) { TextButton(onClick = onNational, enabled = enabled) { Text("地球") } }
            }
            FrostedPanel(Modifier.fillMaxWidth(), panel) {
                Row(
                    Modifier.background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = .08f), colors.surface.copy(alpha = 0f))))
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onMemories, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("忆往昔") }
                    TextButton(onClick = onPick, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("地图选点") }
                    Button(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1.15f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (pendingCount > 0) "待补填 $pendingCount" else "导入照片")
                    }
                }
            }
            Text(
                credits,
                Modifier.background(colors.surface.copy(alpha = .92f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
