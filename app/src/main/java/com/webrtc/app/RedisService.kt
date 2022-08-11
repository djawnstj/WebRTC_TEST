package com.webrtc.app

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.*
import android.os.PowerManager.WakeLock
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.event.connection.ConnectionActivatedEvent
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent
import io.lettuce.core.pubsub.RedisPubSubListener
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class RedisService: Service() {

    companion object {

        private const val TAG = "RedisService"

        val dateFormat = SimpleDateFormat("yyyy-mm-dd HH:mm:ss", Locale.KOREA)

        //========== 레디스 START ==========//

        // 레디스 초기 미연결 시 재연결까지의 시간 간격
        const val redisInitInterval = 2000L

        // 레디스 데이터 수신을 위한 리시버 액션
        const val ACTION_REDIS_DATA = "com.uns.redis.data"

        // 연결 상태를 감시하기 위한 리시버 액션
        const val ACTION_CONNECTION_STATE = "com.uns.connection.state"

        // 로그릴 받기 위한 리시버 액션
        const val ACTION_LOG = "com.uns.log"

        //========== 레디스 END ==========//

    }

    // Lock 객체
    var curPowerLock: WakeLock? = null
    var curWifiLock: WifiLock? = null

    val gson = Gson()

    val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()

        initLock()

        // NotificationChannel 초기화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) initNotificationChannel()

    }

    /**
     * Lock 객체 초기화
     */
    private fun initLock() {

        // wakeLock 객체 초기화
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        curPowerLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK + PowerManager.ACQUIRE_CAUSES_WAKEUP + PowerManager.ON_AFTER_RELEASE, "uns:WAKELOCK")
        curPowerLock?.acquire()

        // wifiLock 객체 초기화
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        curWifiLock = wifiManager.createWifiLock("uns:WIFILOCK")
        curWifiLock?.setReferenceCounted(true)
        curWifiLock?.acquire()

    }

    /**
     * 포그라운드 서비스를 위해 노티 표시
     */
    @TargetApi(Build.VERSION_CODES.Q)
    private fun initNotificationChannel() {

        val channelId = "redis_channel"
        val notiChannel = NotificationChannel(channelId, "Redis Channel", NotificationManager.IMPORTANCE_LOW)
        val notiManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notiManager.createNotificationChannel(notiChannel)

        val noti = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Redis")
            .setContentText("RedisService started.").build()
        startForeground(1, noti)

    }

    override fun onDestroy() {
        super.onDestroy()

        // wakeLock 해제
        if (curPowerLock != null) {
            curPowerLock?.release()
            curPowerLock = null
        }

        // wifiLock 해제
        if (curWifiLock != null) {
            curWifiLock?.release()
            curWifiLock = null
        }

    }

    /**
     * 명령 처리 connect : 레디스 서버 연결
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            handleCommand(it)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * 명령 처리
     */
    private fun handleCommand(intent: Intent) {
        val command = intent.getStringExtra("command")
        AppData.debug(TAG, "command: $command")

        command?.apply {
            when(command) {
                // 레디스 연결 명령
                "connect" -> {
                    disconnectRedis()
                    connectRedis()
                }
                "sned" -> {
                    val data = intent.getStringExtra("data")

                    data?.apply {
                        sendData(this)
                    }
                }
            }
        }
    }

    /**
     * 레디스 서버 연결
     */
    private fun connectRedis() {

        // 쓰레드로 실행
        CoroutineScope(Dispatchers.Default).launch {

            try {
                // 레디스 연결 객체 생성
                AppData.redisUri = RedisURI.create(AppData.redisHost, AppData.redisPort)
                AppData.redisClient = RedisClient.create(AppData.redisUri)

                // 레디스 연결 상태 감시
                AppData.redisClient?.apply {

                    val eventBus = this.resources.eventBus()
                    eventBus.get().subscribe() {
                        if (it is ConnectionActivatedEvent) {
                            AppData.debug(TAG, "ConnectionActivatedEvent called.")

                            val data = "connected"
                            sendToActivity(ACTION_CONNECTION_STATE, "connection", AppData.redisChannel, data)

                        } else if (it is ConnectionDeactivatedEvent) {
                            AppData.debug(TAG, "ConnectionDeactivatedEvent called.")

                            val data = "disconnected"
                            sendToActivity(ACTION_CONNECTION_STATE, "connection", AppData.redisChannel, data)
                        }
                    }

                }
            } catch (e: Exception) {
                e.printStackTrace()

                // 연결 안되었을때는 재시도
                val errorMsg = e.toString()
                if(errorMsg.contains("Unable to connect")) {
                    AppData.debug(TAG, "reconnecting after 5 seconds.")

                    handler.postDelayed({
                        connectRedis()
                    }, redisInitInterval)
                    AppData.debug(TAG, e.cause.toString())
                }

                // 0.2초 후 subscribe
                delay(200)
                subscribeChannel()

            }

        }

    }

    /**
     * 레디스 채널 등록
     */
    private fun subscribeChannel() {

        // 스레드로 실행
        CoroutineScope(Dispatchers.Default).launch {

            try {

                // 레디스 Pub/Sub 연결 및 데이터 수신 리스너 등록
                AppData.redisClient?.apply {

                    val connection = this.connectPubSub().sync()
                    connection.statefulConnection.addListener(object: RedisPubSubListener<String, String> {
                        override fun message(channel: String?, message: String?) {
                            AppData.debug(TAG, "DATA: $channel, $message")

                            sendToActivity(ACTION_REDIS_DATA,"data", channel.toString(), message.toString())

                            // 세션 메시지 중에서 launch 메서드만 처리
                            message?.apply {
                                processSessionMessage(this)
                            }
                        }

                        override fun message(pattern: String?, channel: String?, message: String?) {
                            AppData.debug(TAG, "DATA2: $pattern, $channel, $message")
                        }

                        override fun subscribed(channel: String?, count: Long) {
                            AppData.debug(TAG, "subscribed called : $channel, $count")

                            channel?.apply {
                                sendToActivity(ACTION_CONNECTION_STATE, "subscribed", channel, count.toString())
                            }
                        }

                        override fun psubscribed(pattern: String?, count: Long) {
                            AppData.debug(TAG, "psubscribed called.")
                        }

                        override fun unsubscribed(channel: String?, count: Long) {
                            AppData.debug(TAG, "unsubscribed called.")

                            channel?.apply {
                                sendToActivity(ACTION_CONNECTION_STATE, "unsubscribed", channel, count.toString())
                            }
                        }

                        override fun punsubscribed(pattern: String?, count: Long) {
                            AppData.debug(TAG, "punsubscribed called.")
                        }

                    })

                    connection.subscribe(AppData.redisChannel)
                    AppData.debug(TAG, "subscribed to channel: ${AppData.redisChannel}")

                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

    }

    /**
     * 레디스로부터 받은 세션 메시지 처리
     */
    private fun processSessionMessage(data: String) {

        try {
            // JSON 문자열 파싱
            val message = gson.fromJson(data, SessionMessage::class.java)

            /*
            // 나 자신이 보낸 메시지라면 무시
            var userId = ""
            AppData.userData?.userId?.apply {
                userId = this
            }

            message.userId?.apply {
                if (this == userId) {
                    AppInfo.printLog("나 자신이 보낸 메시지이므로 무시함")
                    return
                }
            }
            */

            AppData.debug(TAG, "message: ${message.command}, ${message.method}, ${message.userId}, ${message.category}, ${message.data}")

            if (message.command == "action") {

                if (message.method == "launch") {

                    val dataTokens = message.data.toString().split(",")
                    var packageToken = ""
                    var activityToken = ""
                    var paramKeyToken = ""
                    var paramValueToken = ""

                    if (dataTokens.size == 4) {
                        packageToken = dataTokens[0]

                        if (dataTokens[1] != "none") activityToken = dataTokens[1]
                        if (dataTokens[2] != "none") paramKeyToken = dataTokens[2]
                        if (dataTokens[3] != "none") paramValueToken = dataTokens[3]

                    }

                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.component = ComponentName(packageToken, activityToken)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_SINGLE_TOP + Intent.FLAG_ACTIVITY_CLEAR_TOP)

                    if (paramKeyToken.isNotBlank()) {
                        intent.extras?.putString(paramKeyToken, paramValueToken)
                    }

                    startActivity(intent)

                }

            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * 레디스 채널 등록 해제
     */
    private fun disconnectRedis() {

        CoroutineScope(Dispatchers.Default).launch {

            try {

                // 레디스 PubSub 연결 및 데이터 수신 리스너 등록
                AppData.redisClient?.apply {
                    this.shutdown()
                    AppData.debug(TAG, "redis connection closed.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

    }

    /**
     * 데이터 전송
     */
    private fun sendData(data: String) {

        CoroutineScope(Dispatchers.Default).launch {

            try {

                // 레디스 PubSub 연결로 데이터 전송
                AppData.redisClient?.apply {

                    val connection = this.connectPubSub().sync()
                    connection.publish(AppData.redisChannel, data)

                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

    }

    /**
     * 화면의 리시버로 데이터 전달
     */
    private fun sendToActivity(action: String, command: String, channel: String, data: String) {

        val broadcastIntent = Intent(action)
        broadcastIntent.putExtra("command", command)
        broadcastIntent.putExtra("channel", channel)
        broadcastIntent.putExtra("data", data)
        sendBroadcast(broadcastIntent)

    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Return the communication channel to the service.")
    }

}