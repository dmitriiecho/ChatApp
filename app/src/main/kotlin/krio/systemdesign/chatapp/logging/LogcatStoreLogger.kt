package krio.systemdesign.chatapp.logging

import android.util.Log
import krio.systemdesign.chatapp.core.mvi.StoreLogger

// adb logcat -s Store
internal object LogcatStoreLogger : StoreLogger {
    override fun log(
        store: String,
        text: String,
    ) {
        Log.d("Store", "$store: $text")
    }
}
