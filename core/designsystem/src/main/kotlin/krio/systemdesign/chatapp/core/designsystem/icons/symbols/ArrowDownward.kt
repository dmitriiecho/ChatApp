package krio.systemdesign.chatapp.core.designsystem.icons.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons

val AppIcons.ArrowDownward: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowDownward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(440f, 160f)
            verticalLineToRelative(487f)
            lineTo(216f, 423f)
            lineToRelative(-56f, 57f)
            lineToRelative(320f, 320f)
            lineToRelative(320f, -320f)
            lineToRelative(-56f, -57f)
            lineToRelative(-224f, 224f)
            verticalLineToRelative(-487f)
            horizontalLineToRelative(-80f)
            close()
        }
    }.build()
}
