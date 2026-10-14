package krio.systemdesign.chatapp.core.designsystem.icons.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons

val AppIcons.ChatBubble: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChatBubble",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(80f, 880f)
            verticalLineToRelative(-720f)
            quadToRelative(0f, -33f, 23.5f, -56.5f)
            reflectiveQuadTo(160f, 80f)
            horizontalLineToRelative(640f)
            quadToRelative(33f, 0f, 56.5f, 23.5f)
            reflectiveQuadTo(880f, 160f)
            verticalLineToRelative(480f)
            quadToRelative(0f, 33f, -23.5f, 56.5f)
            reflectiveQuadTo(800f, 720f)
            horizontalLineTo(240f)
            lineTo(80f, 880f)
            close()
            moveToRelative(126f, -240f)
            horizontalLineToRelative(594f)
            verticalLineToRelative(-480f)
            horizontalLineTo(160f)
            verticalLineToRelative(525f)
            lineToRelative(46f, -45f)
            close()
            moveToRelative(-46f, 0f)
            verticalLineToRelative(-480f)
            verticalLineToRelative(480f)
            close()
        }
    }.build()
}
