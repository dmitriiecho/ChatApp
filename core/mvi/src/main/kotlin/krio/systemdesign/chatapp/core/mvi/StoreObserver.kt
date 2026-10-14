package krio.systemdesign.chatapp.core.mvi

// Sees everything that passes through a Store (for logs, analytics) and changes nothing. Called synchronously
// inside the Store, so it must be quick and must not throw.
interface StoreObserver<in Intent, in State, in Mutation, in Effect> {

    // state: the State the user acted on, before the Executor handles the Intent.
    fun onIntent(
        intent: Intent,
        state: State,
    ) {}

    // state: the new State the Reducer made from this Mutation.
    fun onMutation(
        mutation: Mutation,
        state: State,
    ) {}

    fun onEffect(effect: Effect) {}
}
