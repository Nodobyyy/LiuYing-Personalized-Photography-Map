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
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier.heightIn(min = 38.dp).liquidPress(interactionSource, 0.95f),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
        DropdownMenu(
            expanded, onDismissRequest = close,
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
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
 * specular highlights, high-legibility translucent surface tinting, and a directional rim border.
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
                Color.White.copy(alpha = 0.26f),
                colors.primary.copy(alpha = 0.16f),
                colors.outlineVariant.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.08f)
            ) else listOf(
                Color.White.copy(alpha = 0.92f),
                colors.primary.copy(alpha = 0.18f),
                colors.outlineVariant.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.65f)
            ),
            start = Offset(0f, 0f),
            end = Offset(700f, 700f)
        )
    }
    val surfaceBrush = remember(isDark, colors.surface, colors.surfaceVariant, tintColor) {
        val baseTop = if (tintColor != Color.Unspecified) tintColor else colors.surface.copy(alpha = if (isDark) 0.86f else 0.90f)
        val baseBottom = if (tintColor != Color.Unspecified) tintColor.copy(alpha = (tintColor.alpha * 0.92f).coerceIn(0f, 1f))
            else colors.surfaceVariant.copy(alpha = if (isDark) 0.82f else 0.86f)
        Brush.verticalGradient(listOf(baseTop, baseBottom))
    }
    val specularBrush = remember(isDark, colors.primary) {
        Brush.linearGradient(
            colors = if (isDark) listOf(
                Color.White.copy(alpha = 0.09f),
                colors.primary.copy(alpha = 0.04f),
                Color.Transparent
            ) else listOf(
                Color.White.copy(alpha = 0.55f),
                colors.primary.copy(alpha = 0.05f),
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
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.50f else 0.22f)
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
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            content()
        }
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
                    colors.primary.copy(alpha = 0.90f),
                    colors.primary.copy(alpha = 0.50f)
                )
            )
        } else {
            Brush.linearGradient(
                if (isDark) listOf(
                    Color.White.copy(alpha = 0.20f),
                    colors.outlineVariant.copy(alpha = 0.25f)
                ) else listOf(
                    Color.White.copy(alpha = 0.85f),
                    colors.outlineVariant.copy(alpha = 0.42f)
                )
            )
        }
    }
    val bgBrush = remember(isDark, selected, colors.primaryContainer, colors.surface, colors.surfaceVariant) {
        if (selected) {
            Brush.verticalGradient(
                listOf(
                    colors.primaryContainer.copy(alpha = if (isDark) 0.65f else 0.78f),
                    colors.surface.copy(alpha = if (isDark) 0.80f else 0.90f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    colors.surface.copy(alpha = if (isDark) 0.75f else 0.85f),
                    colors.surfaceVariant.copy(alpha = if (isDark) 0.62f else 0.72f)
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
    Column(baseModifier) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            content()
        }
    }
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
    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    if (opaque) {
        ModalBottomSheet(
            onDismissRequest,
            sheetState = state,
            shape = sheetShape,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.36f),
            content = content
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest,
        sheetState = state,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.34f),
        dragHandle = null
    ) {
        FrostedPanel(
            Modifier.fillMaxWidth(),
            sheetShape,
            elevation = 14.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 42.dp, height = 4.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f))
                    )
                }
                content()
            }
        }
    }
}
