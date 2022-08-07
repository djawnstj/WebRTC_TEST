package com.webrtc.app

import android.util.Log

object AppData {

    var isDebugging = true

    fun debug(tag: String?, msg: String?) { if (isDebugging) Log.d(tag, msg ?: "debug msg is null.") }
    fun error(tag: String?, msg: String?) { if (isDebugging) Log.e(tag, msg ?: "error msg is null.") }
    fun error(tag: String?, msg: String?, e:Exception) { if (isDebugging) Log.e(tag, msg ?: "error msg is null.", e) }

}