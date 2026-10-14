package krio.systemdesign.chatapp.core.mvi

internal class LoggingStoreObserver(
    private val name: String,
    private val logger: StoreLogger,
) : StoreObserver<Any?, Any?, Any?, Any?> {

    override fun onIntent(
        intent: Any?,
        state: Any?,
    ) {
        logger.log(name, "intent $intent")
    }

    override fun onMutation(
        mutation: Any?,
        state: Any?,
    ) {
        logger.log(name, "mutation $mutation -> state $state")
    }

    override fun onEffect(effect: Any?) {
        logger.log(name, "effect $effect")
    }
}
