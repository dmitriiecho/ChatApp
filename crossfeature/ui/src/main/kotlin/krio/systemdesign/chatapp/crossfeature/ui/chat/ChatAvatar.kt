package krio.systemdesign.chatapp.crossfeature.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.core.designsystem.theme.LocalDarkTheme

// A chat's picture until chats have photos: the first letters of its title on a colour picked by the title,
// so one name has one colour on every screen.
@Composable
fun ChatAvatar(
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = avatarColors[avatarColorIndex(title)]
    val isDark = LocalDarkTheme.current
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (isDark) colors.darkContainer else colors.lightContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials(title),
                color = if (isDark) colors.darkContent else colors.lightContent,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// String.hashCode is fixed by its spec, so the colour stays the same across runs and devices.
internal fun avatarColorIndex(title: String): Int = title.hashCode().mod(avatarColors.size)

private class AvatarColors(
    val lightContainer: Color,
    val lightContent: Color,
    val darkContainer: Color,
    val darkContent: Color,
)

// Material tonal pairs: tone 90 under tone 30 in light, tone 30 under tone 90 in dark.
private val avatarColors = listOf(
    AvatarColors(Color(0xFFFFDAD6), Color(0xFF8C1D18), Color(0xFF73332E), Color(0xFFFFDAD6)), // red
    AvatarColors(Color(0xFFFFDCC2), Color(0xFF6E3900), Color(0xFF6E3900), Color(0xFFFFDCC2)), // orange
    AvatarColors(Color(0xFFF6E388), Color(0xFF524600), Color(0xFF524600), Color(0xFFF6E388)), // yellow
    AvatarColors(Color(0xFFC6EFAD), Color(0xFF2B4F17), Color(0xFF2B4F17), Color(0xFFC6EFAD)), // green
    AvatarColors(Color(0xFFA6F2E4), Color(0xFF005047), Color(0xFF005047), Color(0xFFA6F2E4)), // teal
    AvatarColors(Color(0xFFD1E4FF), Color(0xFF0E4A80), Color(0xFF0E4A80), Color(0xFFD1E4FF)), // blue
    AvatarColors(Color(0xFFE0E0FF), Color(0xFF3A3F8F), Color(0xFF3A3F8F), Color(0xFFE0E0FF)), // indigo
    AvatarColors(Color(0xFFFFD8EC), Color(0xFF7A2860), Color(0xFF7A2860), Color(0xFFFFD8EC)), // pink
)

internal fun initials(title: String): String = title
    .split(' ')
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { it.first().uppercase() }

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatAvatarPreview() {
    ChatAppTheme {
        Surface {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatAvatar(title = "Anna")
                ChatAvatar(title = "Max")
                ChatAvatar(title = "Kate")
                ChatAvatar(title = "Project team")
                ChatAvatar(title = "Denis 2", size = 32.dp)
            }
        }
    }
}
