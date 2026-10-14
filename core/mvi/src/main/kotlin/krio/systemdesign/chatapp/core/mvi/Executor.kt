package krio.systemdesign.chatapp.core.mvi

// A screen's logic: calls use cases, decides what to do with an Intent, and reports what happened as Mutations
// and Effects. It gets the Store only for the length of a call, so it can't reach it at any other time.
// Not java.util.concurrent.Executor. It must not throw: an uncaught exception in viewModelScope crashes the app.
interface Executor<Intent, State, Mutation, Effect> {

    // Runs once, when the Store is made (once per ViewModel, not again after a rotation): one-off work of the
    // screen's start, such as opening a chat.
    suspend fun onStart(store: StoreAccess<State, Mutation, Effect>) {}

    // Runs while the screen collects the Store's state and is cancelled 5 s after it stops (rotation keeps it
    // running): the place to observe data, so nothing is observed while the app is in the background.
    suspend fun whileSubscribed(store: StoreAccess<State, Mutation, Effect>) {}

    suspend fun execute(
        intent: Intent,
        store: StoreAccess<State, Mutation, Effect>,
    )
}
