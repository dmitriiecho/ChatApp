package krio.systemdesign.chatapp.core.composeutils.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable

// A TextFieldState for a ViewModel, kept in SavedStateHandle so the typed text survives process death.
@OptIn(SavedStateHandleSaveableApi::class)
fun SavedStateHandle.savedTextField(key: String): TextFieldState =
    saveable(key, saver = TextFieldState.Saver) { TextFieldState() }
