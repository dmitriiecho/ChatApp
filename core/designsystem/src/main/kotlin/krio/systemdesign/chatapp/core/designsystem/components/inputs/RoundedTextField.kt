package krio.systemdesign.chatapp.core.designsystem.components.inputs

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme

// A filled text field with fully rounded ends and no underline: for a field that sits in a bar of its own, like a
// message input, where a frame or a line would only add noise.
@Composable
fun RoundedTextField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
) {
    val noLine = Color.Transparent
    TextField(
        state = state,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        lineLimits = lineLimits,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
            focusedIndicatorColor = noLine,
            unfocusedIndicatorColor = noLine,
            disabledIndicatorColor = noLine,
        ),
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoundedTextFieldPreview() {
    ChatAppTheme {
        Surface {
            RoundedTextField(
                state = remember { TextFieldState() },
                placeholder = "Message",
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
