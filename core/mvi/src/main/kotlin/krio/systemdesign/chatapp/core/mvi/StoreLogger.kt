package krio.systemdesign.chatapp.core.mvi

// Where a Store's log lines go (Logcat in the app). A debugging aid: the lines hold whole States.
fun interface StoreLogger {
    fun log(
        store: String,
        text: String,
    )
}
