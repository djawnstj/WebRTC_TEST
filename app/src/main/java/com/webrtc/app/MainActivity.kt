package com.webrtc.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.webrtc.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "MainActivity" }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val permissions = arrayOf<String>(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        checkPermissions(permissions)

        initActivity()

    }

    private fun initActivity() {

        supportFragmentManager.beginTransaction().replace(binding.videoContainer.id, VideoFragment()).commit()

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