package com.webrtc.app

import android.media.projection.MediaProjection
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.webrtc.app.databinding.FragmentVideoBinding
import org.webrtc.*

class VideoFragment: Fragment() {

    companion object { private const val TAG = "VideoFragment" }

    private val binding by lazy { FragmentVideoBinding.inflate(layoutInflater) }

    //========  webRTC 변수 START ========//
    private val peerConnectionFactory by lazy { PeerConnectionFactory.builder().createPeerConnectionFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        initPeerConnection()

        return binding.root
    }

    private fun initPeerConnection() {

        val videoCapture = createVideoCaptuer()

    }

    /**
     * VideoCapturer 객체 생성
     */
    private fun createVideoCaptuer(): VideoCapturer? {
        var capturer: VideoCapturer? = null
        capturer =
            if (Camera2Enumerator.isSupported(activity)) {
                createCameraCapturer(Camera2Enumerator(activity))
            } else {
                createCameraCapturer(Camera1Enumerator(false))
            }

        return capturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // 전면 카메라가 있는지 확인
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val capturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    return capturer
                }
            }
        }

}