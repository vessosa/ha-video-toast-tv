package com.vessosa.havideotoasttv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class HAListenerService : Service() {
    companion object {
        const val ACTION_RELOAD = "com.vessosa.havideotoasttv.RELOAD"
        private const val EVENT_TV_TOAST = "ha_tv_toast"
        private const val EVENT_VIDEO_TOAST = "ha_video_toast"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var msgId = 0
    private var connectionGeneration = 0
    private var stopped = false
    private lateinit var overlay: OverlayToastManager

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayToastManager(this)
        startForeground(1, notification("Connecting to Home Assistant"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopped = false
        if (intent?.action == ACTION_RELOAD) {
            updateNotification("Reloading Home Assistant config")
            connect()
        } else if (ws == null) {
            connect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopped = true
        ws?.close(1000, "service stopped")
        ws = null
        overlay.closeAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        val config = ConfigStore.load(this)
        if (!config.isConfigured) {
            stopSelf()
            return
        }
        stopped = false
        connectionGeneration += 1
        val generation = connectionGeneration
        ws?.cancel()
        val req = Request.Builder().url(Network.wsUrl(config.haUrl)).build()
        ws = Network.unsafeClient.newWebSocket(req, Listener(config, generation))
    }

    private fun reconnectLater() {
        if (!stopped) handler.postDelayed({ connect() }, 5000)
    }

    private inner class Listener(
        private val config: ConfigStore.Config,
        private val generation: Int
    ) : WebSocketListener() {
        private val subscriptions = mutableMapOf<Int, String>()

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent()) return
            val json = JSONObject(text)
            when (json.optString("type")) {
                "auth_required" -> {
                    webSocket.send(JSONObject()
                        .put("type", "auth")
                        .put("access_token", config.token)
                        .toString())
                }
                "auth_ok" -> {
                    subscribe(webSocket, EVENT_TV_TOAST)
                    subscribe(webSocket, EVENT_VIDEO_TOAST)
                    updateNotification("Connected to Home Assistant")
                }
                "auth_invalid" -> updateNotification("Home Assistant auth failed")
                "event" -> {
                    val eventType = subscriptions[json.optInt("id")] ?: return
                    val data = json.getJSONObject("event").optJSONObject("data") ?: return
                    val request = parseToast(eventType, data) ?: return
                    handler.post {
                        if (isCurrent() && Settings.canDrawOverlays(this@HAListenerService)) {
                            overlay.show(request)
                        }
                    }
                }
            }
        }

        private fun subscribe(webSocket: WebSocket, eventType: String) {
            msgId += 1
            subscriptions[msgId] = eventType
            webSocket.send(JSONObject()
                .put("id", msgId)
                .put("type", "subscribe_events")
                .put("event_type", eventType)
                .toString())
        }

        private fun parseToast(eventType: String, data: JSONObject): ToastRequest? {
            val duration = data.optInt("duration", config.duration)
            val type = data.optString("type", "").lowercase()
            val camera = data.optString("camera", "").trim()

            if (eventType == EVENT_VIDEO_TOAST || type == "video" || camera.isNotBlank()) {
                if (camera.isBlank()) return null
                return ToastRequest.Video(
                    camera = camera,
                    duration = duration,
                    fullscreen = data.optBoolean("fullscreen", false)
                )
            }

            val title = data.optString("title", "").trim()
            val message = data.optString("message", data.optString("text", "")).trim()
            if (title.isBlank() && message.isBlank()) return null

            return ToastRequest.Message(
                title = title.ifBlank { "Notification" },
                message = message,
                duration = duration,
                level = when (data.optString("level", "info").lowercase()) {
                    "success", "ok" -> ToastRequest.Message.Level.SUCCESS
                    "warning", "warn" -> ToastRequest.Message.Level.WARNING
                    "error", "critical", "danger" -> ToastRequest.Message.Level.ERROR
                    else -> ToastRequest.Message.Level.INFO
                }
            )
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent()) return
            updateNotification("Connection lost; retrying")
            ws = null
            reconnectLater()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent()) return
            ws = null
            reconnectLater()
        }

        private fun isCurrent(): Boolean =
            !stopped && generation == connectionGeneration
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, notification(text))
    }

    private fun notification(text: String): Notification {
        val channelId = "ha_video_toast_tv"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                channelId,
                "HA Video Toast TV",
                NotificationManager.IMPORTANCE_LOW
            ))
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("HA Video Toast TV")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
    }
}
