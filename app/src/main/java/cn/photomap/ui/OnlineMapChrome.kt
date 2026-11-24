package cn.photomap.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnlineMapChrome(
    placeCount: Int,
    photoCount: Int,
    pendingCount: Int,
    detail: String,
    enabled: Boolean,
    onPlaces: () -> Unit,
    onMap: () -> Unit,
    onData: () -> Unit,
    onNational: () -> Unit,
    onPick: () -> Unit,
    onImport: () -> Unit,
    onMemories: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dockShape = RoundedCornerShape(30.dp)
    val pillShape = RoundedCornerShape(24.dp)
    val brandInteraction = remember { MutableInteractionSource() }
    val mapBtnInteraction = remember { MutableInteractionSource() }
    val dataBtnInteraction = remember { MutableInteractionSource() }
    val earthBtnInteraction = remember { MutableInteractionSource() }
    val memBtnInteraction = remember { MutableInteractionSource() }
    val pickBtnInteraction = remember { MutableInteractionSource() }
    val importBtnInteraction = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        // 1. Airy Floating Top Bar: Left tappable footprints pill + Right circular glass controls
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tappable Brand & Footprints Pill -> opens "我的足迹" quick-jump drawer
            FrostedPanel(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .liquidPress(brandInteraction, 0.96f),
                shape = pillShape,
                elevation = 8.dp
            ) {
                Row(
                    Modifier
                        .clickable(
                            enabled = enabled,
                            interactionSource = brandInteraction,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClick = onPlaces
                        )
                        .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        colors.primary.copy(alpha = 0.24f),
                                        colors.primaryContainer.copy(alpha = 0.65f)
                                    )
                                )
                            )
                            .border(1.dp, colors.primary.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(AppIconType.SPARKLES, tint = colors.primary, size = 15.dp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            "留影",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        AnimatedContent(
                            targetState = if (placeCount == 0) "点击查看足迹" else "$placeCount 地点 · $photoCount 张",
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
                    AppIcon(AppIconType.CHEVRON_RIGHT, tint = colors.onSurfaceVariant.copy(alpha = 0.7f), size = 14.dp)
                }
            }

            Spacer(Modifier.width(10.dp))

            // Right circular glass action buttons: Layers & Settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularGlassButton(
                    icon = AppIconType.LAYERS,
                    description = "底图与图层",
                    enabled = enabled,
                    interactionSource = mapBtnInteraction,
                    onClick = onMap
                )
                CircularGlassButton(
                    icon = AppIconType.SLIDERS,
                    description = "设置与数据备份",
                    enabled = enabled,
                    interactionSource = dataBtnInteraction,
                    onClick = onData
                )
            }
        }

        // 2. Bottom Auxiliary Row + Spacious Floating Navigation Dock
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
                horizontalArrangement = Arrangement.SpaceBetween,
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
                                Modifier
                                    .clickable(enabled = enabled, onClick = onMap)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
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
                Spacer(Modifier.width(10.dp))
                // Compass / Reset Globe button (compact circular glass control)
                CircularGlassButton(
                    icon = AppIconType.COMPASS,
                    description = "归位到地球视图",
                    enabled = enabled,
                    interactionSource = earthBtnInteraction,
                    onClick = onNational
                )
            }

            // Bottom Primary Dock: 2 clean icon-over-label tabs + 1 prominent primary CTA pill
            FrostedPanel(Modifier.fillMaxWidth(), dockShape, elevation = 14.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockTabButton(
                        icon = AppIconType.HISTORY,
                        label = "忆往昔",
                        enabled = enabled,
                        interactionSource = memBtnInteraction,
                        modifier = Modifier.weight(0.88f),
                        onClick = onMemories
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(colors.outlineVariant.copy(alpha = 0.35f))
                    )
                    DockTabButton(
                        icon = AppIconType.CROSSHAIR,
                        label = "地图选点",
                        enabled = enabled,
                        interactionSource = pickBtnInteraction,
                        modifier = Modifier.weight(0.96f),
                        onClick = onPick
                    )
                    Button(
                        onClick = onImport,
                        enabled = enabled,
                        interactionSource = importBtnInteraction,
                        modifier = Modifier
                            .weight(1.30f)
                            .height(50.dp)
                            .liquidPress(importBtnInteraction, 0.95f),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        AppIcon(AppIconType.CAMERA_PLUS, tint = colors.onPrimary, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = if (pendingCount > 0) "待补填 $pendingCount" else "导入照片",
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "importBtnLabel"
                        ) { label ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularGlassButton(
    icon: AppIconType,
    description: String,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    FrostedPanel(
        modifier = Modifier
            .size(46.dp)
            .liquidPress(interactionSource, 0.92f),
        shape = CircleShape,
        elevation = 8.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(icon, tint = colors.onSurface, size = 19.dp)
        }
    }
}

@Composable
private fun DockTabButton(
    icon: AppIconType,
    label: String,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .height(50.dp)
            .liquidPress(interactionSource, 0.94f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(icon, tint = colors.primary, size = 19.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}
