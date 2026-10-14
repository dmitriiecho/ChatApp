package krio.systemdesign.chatapp.core.designsystem.icons.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons

val AppIcons.Logout: ImageVector by lazy {
    ImageVector.Builder(
        name = "Logout",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(200f, 840f)
            quadToRelative(-33f, 0f, -56.5f, -23.5f)
            reflectiveQuadTo(120f, 760f)
            verticalLineToRelative(-560f)
            quadToRelative(0f, -33f, 23.5f, -56.5f)
            reflectiveQuadTo(200f, 120f)
            horizontalLineToRelative(280f)
            verticalLineToRelative(80f)
            horizontalLineTo(200f)
            verticalLineToRelative(560f)
            horizontalLineToRelative(280f)
            verticalLineToRelative(80f)
            horizontalLineTo(200f)
            close()
            moveToRelative(440f, -160f)
            lineToRelative(-55f, -58f)
            lineToRelative(102f, -102f)
            horizontalLineTo(360f)
            verticalLineToRelative(-80f)
            horizontalLineToRelative(327f)
            lineTo(585f, 338f)
            lineToRelative(55f, -58f)
            lineToRelative(200f, 200f)
            lineToRelative(-200f, 200f)
            close()
        }
    }.build()
}
