package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable fun FullScreenDialog(onDismiss: ()->Unit, dark: Boolean=MaterialTheme.colorScheme.background.luminance()<0.5f,
    compactTop: Boolean = false, content: @Composable ()->Unit) {
    val background=if(dark) Color.Black else MaterialTheme.colorScheme.background
    val hostInsets = WindowInsets.safeContent
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        DisposableEffect(view,dark,background) {
            val window=(view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                WindowCompat.getInsetsController(it,view).apply {isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}
            }
            onDispose { }
        }
        // Some edge-to-edge dialog windows report no bottom inset even when the
        // host still draws gesture navigation over the dialog.
        val safeInsets = WindowInsets.safeContent.union(hostInsets).union(WindowInsets(bottom = 24.dp))
        var screenTop by remember { mutableIntStateOf(0) }
        val insets = if (compactTop) {
            val top = (safeInsets.getTop(LocalDensity.current) - screenTop).coerceAtLeast(0)
            safeInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).union(WindowInsets(top = top))
        } else safeInsets
        Box(Modifier.fillMaxSize().background(background)
            .onGloballyPositioned { if (compactTop) screenTop = it.positionOnScreen().y.toInt().coerceAtLeast(0) }
            .windowInsetsPadding(insets)) { content() }
    }
}

