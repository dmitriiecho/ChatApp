package krio.systemdesign.chatapp.core.mvi

import kotlinx.coroutines.CoroutineScope

// Makes every Store of the app, so what all of them share is set in one place: with a logger, each Store logs
// its Intents, Mutations and Effects under its name.
class StoreFactory(private val logger: StoreLogger? = null) {

    fun <Intent, State, Mutation, Effect> create(
        name: String,
        initialState: State,
        reducer: Reducer<State, Mutation>,
        executor: Executor<Intent, State, Mutation, Effect>,
        scope: CoroutineScope,
        observers: List<StoreObserver<Intent, State, Mutation, Effect>> = emptyList(),
    ): Store<Intent, State, Mutation, Effect> = Store(
        initialState = initialState,
        reducer = reducer,
        executor = executor,
        scope = scope,
        observers = observers + listOfNotNull(logger?.let { LoggingStoreObserver(name, it) }),
    )
}
