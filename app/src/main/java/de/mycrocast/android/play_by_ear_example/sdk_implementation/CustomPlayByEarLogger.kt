package de.mycrocast.android.play_by_ear_example.sdk_implementation

import android.util.Log
import de.mycrocast.android.play_by_ear.sdk.logger.PlayByEarLogger

class CustomPlayByEarLogger : PlayByEarLogger {
    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warning(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message + ": ${throwable.message}")
    }
}