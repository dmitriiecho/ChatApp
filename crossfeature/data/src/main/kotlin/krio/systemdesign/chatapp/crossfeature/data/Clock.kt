package krio.systemdesign.chatapp.crossfeature.data

import org.koin.core.annotation.Singleton

// The time, swapped for a fixed one in tests.
internal fun interface Clock {
    fun nowMillis(): Long
}

@Singleton
internal class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
