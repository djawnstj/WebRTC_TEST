package com.webrtc.app

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object AppData {

    var isDebugging = true

    fun debug(tag: String?, msg: String?) { if (isDebugging) Log.d(tag, msg ?: "debug msg is null.") }
    fun error(tag: String?, msg: String?) { if (isDebugging) Log.e(tag, msg ?: "error msg is null.") }
    fun error(tag: String?, msg: String?, e:Exception) { if (isDebugging) Log.e(tag, msg ?: "error msg is null.", e) }

    fun Context?.showToast(msg: String?) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

}