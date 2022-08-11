package com.webrtc.app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore.Video
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.webrtc.app.databinding.ActivityMainBinding
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "MainActivity" }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val permissions = arrayOf<String>(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    //========= 레디스 START =========//

    // 레디스 데이터 수신을 위한 리시버와 필터
    lateinit var commandReceiver1: BroadcastReceiver
    lateinit var commandFilter1: IntentFilter

    // 연결 상태를 감시하기 위한 리시버 액션
    lateinit var commandReceiver2: BroadcastReceiver
    lateinit var commandFilter2: IntentFilter

    // 로그를 받기 위한 리시버 액션
    lateinit var commandReceiver3: BroadcastReceiver
    lateinit var commandFilter3: IntentFilter


    var handler = Handler(Looper.getMainLooper())

    //========= 레디스 END =========//

    val videoFragment by lazy { VideoFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initActivity()

    }

    private fun initActivity() {

        checkPermissions(permissions)

        supportFragmentManager.beginTransaction().replace(binding.videoContainer.id, videoFragment).commit()

        initReceiver()

    }
    /**
     * 레디스 리시버 객체 생성
     */
    fun initReceiver() {

        // 레디스 데이터 수신
        commandFilter1 = IntentFilter()
        commandFilter1.addAction(RedisService.ACTION_REDIS_DATA)
        commandReceiver1 = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                AppData.debug(TAG, "onReceive for ACTION_REDIS_DATA called.")

                val now = AppData.dateFormat4.format(Date())

                val command = intent.getStringExtra("command")
                val channel = intent.getStringExtra("channel")
                val data = intent.getStringExtra("data")


                // 프래그먼트로 전송
                videoFragment.apply {
                    this.onRedisData(command, channel, data)
                }

            }
        }


        // 연결 상태 감시 데이터 수신
        commandFilter2 = IntentFilter()
        commandFilter2.addAction(RedisService.ACTION_CONNECTION_STATE)
        commandReceiver2 = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                AppData.debug(TAG, "onReceive for ACTION_CONNECTION_STATE called.")

                val command = intent.getStringExtra("command")
                val channel = intent.getStringExtra("channel")
                val data = intent.getStringExtra("data")
                data?.apply {
                    //AppData.showToast(applicationContext, "서버 연결됨")

                    // 프래그먼트로 전송
                    videoFragment.apply {
                        this.onRedisConnection(command, channel, data)
                    }
                }
            }
        }


        // 로그 데이터 수신
        commandFilter3 = IntentFilter()
        commandFilter3.addAction(RedisService.ACTION_LOG)
        commandReceiver3 = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                AppData.debug(TAG, "onReceive for ACTION_LOG called.")

                val command = intent.getStringExtra("command")
                val channel = intent.getStringExtra("channel")
                val data = intent.getStringExtra("data")
                data?.apply {

                    // 프래그먼트로 전송
                    videoFragment.apply {
                        this.onRedisLog(command, channel, data)
                    }
                }
            }
        }

    }

    private fun checkPermissions(permissions: Array<String>) {

        val targetList = ArrayList<String>()

        permissions.forEach {
            val permissionCheck = ContextCompat.checkSelfPermission(this, it)

            if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                AppData.debug(TAG, "권한없음 : $it")
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, it)) {
                    targetList.add(it)
                }
            }
        }

        val targets = arrayOfNulls<String>(targetList.size)
        targetList.toArray(targets)

        if (targets.isEmpty().not()) {
            // 위험 권한 부여 요청
            ActivityCompat.requestPermissions(this, targets, 101)
        }

    }

}