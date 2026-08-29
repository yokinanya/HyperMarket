package com.hyper.market

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.view.Window
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ConfigureSystemBars(isAboutPage: Boolean) {
    val view = LocalView.current
    val surface = MiuixTheme.colorScheme.surface
    val background = MiuixTheme.colorScheme.background
    val barColor = if (isAboutPage) Color.Transparent else surface
    val lightSystemBars = if (isAboutPage) {
        background.luminance() > 0.5f
    } else {
        surface.luminance() > 0.5f
    }
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        if (Build.VERSION.SDK_INT < 35) {
            setLegacyBarColors(window, barColor, barColor)
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = !isAboutPage
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = lightSystemBars
        controller.isAppearanceLightNavigationBars = lightSystemBars
    }
}

@Suppress("DEPRECATION")
private fun setLegacyBarColors(window: Window, status: Color, navigation: Color) {
    window.statusBarColor = status.toArgb()
    window.navigationBarColor = navigation.toArgb()
}

@Composable
internal fun LaunchNotice(show: Boolean, onDismiss: () -> Unit) {
    WindowDialog(
        show = show,
        title = "关于本软件的说明",
        summary = LaunchDialogHelper.message,
    ) {
        TextButton(text = "知道了", onClick = onDismiss)
    }
}
