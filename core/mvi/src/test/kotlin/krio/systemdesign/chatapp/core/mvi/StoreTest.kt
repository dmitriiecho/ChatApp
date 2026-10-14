package krio.systemdesign.chatapp.core.mvi

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    private sealed interface CounterIntent {
        data class Add(val amount: Int) : CounterIntent

        data object Ping : CounterIntent
    }

    // Counts what it is asked to add and tracks whether the screen is observing.
    private class CounterExecutor : Executor<CounterIntent, Int, Int, String> {

        var isObserving = false
        var observeStarts = 0
        var starts = 0

        override suspend fun onStart(store: StoreAccess<Int, Int, String>) {
            starts++
        }

        override suspend fun whileSubscribed(store: StoreAccess<Int, Int, String>) {
            isObserving = true
            observeStarts++
            try {
                awaitCancellation()
            } finally {
                isObserving = false
            }
        }

        override suspend fun execute(
            intent: CounterIntent,
            store: StoreAccess<Int, Int, String>,
        ) {
            when (intent) {
                is CounterIntent.Add -> store.dispatch(intent.amount)
                CounterIntent.Ping -> store.publish("pong")
            }
        }
    }

    private val executor = CounterExecutor()

    // Unconfined: the Store's coroutines run at once, as on Main.immediate. Cancelled when the test ends.
    private fun TestScope.store(
        observers: List<StoreObserver<CounterIntent, Int, Int, String>> = emptyList(),
        factory: StoreFactory? = null,
    ): Store<CounterIntent, Int, Int, String> {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val reducer = Reducer<Int, Int> { state, mutation -> state + mutation }
        return factory?.create("Counter", 0, reducer, executor, scope, observers)
            ?: Store(0, reducer, executor, scope, observers)
    }

    private fun TestScope.subscribe(store: Store<*, *, *, *>) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.state.collect {} }

    @Test
    fun `the start runs once, with or without subscribers`() = runTest {
        val store = store()
        subscribe(store).cancel()
        subscribe(store)

        assertThat(executor.starts).isEqualTo(1)
        assertThat(store.state.value).isEqualTo(0)
    }

    @Test
    fun `mutations go through the reducer`() = runTest {
        val store = store()

        store.accept(CounterIntent.Add(2))
        store.accept(CounterIntent.Add(3))

        assertThat(store.state.value).isEqualTo(5)
    }

    @Test
    fun `an effect reaches the screen`() = runTest {
        val store = store()

        store.effects.test {
            store.accept(CounterIntent.Ping)

            assertThat(awaitItem()).isEqualTo("pong")
        }
    }

    @Test
    fun `an effect sent while the screen isn't collecting waits for it`() = runTest {
        val store = store()

        store.accept(CounterIntent.Ping)

        store.effects.test {
            assertThat(awaitItem()).isEqualTo("pong")
        }
    }

    @Test
    fun `observing starts with the first subscriber and stops 5 s after the last`() = runTest {
        val store = store()
        assertThat(executor.isObserving).isFalse()

        val subscriber = subscribe(store)
        assertThat(executor.isObserving).isTrue()

        subscriber.cancel()
        advanceTimeBy(4_999)
        runCurrent()
        assertThat(executor.isObserving).isTrue()

        advanceTimeBy(2)
        runCurrent()
        assertThat(executor.isObserving).isFalse()
    }

    @Test
    fun `a subscriber back within 5 s, as after a rotation, keeps observing`() = runTest {
        val store = store()
        subscribe(store).cancel()
        advanceTimeBy(3_000)

        subscribe(store)
        advanceTimeBy(10_000)
        runCurrent()

        assertThat(executor.isObserving).isTrue()
        assertThat(executor.observeStarts).isEqualTo(1)
    }

    @Test
    fun `an observer sees the intent with the state before it, the new state and the effect`() = runTest {
        val seen = mutableListOf<String>()
        val observer = object : StoreObserver<CounterIntent, Int, Int, String> {
            override fun onIntent(
                intent: CounterIntent,
                state: Int,
            ) {
                seen += "intent $intent at $state"
            }

            override fun onMutation(
                mutation: Int,
                state: Int,
            ) {
                seen += "mutation $mutation -> $state"
            }

            override fun onEffect(effect: String) {
                seen += "effect $effect"
            }
        }
        val store = store(observers = listOf(observer))

        store.accept(CounterIntent.Add(2))
        store.accept(CounterIntent.Ping)

        assertThat(seen).isEqualTo(
            listOf("intent Add(amount=2) at 0", "mutation 2 -> 2", "intent Ping at 2", "effect pong"),
        )
    }

    @Test
    fun `a factory with a logger logs under the store's name`() = runTest {
        val lines = mutableListOf<String>()
        val store = store(factory = StoreFactory { store, text -> lines += "$store: $text" })

        store.accept(CounterIntent.Add(2))
        store.accept(CounterIntent.Ping)

        assertThat(lines).isEqualTo(
            listOf(
                "Counter: intent Add(amount=2)",
                "Counter: mutation 2 -> state 2",
                "Counter: intent Ping",
                "Counter: effect pong",
            ),
        )
    }
}
