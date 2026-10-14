package krio.systemdesign.chatapp.core.composeutils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

// Runs a ViewModel test. viewModelScope runs on Dispatchers.Main, which a JVM test doesn't have: it is replaced by
// a test dispatcher that shares runTest's virtual clock and runs launched coroutines at once.
@OptIn(ExperimentalCoroutinesApi::class)
fun viewModelTest(block: suspend TestScope.() -> Unit): TestResult {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    return try {
        runTest { block() }
    } finally {
        Dispatchers.resetMain()
    }
}

// Keeps a ViewModel's state collected, as its screen does: a Store observes its data only while its state is
// collected, so with no collector the state keeps its initial value. The collector starts at once, so the test
// can read the state's value right after an intent.
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepCollecting(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}
