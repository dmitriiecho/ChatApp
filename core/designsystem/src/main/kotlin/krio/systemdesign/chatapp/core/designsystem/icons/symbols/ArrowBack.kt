package krio.systemdesign.chatapp.core.designsystem.icons.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons

val AppIcons.ArrowBack: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(313f, 520f)
            lineToRelative(224f, 224f)
            lineToRelative(-57f, 56f)
            lineToRelative(-320f, -320f)
            lineToRelative(320f, -320f)
            lineToRelative(57f, 56f)
            lineToRelative(-224f, 224f)
            horizontalLineToRelative(487f)
            verticalLineToRelative(80f)
            horizontalLineTo(313f)
            close()
        }
    }.build()
}
