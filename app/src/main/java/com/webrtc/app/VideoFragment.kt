package com.webrtc.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.webrtc.app.AppData.showToast
import com.webrtc.app.databinding.FragmentVideoBinding
import org.webrtc.*


class VideoFragment: Fragment() {

    companion object { private const val TAG = "VideoFragment" }

    private val binding by lazy { FragmentVideoBinding.inflate(layoutInflater) }

    //========  webRTC 변수 START ========//
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    //========  webRTC 변수 END ========//

    //========  비디오 변수 START ========//
    private val videoCapturer by lazy { createVideoCapturer() }
    private val videoSource by lazy { videoCapturer?.let { peerConnectionFactory.createVideoSource(it.isScreencast) } }
    private val videoConstraints by lazy { MediaConstraints() }
    private val rootEglBase: EglBase by lazy { EglBase.create() }
    private val surfaceTextureHelper by lazy { SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext) }
    private lateinit var localVideoTrack: VideoTrack
    private var videoFps = 30
    private var videoWidth = 1080
    private var videoHeight = 1920

    //========  비디오 변수 END ========//

    //========  오디오 변수 START ========//
    private val audioConstraints by lazy { MediaConstraints() }
    private val audioSource by lazy { peerConnectionFactory.createAudioSource(audioConstraints) }
    private val localAudioTrack by lazy { peerConnectionFactory.createAudioTrack("101", audioSource) }
    //========  오디오 변수 END ========//


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        initFragment()

        return binding.root

    }

    private fun initFragment() {

        initFactory()

        initVideoViews()

        initLocalVideo()

        initLocalAudio()

        startLocalVideoCapturer()

    }

    private fun initFactory() {

        AppData.debug(TAG, "initFactory() called.")

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(activity)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder().setOptions(options).createPeerConnectionFactory()

    }

    private fun initVideoViews() {

        try {
            binding.localVideoView.init(rootEglBase.eglBaseContext, null)
//            binding.remoteVideoView.init(rootEglBase.getEglBaseContext(), null)

            // SurfaceView를 사용하기 때문에 뷰의 우선순위가 높은 것을 위쪽에 표시함
            // setZOrderMediaOverlay가 true인 것을 위쪽에 표시하도록 설정함
            binding.localVideoView.setZOrderMediaOverlay(true)
//            binding.remoteVideoView.setZOrderMediaOverlay(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun initLocalVideo() {

        AppData.debug(TAG, "initLocalVideo() called.")

        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            ?: AppData.debug(TAG, "initLocalVideo() : videoCapture is null")

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

    }

    private fun initLocalAudio() {

        AppData.debug(TAG, "initLocalAudio() called.")

        localAudioTrack.setEnabled(true)

        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleNoiseSuppression", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("levelControl", "true"))

    }

    private fun startLocalVideoCapturer() {

        AppData.debug(TAG, "startLocalVideoCapturer() called.")

        videoSource?.adaptOutputFormat(videoWidth, videoHeight, videoFps)

        videoCapturer?.let {
            val displayMetrics = DisplayMetrics()
            val videoWidth = displayMetrics.widthPixels
            val videoHeight = displayMetrics.heightPixels

            it.startCapture(videoWidth, videoHeight, videoFps)
        }

        binding.localVideoView.isVisible = true
        localVideoTrack.addSink(binding.localVideoView)

        binding.localVideoView.setMirror(true)

    }

    /**
     * VideoCapturer 객체 생성
     */
    private fun createVideoCapturer(): VideoCapturer? =
        if (Camera2Enumerator.isSupported(activity)) {
            createCameraCapturer(Camera2Enumerator(activity))
        } else {
            createCameraCapturer(Camera1Enumerator(false))
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