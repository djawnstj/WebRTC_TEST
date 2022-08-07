package com.webrtc.app

import android.os.Bundle
import android.util.DisplayMetrics
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
    //========  webRTC 변수 END ========//

    //========  비디오 속성 변수 START ========//
    private val videoCapturer by lazy { createVideoCapturer() }
    private val videoSource by lazy { videoCapturer?.let { peerConnectionFactory.createVideoSource(it.isScreencast) } }
    private val videoConstraints by lazy { MediaConstraints() }
    private val rootEglBase: EglBase by lazy { EglBase.create() }
    private val surfaceTextureHelper by lazy { SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext) }
    private lateinit var localVideoTrack: VideoTrack
    //========  비디오 속성 변수 END ========//

    //========  오디오 속성 변수 START ========//
    private val audioConstraints by lazy { MediaConstraints() }
    private val audioSource by lazy { peerConnectionFactory.createAudioSource(audioConstraints) }
    private val localAudioTrack by lazy { peerConnectionFactory.createAudioTrack("101", audioSource) }
    //========  오디오 속성 변수 END ========//


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        initLocalVideo()

        initLocalAudio()

        return binding.root
    }

    private fun initLocalVideo() {

        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            ?: AppData.debug(TAG, "initLocalVideo() : videoCapture is null")

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

    }

    private fun initLocalAudio() {

        localAudioTrack.setEnabled(true)

        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleNoiseSuppression", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("levelControl", "true"))

    }

    private fun startLocalMedia() {

        videoCapturer?.let {
            val displayMetrics = DisplayMetrics()
        }

    }

    /**
     * VideoCapturer 객체 생성
     */
    private fun createVideoCapturer(): VideoCapturer? {
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

        // 전면 카메라가 없으면 다른 카메라가 있는지 확인
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }

}