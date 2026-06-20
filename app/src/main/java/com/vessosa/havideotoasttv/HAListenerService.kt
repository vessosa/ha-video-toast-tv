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
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var msgId = 0
    private var stopped = false
    private lateinit var overlay: OverlayToastManager

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayToastManager(this)
        startForeground(1, notification("Connecting to Home Assistant"))
        connect()
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
        ws?.cancel()
        val req = Request.Builder().url(Network.wsUrl(config.haUrl)).build()
        ws = Network.unsafeClient.newWebSocket(req, Listener(config))
    }

    private fun reconnectLater() {
        if (!stopped) handler.postDelayed({ connect() }, 5000)
    }

    private inner class Listener(private val config: ConfigStore.Config) : WebSocketListener() {
        private var subId = -1

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "auth_required" -> {
                    webSocket.send(JSONObject()
                        .put("type", "auth")
                        .put("access_token", config.token)
                        .toString())
                }
                "auth_ok" -> {
                    msgId += 1
                    subId = msgId
                    webSocket.send(JSONObject()
                        .put("id", subId)
                        .put("type", "subscribe_events")
                        .put("event_type", "ha_video_toast")
                        .toString())
                    updateNotification("Connected to Home Assistant")
                }
                "auth_invalid" -> updateNotification("Home Assistant auth failed")
                "event" -> {
                    if (json.optInt("id") != subId) return
                    val data = json.getJSONObject("event").optJSONObject("data") ?: return
                    val camera = data.optString("camera", "")
                    if (camera.isBlank()) return
                    val duration = data.optInt("duration", config.duration)
                    val fullscreen = data.optBoolean("fullscreen", false)
                    handler.post {
                        if (Settings.canDrawOverlays(this@HAListenerService)) {
                            overlay.show(camera, duration, fullscreen)
                        }
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            updateNotification("Connection lost; retrying")
            ws = null
            reconnectLater()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ws = null
            reconnectLater()
        }
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
