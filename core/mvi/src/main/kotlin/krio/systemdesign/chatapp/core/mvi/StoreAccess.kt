package krio.systemdesign.chatapp.core.mvi

// What an Executor may do with its Store, handed to it in every call. It can't set the State: a change goes
// through dispatch and the Reducer.
interface StoreAccess<State, Mutation, Effect> {

    // The current State, e.g. to ignore a second tap while the first one is handled.
    val state: State

    fun dispatch(mutation: Mutation)

    // A one-off event for the screen: navigation, a snackbar.
    suspend fun publish(effect: Effect)
}
