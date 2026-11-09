package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.absoluteValue

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
    FullScreenDialog(onDismiss = onClose, dark = dark, compactTop = true) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.surface, colors.surfaceVariant, colors.surface)))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FrostedPanel(shape = RoundedCornerShape(20.dp), elevation = 4.dp) {
                    TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("返回", fontWeight = FontWeight.Medium)
                    }
                }
                Column {
                    Text("忆往昔", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("回到 ${anniversary.format(dateFormat)} 前后", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            if (days.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    FrostedPanel(shape = RoundedCornerShape(28.dp), elevation = 6.dp) {
                        Text(
                            "有拍摄日期的照片会出现在这里。导入照片后，就可以回看这一天前后的记录。",
                            Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                val rimBorder = remember {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.36f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.24f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(800f, 1200f)
                    )
                }
                VerticalPager(
                    state = pager,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp),
                    pageSpacing = 16.dp,
                    key = { days[it].date.toEpochDay() }
                ) { index ->
                    val day = days[index]
                    val first = day.photos.first()
                    val shape = RoundedCornerShape(30.dp)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                val pageOffset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue
                                val fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                val s = 0.93f + 0.07f * fraction
                                scaleX = s
                                scaleY = s
                                alpha = 0.72f + 0.28f * fraction
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.4f))
                                .clip(shape)
                                .background(Color(0xff1e2526))
                                .border(1.dp, rimBorder, shape)
                                .clickable { onOpen(day, 0) }
                        ) {
                            AsyncImage(
                                library.file(first.photo.thumbnail), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(30.dp)
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .26f)))
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(library.file(first.photo.original)).size(1080).build(),
                                contentDescription = first.photo.title.ifBlank { first.photo.filename },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(bottom = 84.dp)
                            )
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0D1312), Color(0xEB0A0E0E))))
                                    .padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(day.date.format(dateFormat), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                val tag = when {
                                    day.date == anniversary -> "去年今天"
                                    index == anchor -> "最接近去年今天"
                                    else -> first.placeName
                                }
                                Text("$tag · ${day.photos.size} 张", color = Color.White.copy(alpha = .90f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (day.photos.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(day.photos.size, key = { day.photos[it].photo.id }) { photoIndex ->
                                    val photo = day.photos[photoIndex].photo
                                    val thumbShape = RoundedCornerShape(14.dp)
                                    AsyncImage(
                                        library.file(photo.thumbnail), photo.filename, contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(thumbShape)
                                            .border(0.8.dp, colors.outlineVariant.copy(alpha = 0.5f), thumbShape)
                                            .clickable { onOpen(day, photoIndex) }
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

