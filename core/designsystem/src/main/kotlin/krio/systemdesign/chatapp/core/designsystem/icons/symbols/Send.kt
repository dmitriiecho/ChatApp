package krio.systemdesign.chatapp.core.designsystem.icons.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons

val AppIcons.Send: ImageVector by lazy {
    ImageVector.Builder(
        name = "Send",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(120f, 800f)
            verticalLineToRelative(-640f)
            lineToRelative(760f, 320f)
            lineToRelative(-760f, 320f)
            close()
            moveToRelative(80f, -120f)
            lineToRelative(474f, -200f)
            lineToRelative(-474f, -200f)
            verticalLineToRelative(140f)
            lineToRelative(240f, 60f)
            lineToRelative(-240f, 60f)
            verticalLineToRelative(140f)
            close()
            moveToRelative(0f, 0f)
            verticalLineToRelative(-400f)
            verticalLineToRelative(400f)
            close()
        }
    }.build()
}
