package cn.photomap.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnlineMapChrome(
    placeCount: Int,
    photoCount: Int,
    pendingCount: Int,
    detail: String,
    showAttribution: Boolean,
    enabled: Boolean,
    onMap: () -> Unit,
    onData: () -> Unit,
    onNational: () -> Unit,
    onPick: () -> Unit,
    onImport: () -> Unit,
    onMemories: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val panel = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(24.dp)
    val mapBtnInteraction = remember { MutableInteractionSource() }
    val dataBtnInteraction = remember { MutableInteractionSource() }
    val earthBtnInteraction = remember { MutableInteractionSource() }
    val memBtnInteraction = remember { MutableInteractionSource() }
    val pickBtnInteraction = remember { MutableInteractionSource() }
    val importBtnInteraction = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) {
                FrostedPanel(shape = panel, elevation = 8.dp) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(colors.primary, colors.primary.copy(alpha = 0.4f))
                                    )
                                )
                        )
                        Column {
                            Text("留影", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            AnimatedContent(
                                targetState = "$placeCount 个地点 · $photoCount 张照片",
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInVertically { it / 3 })
                                        .togetherWith(fadeOut(tween(150)) + slideOutVertically { -it / 3 })
                                },
                                label = "statsCounter"
                            ) { statsText ->
                                Text(statsText, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                            if (showAttribution) Text("© OpenStreetMap · OpenMapTiles · OpenFreeMap · Protomaps · EOX",
                                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FrostedPanel(shape = pill, elevation = 6.dp, modifier = Modifier.liquidPress(mapBtnInteraction, 0.94f)) {
                    TextButton(
                        onClick = onMap,
                        enabled = enabled,
                        interactionSource = mapBtnInteraction,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("底图", fontWeight = FontWeight.Medium) }
                }
                FrostedPanel(shape = pill, elevation = 6.dp, modifier = Modifier.liquidPress(dataBtnInteraction, 0.94f)) {
                    TextButton(
                        onClick = onData,
                        enabled = enabled,
                        interactionSource = dataBtnInteraction,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("数据", fontWeight = FontWeight.Medium) }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = detail.isNotBlank(),
                        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f)
                    ) {
                        FrostedPanel(shape = pill, elevation = 4.dp) {
                            Text(
                                detail.substringBefore('\n'),
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                FrostedPanel(shape = pill, elevation = 6.dp, modifier = Modifier.liquidPress(earthBtnInteraction, 0.94f)) {
                    TextButton(
                        onClick = onNational,
                        enabled = enabled,
                        interactionSource = earthBtnInteraction,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("归位", fontWeight = FontWeight.Medium) }
                }
            }
            FrostedPanel(Modifier.fillMaxWidth(), panel, elevation = 10.dp) {
                Row(
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    colors.primary.copy(alpha = .12f),
                                    colors.primaryContainer.copy(alpha = .08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onMemories,
                        enabled = enabled,
                        interactionSource = memBtnInteraction,
                        modifier = Modifier.weight(1f).liquidPress(memBtnInteraction, 0.95f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) { Text("忆往昔", fontWeight = FontWeight.Medium) }
                    Box(Modifier.width(1.dp).height(20.dp).background(colors.outlineVariant.copy(alpha = 0.4f)))
                    TextButton(
                        onClick = onPick,
                        enabled = enabled,
                        interactionSource = pickBtnInteraction,
                        modifier = Modifier.weight(1f).liquidPress(pickBtnInteraction, 0.95f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) { Text("地图选点", fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = onImport,
                        enabled = enabled,
                        interactionSource = importBtnInteraction,
                        modifier = Modifier.weight(1.18f).liquidPress(importBtnInteraction, 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        AnimatedContent(
                            targetState = if (pendingCount > 0) "待补填 $pendingCount" else "导入照片",
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "importBtnLabel"
                        ) { label ->
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

