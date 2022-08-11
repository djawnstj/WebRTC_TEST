package com.webrtc.app

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

object AppData {

    var isDebugging = true

    fun debug(tag: String?, msg: String?) { if (isDebugging) Log.d(tag, msg ?: "debug msg is null.") }
    fun error(tag: String?, msg: String?) { if (isDebugging) Log.e(tag, msg ?: "error msg is null.") }
    fun error(tag: String?, msg: String?, e:Exception) { if (isDebugging) Log.e(tag, msg ?: "error msg is null.", e) }

    fun Context?.showToast(msg: String?) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()


    @JvmField val dateFormat4 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREAN)

    //===== 레디스 설정 START =====//

    var redisHost:String = "211.45.4.23"

    var redisPort:Int = 40019

    var redisChannel:String = "EJS TEST"

    var redisUri: RedisURI? = null

    var redisClient: RedisClient? = null

    // AtomicLong object for request id creation
    val counter = AtomicLong(System.currentTimeMillis())

    var startInviteFlag = false

    var callStatus = "none"

    var sessionId = ""

    //===== 레디스 설정 END =====//

}