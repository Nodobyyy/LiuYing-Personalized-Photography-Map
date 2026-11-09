package cn.photomap.ui

import android.view.WindowManager
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable fun FullScreenDialog(onDismiss: ()->Unit, dark: Boolean=MaterialTheme.colorScheme.background.luminance()<0.5f, content: @Composable ()->Unit) {
    val background=if(dark) Color.Black else MaterialTheme.colorScheme.background
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        DisposableEffect(view,dark,background) {
            val window=(view.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it,false)
                it.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                if(Build.VERSION.SDK_INT>=30) it.attributes=it.attributes.apply {setFitInsetsTypes(0)}
                if(Build.VERSION.SDK_INT>=29) it.isNavigationBarContrastEnforced=false
                it.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
                it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background.toArgb()))
                @Suppress("DEPRECATION")
                if(Build.VERSION.SDK_INT<35) {
                    it.statusBarColor=background.toArgb()
                    it.navigationBarColor=background.toArgb()
                }
                WindowCompat.getInsetsController(it,view).apply {isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}
                view.requestApplyInsets()
            }
            onDispose { }
        }
        content()
    }
}

