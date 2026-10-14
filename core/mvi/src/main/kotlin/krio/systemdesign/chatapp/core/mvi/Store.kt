package krio.systemdesign.chatapp.core.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

// A screen's MVI loop: an Intent goes to the Executor, its Mutations go through the Reducer into the State,
// its Effects go to the screen. accept is the only way in, and the State changes only in the Reducer.
// The scope must run on the main thread (viewModelScope does): an Executor's checks of the State, like
// "already sending", then hold until its next suspension.
class Store<Intent, State, Mutation, Effect>(
    initialState: State,
    private val reducer: Reducer<State, Mutation>,
    private val executor: Executor<Intent, State, Mutation, Effect>,
    private val scope: CoroutineScope,
    private val observers: List<StoreObserver<Intent, State, Mutation, Effect>> = emptyList(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    // A Channel, not a SharedFlow: an effect sent while the screen isn't collecting (rotation, background) waits
    // in the buffer instead of being dropped.
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private val access = object : StoreAccess<State, Mutation, Effect> {

        override val state: State
            get() = _state.value

        override fun dispatch(mutation: Mutation) {
            // updateAndGet calls the Reducer again if another Mutation got in between: safe, as it is pure.
            val newState = _state.updateAndGet { state -> reducer.reduce(state, mutation) }
            observers.forEach { it.onMutation(mutation, newState) }
        }

        override suspend fun publish(effect: Effect) {
            _effects.send(effect)
            observers.forEach { it.onEffect(effect) }
        }
    }

    // After access: properties are initialized top to bottom.
    init {
        scope.launch { executor.onStart(access) }
        scope.launch {
            // The same rule stateIn(WhileSubscribed(5_000)) follows: a rotation resubscribes within the timeout.
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
                .command(_state.subscriptionCount)
                .collectLatest { command ->
                    if (command == SharingCommand.START) executor.whileSubscribed(access)
                }
        }
    }

    fun accept(intent: Intent) {
        observers.forEach { it.onIntent(intent, _state.value) }
        // Each Intent gets its own coroutine, so a slow one doesn't hold the next.
        scope.launch { executor.execute(intent, access) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
