package com.vessosa.havideotoasttv

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object Network {
    val unsafeClient: OkHttpClient by lazy {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf<TrustManager>(trust), SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun wsUrl(haUrl: String): String {
        val base = haUrl.trim().trimEnd('/')
        return when {
            base.startsWith("https://") -> base.replaceFirst("https://", "wss://") + "/api/websocket"
            base.startsWith("http://") -> base.replaceFirst("http://", "ws://") + "/api/websocket"
            else -> "ws://$base/api/websocket"
        }
    }

    fun streamUrl(haUrl: String, camera: String): String =
        haUrl.trim().trimEnd('/') + "/api/camera_proxy_stream/" + camera
}
