package krio.systemdesign.chatapp.core.composeutils.effects

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Handles a ViewModel's one-off effects (navigation, snackbars) while the screen is visible.
// An effect sent while the app is in the background waits in the ViewModel's channel
// and is handled when the user comes back.
@Composable
fun <T> ObserveEffects(
    effects: Flow<T>,
    onEffect: ObserveEffectsScope.(effect: T) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The handler changes when what it captures changes (e.g. a new callback): use the latest one without restarting.
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycle) {
        // Starts each time the screen becomes visible and is cancelled when it stops.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Main.immediate: an effect is handled right inside the ViewModel's send. With a pause in between,
            // a screen stopping at that moment would take the effect from the channel and lose it.
            withContext(Dispatchers.Main.immediate) {
                val scope = ObserveEffectsScope(scope = this, lifecycle = lifecycle)
                effects.collect { effect -> scope.currentOnEffect(effect) }
            }
        }
    }
}

// What a screen can do with an effect. Both run in coroutines that stop when the screen stops.
class ObserveEffectsScope internal constructor(
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle,
) {
    // Navigates only while the screen is on top (RESUMED). After a fast double tap the first navigation takes
    // the screen off the top, so the second one waits and is dropped when the screen stops. Navigation from
    // an effect that came while the app was in the background runs once the screen is on top again.
    fun navigate(block: () -> Unit) {
        scope.launch { lifecycle.withResumed(block) }
    }

    // SnackbarHostState.showSnackbar suspends until the snackbar is dismissed: run right here, it would hold
    // the next effect (e.g. navigation) for seconds. So it gets its own coroutine.
    fun showSnackbar(
        hostState: SnackbarHostState,
        message: String,
    ) {
        scope.launch { hostState.showSnackbar(message) }
    }
}
