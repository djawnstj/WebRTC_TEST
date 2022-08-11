package com.webrtc.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.webrtc.app.databinding.FragmentVideoBinding
import org.json.JSONObject
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
//    private val videoConstraints by lazy { MediaConstraints() }
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

    var userId = ""
    var receiverId = ""

    val gson = Gson()


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

        connectToRedis()

    }

    /**
     * 서비스를 통해 레디스 서버 연결
     */
    fun connectToRedis() {
        AppData.debug(TAG, "connectToRedis called.")

        context?.apply {
            val serviceIntent = Intent(this, RedisService::class.java)
            serviceIntent.putExtra("command", "connect")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.startForegroundService(serviceIntent)
            } else {
                this.startService(serviceIntent)
            }
        }

    }

    /**
     * 서비스를 통해 레디스 서버 연결 해제
     */
    fun disconnectFromRedis() {
        context?.apply {
            val serviceIntent = Intent(this, RedisService::class.java)
            serviceIntent.putExtra("command", "disconnect")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.startForegroundService(serviceIntent)
            } else {
                this.startService(serviceIntent)
            }
        }

    }

    /**
     * 서비스를 통해 데이터 전송
     */
    fun sendData(data:String) {
        context?.apply {
            val serviceIntent = Intent(this, RedisService::class.java)
            serviceIntent.putExtra("command", "send")
            serviceIntent.putExtra("data", data)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.startForegroundService(serviceIntent)
            } else {
                this.startService(serviceIntent)
            }
        }

    }


    fun onRedisConnection(command:String?, channel:String?, data:String?) {

        if (AppData.startInviteFlag) {
            if (command == "subscribed") {
                sendInvite("video")

                AppData.startInviteFlag = false
            }
        }
    }

    fun onRedisData(command:String?, channel:String?, data:String?) {

        // 세션 메시지 처리
        data?.apply {
            AppData.error(TAG, "redis message : $command, $channel, \n$data")
        }

    }

    fun onRedisLog(command:String?, channel:String?, data:String?) {
        AppData.debug(TAG, "onRedisLog called : ${command}, ${channel}, ${data}")

    }

    /** PeerConnectionFactory 초기화 */
    private fun initFactory() {

        AppData.debug(TAG, "initFactory() called.")

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(activity)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder().setOptions(options).createPeerConnectionFactory()

    }

    /** WebRTC SurfaceViewRenderer 초기화 */
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

    /** local videoCapturer 초기화 */
    private fun initLocalVideo() {

        AppData.debug(TAG, "initLocalVideo() called.")

        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            ?: AppData.debug(TAG, "initLocalVideo() : videoCapture is null")

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

    }

    /** local audio 초기화 */
    private fun initLocalAudio() {

        AppData.debug(TAG, "initLocalAudio() called.")

        localAudioTrack.setEnabled(true)

        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googleNoiseSuppression", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("levelControl", "true"))

    }

    /** 영상 캡처 시작 */
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

    //===== 세션 메시지 START =====//

    /**
     * Step 1) Invite 전송
     * A -> B
     *
     * video : 일반, screen : 화면공유
     */
    fun sendInvite(category:String) {

        try {
            val sessionId = createRequestCode()
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", receiverId)
            output.put("command", "session")
            output.put("method", "invite")
            output.put("code", "100")
            output.put("category", category)

            output.put("data", "")

            AppData.debug(TAG, "서버로 보낼 데이터 : $output")

            sendData(output.toString())

            AppData.callStatus = "ringing"

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * Step 2) Ringing 전송
     * B -> A
     */
    fun sendRinging(message: SessionMessage) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "ringing")
            output.put("code", "180")
            output.put("category", message.category)
            output.put("data", "")
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
            //callStatus = "ringing"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Step 3) Accept(ok) 전송
     * A -> B
     */
    fun sendAccept(message: SessionMessage) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            //val message = messageReceived
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message!!.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "ok")
            output.put("code", "200")
            output.put("category", message.category)

            output.put("data", "")


            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Step 4) Ack 전송
     * B -> A
     */
    fun sendAck(message: SessionMessage) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "ack")
            output.put("code", "210")
            output.put("category", message.category)
            output.put("data", "")

            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * End Step 1) Bye 전송
     * A -> B
     */
    fun sendBye(category: String?) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", AppData.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", receiverId)
            output.put("command", "session")
            output.put("method", "bye")
            output.put("code", "400")
            output.put("category", category)
            output.put("data", "")
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
            //callStatus = "bye"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * End Step 2) Ok 전송
     * B -> A
     */
    fun sendOk(message: SessionMessage) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "ok")
            output.put("code", "200")
            output.put("category", message.category)
            output.put("data", "")

            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // MIKE START 220429
    // OK --> BYE_OK로 수정
    /**
     * End Step 2) BYE_OK 전송
     * B -> A
     */
    fun sendByeOk(message: SessionMessage) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "bye_ok")
            output.put("code", "200")
            output.put("category", message.category)
            output.put("data", "")
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * INFO 전송
     */
    fun sendInfo(sessionId:String, userId:String, videoEnabled:String, audioEnabled:String, category:String) {
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", "")
            output.put("command", "session")
            output.put("method", "info")
            output.put("code", "510")
            output.put("category", category)
            output.put("data", "")
            output.put("videoEnabled", videoEnabled)
            output.put("audioEnabled", audioEnabled)
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ACTION 전송
     */
    fun sendAction(sessionId:String, userId:String, receiver:String, method:String, data:String) {
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", receiver)
            output.put("command", "action")
            output.put("method", method)
            output.put("code", "510")
            output.put("category", "")
            output.put("data", data)
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ACTION (launch) 전송
     */
    fun sendLaunchAction(sessionId:String, userId:String, receiver:String, method:String, packageName:String, activityName:String, paramKey:String, paramValue:String) {
        try {
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", receiver)
            output.put("command", "action")
            output.put("method", method)
            output.put("code", "510")
            output.put("category", "")
            output.put("packageName", packageName)
            output.put("activityName", activityName)
            output.put("paramKey", paramKey)
            output.put("paramValue", paramValue)
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * sdp 전송
     */
    fun sendSdp(message: SessionMessage, sessionDescription: SessionDescription) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        var sdp =
            "{" + "\"" + "type" + "\"" + ":" + "\"" + sessionDescription.type.canonicalForm() + "\","
        sdp += "\"" + "sdp" + "\"" + ":" + "\"" + sessionDescription.description + "\"" + "}"
        try {
            val sdpObject = JSONObject()
            sdpObject.put("type", sessionDescription.type.canonicalForm())
            sdpObject.put("sdp", sessionDescription.description)
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "sdp")
            output.put("code", "310")
            output.put("category", message.category)
            output.put("data", sdpObject)
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * candidate 전송
     */
    fun sendCandidate(message: SessionMessage, iceCandidate: IceCandidate) {
        //if (connStatus == MediaDataThread.DISCONNECTED) {
        //    Log.d(MediaDataThread.TAG, "서버에 연결되어 있지 않습니다. 먼저 서버에 연결하세요.")
        //    return
        //}
        try {
            val candidateObject = JSONObject()
            candidateObject.put("type", "candidate")
            candidateObject.put("label", iceCandidate.sdpMLineIndex)
            candidateObject.put("id", iceCandidate.sdpMid)
            candidateObject.put("candidate", iceCandidate.sdp)
            val requestCode = createRequestCode()
            val output = JSONObject()
            output.put("sessionId", message.sessionId)
            output.put("requestCode", requestCode)
            output.put("userId", userId)
            output.put("sender", userId)
            output.put("receiver", message.sender)
            output.put("command", "session")
            output.put("method", "candidate")
            output.put("code", "320")
            output.put("category", message.category)
            output.put("data", candidateObject)
            AppData.debug(TAG, "서버로 보낼 데이터 : $output")
            sendData(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Create a new request id
     */
    fun createRequestCodeLong(): Long {
        return AppData.counter.getAndIncrement()
    }

    fun createRequestCode(): String {
        return createRequestCodeLong().toString()
    }


    //===== 세션 메시지 END =====//

}