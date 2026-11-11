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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun OnlineMapChrome(
    placeCount: Int,
    photoCount: Int,
    pendingCount: Int,
    detail: String,
    enabled: Boolean,
    onMap: () -> Unit,
    onData: () -> Unit,
    onNational: () -> Unit,
    onPick: () -> Unit,
    onImport: () -> Unit,
    onMemories: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dockShape = RoundedCornerShape(28.dp)
    val pillShape = RoundedCornerShape(22.dp)
    val mapBtnInteraction = remember { MutableInteractionSource() }
    val dataBtnInteraction = remember { MutableInteractionSource() }
    val earthBtnInteraction = remember { MutableInteractionSource() }
    val memBtnInteraction = remember { MutableInteractionSource() }
    val pickBtnInteraction = remember { MutableInteractionSource() }
    val importBtnInteraction = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        // 1. Unified Top Header Bar (Single horizontal axis, 48dp minimum touch targets)
        FrostedPanel(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = dockShape,
            elevation = 8.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Brand emblem + title & stats
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.22f),
                                    colors.primaryContainer.copy(alpha = 0.55f)
                                )
                            )
                        )
                        .border(1.dp, colors.primary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(AppIconType.SPARKLES, tint = colors.primary, size = 16.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "留影",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    AnimatedContent(
                        targetState = "$placeCount 地点 · $photoCount 张",
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically { it / 3 })
                                .togetherWith(fadeOut(tween(140)) + slideOutVertically { -it / 3 })
                        },
                        label = "statsCounter"
                    ) { statsText ->
                        Text(
                            statsText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Right action pills inside the unified header bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onMap,
                        enabled = enabled,
                        interactionSource = mapBtnInteraction,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .liquidPress(mapBtnInteraction, 0.94f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.secondaryContainer.copy(alpha = 0.72f),
                            contentColor = colors.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        AppIcon(AppIconType.LAYERS, tint = colors.primary, size = 15.dp)
                        Spacer(Modifier.width(5.dp))
                        Text("底图", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = onData,
                        enabled = enabled,
                        interactionSource = dataBtnInteraction,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .liquidPress(dataBtnInteraction, 0.94f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.secondaryContainer.copy(alpha = 0.72f),
                            contentColor = colors.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        AppIcon(AppIconType.DATABASE, tint = colors.primary, size = 15.dp)
                        Spacer(Modifier.width(5.dp))
                        Text("数据", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Bottom Auxiliary Row + Floating Navigation Dock
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = detail.isNotBlank(),
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f)
                    ) {
                        FrostedPanel(shape = pillShape, elevation = 4.dp) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary)
                                )
                                Text(
                                    detail.substringBefore('\n'),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                FrostedPanel(
                    shape = pillShape,
                    elevation = 6.dp,
                    modifier = Modifier.liquidPress(earthBtnInteraction, 0.94f)
                ) {
                    TextButton(
                        onClick = onNational,
                        enabled = enabled,
                        interactionSource = earthBtnInteraction,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        AppIcon(AppIconType.COMPASS, tint = colors.primary, size = 16.dp)
                        Spacer(Modifier.width(5.dp))
                        Text("归位", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Bottom Primary Dock
            FrostedPanel(Modifier.fillMaxWidth(), dockShape, elevation = 12.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onMemories,
                        enabled = enabled,
                        interactionSource = memBtnInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .liquidPress(memBtnInteraction, 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        AppIcon(AppIconType.HISTORY, tint = colors.primary, size = 17.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("忆往昔", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(colors.outlineVariant.copy(alpha = 0.45f))
                    )
                    TextButton(
                        onClick = onPick,
                        enabled = enabled,
                        interactionSource = pickBtnInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .liquidPress(pickBtnInteraction, 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        AppIcon(AppIconType.CROSSHAIR, tint = colors.primary, size = 17.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("地图选点", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = onImport,
                        enabled = enabled,
                        interactionSource = importBtnInteraction,
                        modifier = Modifier
                            .weight(1.15f)
                            .heightIn(min = 48.dp)
                            .liquidPress(importBtnInteraction, 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        AppIcon(AppIconType.CAMERA_PLUS, tint = colors.onPrimary, size = 17.dp)
                        Spacer(Modifier.width(4.dp))
                        AnimatedContent(
                            targetState = if (pendingCount > 0) "待补填 $pendingCount" else "导入照片",
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "importBtnLabel"
                        ) { label ->
                            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
