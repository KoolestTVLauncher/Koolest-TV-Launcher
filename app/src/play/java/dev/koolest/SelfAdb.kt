package dev.koolest

import android.content.Context

/** The Play build has no adb shortcuts (see the sideload flavor's SelfAdb): Play rejects apps that switch off system apps. */
object SelfAdb {
    const val AVAILABLE = false

    fun run(c: Context, commands: List<String>, done: (Result<String>) -> Unit) =
        done(Result.failure(UnsupportedOperationException("sideload build only")))
}
