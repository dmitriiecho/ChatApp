package krio.systemdesign.chatapp.core.designsystem.theme

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat

// Makes the status and navigation bar icons light on a dark theme and dark on a light one. Android matches them
// to the system theme, but the app switches its theme itself, so the two can differ.
@Composable
fun SystemBarsAppearance(
    window: Window,
    darkTheme: Boolean,
) {
    DisposableEffect(window, darkTheme) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        onDispose {}
    }
}
