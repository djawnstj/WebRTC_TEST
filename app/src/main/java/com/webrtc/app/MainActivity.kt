package com.webrtc.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.webrtc.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "MainActivity" }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction().replace(binding.videoContainer.id, VideoFragment()).commit()
    }
}