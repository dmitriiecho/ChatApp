package krio.systemdesign.chatapp.core.composeutils

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot

// Types text into a field as the user would. On a device Compose applies the change to snapshotFlow readers on
// the next frame; a test has no frames, so the change is applied here.
fun TextFieldState.typeText(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}
