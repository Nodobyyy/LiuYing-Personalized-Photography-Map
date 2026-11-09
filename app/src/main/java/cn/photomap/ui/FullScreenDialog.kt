package cn.photomap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable fun FullScreenDialog(onDismiss: ()->Unit, dark: Boolean=MaterialTheme.colorScheme.background.luminance()<0.5f, content: @Composable ()->Unit) {
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
        Box(Modifier.fillMaxSize().background(background).windowInsetsPadding(safeInsets)) { content() }
    }
}

