package cn.photomap.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cn.photomap.BuildConfig

val LocalMapBackdrop = compositionLocalOf<ImageBitmap?> { null }

@Composable
fun EditorPanel(content: @Composable () -> Unit) {
    if (BuildConfig.ONLINE_MAPS) {
        FrostedPanel(Modifier.fillMaxSize(), RoundedCornerShape(0.dp)) { content() }
    } else {
        Surface(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun DetailMenu(label: String, enabled: Boolean, options: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val close = { expanded = false }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text(label) }
        DropdownMenu(
            expanded, onDismissRequest = close,
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
            shadowElevation = 5.dp
        ) {
            options(close)
        }
    }
}

@Composable
fun FrostedPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(shape).border(1.dp, colors.outlineVariant.copy(alpha = .6f), shape)) {
        if (Build.VERSION.SDK_INT >= 31) LocalMapBackdrop.current?.let { image ->
            Image(image, null, Modifier.matchParentSize().blur(24.dp), Alignment.BottomCenter, ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            colors.surface.copy(alpha = .92f), colors.surfaceVariant.copy(alpha = .88f)
        ))))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPanelSheet(
    onDismissRequest: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    if (!BuildConfig.ONLINE_MAPS) {
        ModalBottomSheet(onDismissRequest, sheetState = state, content = content)
        return
    }
    ModalBottomSheet(
        onDismissRequest, sheetState = state, containerColor = Color.Transparent, dragHandle = null
    ) {
        FrostedPanel(Modifier.fillMaxWidth(), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                content()
            }
        }
    }
}
