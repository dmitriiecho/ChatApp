package krio.systemdesign.chatapp.core.mvi

// The only place a screen's new State is made: a pure function of the old State and a Mutation. No coroutines,
// no I/O: the Store may call it again for the same Mutation when two of them race.
fun interface Reducer<State, Mutation> {
    fun reduce(
        state: State,
        mutation: Mutation,
    ): State
}
