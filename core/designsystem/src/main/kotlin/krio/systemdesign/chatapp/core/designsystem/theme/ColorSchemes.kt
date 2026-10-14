package krio.systemdesign.chatapp.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The app's own palette, not one from the wallpaper (dynamic color on Android 12+): the app looks the same
// on every device. A warm one: cream surfaces and a coral accent. Every role the app uses is set, so none falls
// back to Material's purple baseline.
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC4453F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF3B0A06),
    secondary = Color(0xFF775651),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF5D3F3B),
    tertiary = Color(0xFF705C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCDFA6),
    onTertiaryContainer = Color(0xFF574419),
    // Also designsystem_window_background in values/colors.xml.
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF6E5E55),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BB),
    // The brightest surface: the message field, white on the input bar.
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEDE0D5),
    // Tones of the same cream for surfaces apart from the background: the top and bottom bars (Container),
    // others' messages (Highest).
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF3EC),
    surfaceContainer = Color(0xFFF6EDE5),
    surfaceContainerHigh = Color(0xFFF0E5DC),
    surfaceContainerHighest = Color(0xFFEADFD6),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF5C1612),
    primaryContainer = Color(0xFF6E2C25),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFE7BDB6),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFFDFC38C),
    onTertiary = Color(0xFF3E2E04),
    tertiaryContainer = Color(0xFF574419),
    onTertiaryContainer = Color(0xFFFCDFA6),
    // Also designsystem_window_background in values-night/colors.xml.
    background = Color(0xFF1A1614),
    onBackground = Color(0xFFEEE0DA),
    surface = Color(0xFF1A1614),
    onSurface = Color(0xFFEEE0DA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFB5A49B),
    outline = Color(0xFFA08D86),
    outlineVariant = Color(0xFF53433F),
    surfaceBright = Color(0xFF352D2A),
    surfaceDim = Color(0xFF161210),
    surfaceContainerLowest = Color(0xFF130F0D),
    surfaceContainerLow = Color(0xFF1F1A18),
    surfaceContainer = Color(0xFF251F1C),
    surfaceContainerHigh = Color(0xFF2E2724),
    surfaceContainerHighest = Color(0xFF352D2A),
)
