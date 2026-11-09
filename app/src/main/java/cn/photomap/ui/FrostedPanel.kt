package cn.photomap.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.photomap.BuildConfig

val LocalMapBackdrop = compositionLocalOf<ImageBitmap?> { null }

/**
 * Lightweight, GPU-accelerated spring scale bounce on press.
 * Uses graphicsLayer so no recomposition or relayout occurs during animation.
 */
@Composable
fun Modifier.liquidPress(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    pressedScale: Float = 0.96f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun EditorPanel(content: @Composable () -> Unit) {
    if (BuildConfig.ONLINE_MAPS) {
        FrostedPanel(Modifier.fillMaxSize(), RoundedCornerShape(0.dp), elevation = 0.dp) { content() }
    } else {
        Surface(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun DetailMenu(label: String, enabled: Boolean, options: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val close = { expanded = false }
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier.liquidPress(interactionSource, 0.95f)
        ) { Text(label) }
        DropdownMenu(
            expanded, onDismissRequest = close,
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            options(close)
        }
    }
}

/**
 * Liquid Glass / Frosted Panel:
 * Combines hardware-accelerated RenderEffect blur (API 31+) with multi-stop refractive
 * specular highlights, translucent surface tinting, and a directional rim border.
 */
@Composable
fun FrostedPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 6.dp,
    tintColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val borderBrush = remember(isDark, colors.outlineVariant, colors.primary) {
        Brush.linearGradient(
            colors = if (isDark) listOf(
                Color.White.copy(alpha = 0.28f),
                colors.primary.copy(alpha = 0.16f),
                colors.outlineVariant.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.08f)
            ) else listOf(
                Color.White.copy(alpha = 0.88f),
                colors.primary.copy(alpha = 0.18f),
                colors.outlineVariant.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.55f)
            ),
            start = Offset(0f, 0f),
            end = Offset(700f, 700f)
        )
    }
    val surfaceBrush = remember(isDark, colors.surface, colors.surfaceVariant, colors.primary, tintColor) {
        val baseTop = if (tintColor != Color.Unspecified) tintColor else colors.surface.copy(alpha = if (isDark) 0.80f else 0.85f)
        val baseBottom = colors.surfaceVariant.copy(alpha = if (isDark) 0.74f else 0.78f)
        Brush.verticalGradient(listOf(baseTop, baseBottom))
    }
    val specularBrush = remember(isDark, colors.primary) {
        Brush.linearGradient(
            colors = if (isDark) listOf(
                Color.White.copy(alpha = 0.10f),
                colors.primary.copy(alpha = 0.05f),
                Color.Transparent
            ) else listOf(
                Color.White.copy(alpha = 0.52f),
                colors.primary.copy(alpha = 0.06f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(360f, 520f)
        )
    }
    Box(
        modifier
            .then(
                if (elevation > 0.dp) Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.16f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.2f)
                ) else Modifier
            )
            .clip(shape)
            .border(1.dp, borderBrush, shape)
    ) {
        if (Build.VERSION.SDK_INT >= 31) LocalMapBackdrop.current?.let { image ->
            Image(
                image, null,
                Modifier.matchParentSize().blur(28.dp),
                Alignment.BottomCenter, ContentScale.Crop
            )
        }
        Box(Modifier.matchParentSize().background(surfaceBrush))
        Box(Modifier.matchParentSize().background(specularBrush))
        content()
    }
}

/**
 * Interactive Liquid Glass Card for list items, record cards, or option tiles.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val borderBrush = remember(isDark, selected, colors.primary, colors.outlineVariant) {
        if (selected) {
            Brush.linearGradient(
                listOf(
                    colors.primary.copy(alpha = 0.85f),
                    colors.primary.copy(alpha = 0.45f)
                )
            )
        } else {
            Brush.linearGradient(
                if (isDark) listOf(
                    Color.White.copy(alpha = 0.20f),
                    colors.outlineVariant.copy(alpha = 0.22f)
                ) else listOf(
                    Color.White.copy(alpha = 0.78f),
                    colors.outlineVariant.copy(alpha = 0.35f)
                )
            )
        }
    }
    val bgBrush = remember(isDark, selected, colors.primaryContainer, colors.surface, colors.surfaceVariant) {
        if (selected) {
            Brush.verticalGradient(
                listOf(
                    colors.primaryContainer.copy(alpha = if (isDark) 0.58f else 0.68f),
                    colors.surface.copy(alpha = if (isDark) 0.72f else 0.82f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    colors.surface.copy(alpha = if (isDark) 0.65f else 0.74f),
                    colors.surfaceVariant.copy(alpha = if (isDark) 0.52f else 0.62f)
                )
            )
        }
    }
    val baseModifier = if (onClick != null) {
        modifier
            .liquidPress(interactionSource, 0.98f)
            .clip(shape)
            .background(bgBrush)
            .border(if (selected) 1.5.dp else 1.dp, borderBrush, shape)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
    } else {
        modifier
            .clip(shape)
            .background(bgBrush)
            .border(if (selected) 1.5.dp else 1.dp, borderBrush, shape)
    }
    Column(baseModifier, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPanelSheet(
    onDismissRequest: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    opaque: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    if (opaque) {
        ModalBottomSheet(onDismissRequest, sheetState = state, containerColor = MaterialTheme.colorScheme.surface, content = content)
        return
    }
    ModalBottomSheet(
        onDismissRequest,
        sheetState = state,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = null
    ) {
        FrostedPanel(
            Modifier.fillMaxWidth(),
            RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            elevation = 12.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 4.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                    )
                }
                content()
            }
        }
    }
}

