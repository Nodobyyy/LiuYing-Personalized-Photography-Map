package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.photomap.data.Library
import cn.photomap.data.PhotoDay
import cn.photomap.data.anniversaryPage
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Memories(
    days: List<PhotoDay>,
    anniversary: LocalDate,
    dark: Boolean,
    library: Library,
    onClose: () -> Unit,
    onOpen: (PhotoDay, Int) -> Unit
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日") }
    val anchor = remember(days, anniversary) { anniversaryPage(days, anniversary) }
    val pager = rememberPagerState(initialPage = anchor) { days.size }
    FullScreenDialog(onDismiss = onClose, dark = dark) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.surface, colors.surfaceVariant, colors.surface)))
                .systemBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("返回") }
                Column(Modifier.padding(start = 8.dp)) {
                    Text("忆往昔", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("回到 ${anniversary.format(dateFormat)} 前后", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (days.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("有拍摄日期的照片会出现在这里。导入照片后，就可以回看这一天前后的记录。")
                }
            } else {
                VerticalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    pageSpacing = 14.dp,
                    key = { days[it].date.toEpochDay() }
                ) { index ->
                    val day = days[index]
                    val first = day.photos.first()
                    val shape = RoundedCornerShape(28.dp)
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f).clip(shape).background(Color(0xff22292a))
                                .clickable { onOpen(day, 0) }
                        ) {
                            AsyncImage(
                                library.file(first.photo.thumbnail), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(28.dp)
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(library.file(first.photo.original)).size(1080).build(),
                                contentDescription = first.photo.title.ifBlank { first.photo.filename },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(bottom = 75.dp)
                            )
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .84f))))
                                    .padding(start = 17.dp, end = 17.dp, top = 20.dp, bottom = 16.dp)
                            ) {
                                Text(day.date.format(dateFormat), color = Color.White, style = MaterialTheme.typography.titleLarge)
                                val tag = when {
                                    day.date == anniversary -> "去年今天"
                                    index == anchor -> "最接近去年今天"
                                    else -> first.placeName
                                }
                                Text("$tag · ${day.photos.size} 张", color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (day.photos.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(day.photos.size, key = { day.photos[it].photo.id }) { photoIndex ->
                                    val photo = day.photos[photoIndex].photo
                                    AsyncImage(library.file(photo.thumbnail), photo.filename, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(day, photoIndex) })
                                }
                            }
                        }
                    }
                }
                Text(
                    "上滑看更早 · 下滑看更新",
                    Modifier.align(Alignment.CenterHorizontally).padding(10.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
