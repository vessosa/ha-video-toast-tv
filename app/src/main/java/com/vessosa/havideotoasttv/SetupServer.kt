package com.vessosa.havideotoasttv

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

class SetupServer(private val context: Context, private val onSaved: () -> Unit) {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    var url: String = ""
        private set

    fun start(): String {
        if (running && url.isNotBlank()) return url
        server = ServerSocket(0)
        running = true
        val port = server!!.localPort
        url = "http://${localIp()}:$port"
        Thread { loop() }.start()
        return url
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
    }

    private fun loop() {
        while (running) {
            val socket = runCatching { server?.accept() }.getOrNull() ?: break
            Thread {
                socket.use {
                    val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    val request = reader.readLine() ?: return@use
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                        if (line.startsWith("Content-Length:", true)) {
                            contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }
                    val body = CharArray(contentLength)
                    if (contentLength > 0) reader.read(body)
                    val response = if (request.startsWith("POST /save")) {
                        save(String(body))
                        "Saved. You can close this page."
                    } else {
                        page()
                    }
                    val bytes = response.toByteArray()
                    it.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
                    )
                    it.getOutputStream().write(bytes)
                }
            }.start()
        }
    }

    private fun save(body: String) {
        val values = body.split('&').associate {
            val k = it.substringBefore('=')
            val v = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            k to v
        }
        val old = ConfigStore.load(context)
        ConfigStore.save(context, old.copy(
            haUrl = values["ha_url"].orEmpty(),
            token = values["token"].orEmpty()
        ))
        onSaved()
    }

    private fun page(): String {
        val cfg = ConfigStore.load(context)
        return """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>HA Video Toast TV Setup</title>
            <style>body{font-family:system-ui;background:#09090b;color:#f4f4f5;margin:28px;max-width:760px}
            input,button{font-size:18px;padding:12px;margin:8px 0;width:100%;box-sizing:border-box}
            input{background:#18181b;color:#fff;border:1px solid #3f3f46;border-radius:6px}
            button{background:#06b6d4;border:0;border-radius:6px;font-weight:700}
            code{color:#67e8f9}</style></head><body>
            <h1>HA Video Toast TV</h1>
            <form method="post" action="/save">
            <label>Home Assistant URL</label>
            <input name="ha_url" value="${cfg.haUrl}" placeholder="http://192.168.1.100:8123">
            <label>Long-lived access token</label>
            <input name="token" value="${cfg.token}" placeholder="Paste token">
            <button type="submit">Save</button>
            </form>
            <p>Automation event type: <code>ha_video_toast</code></p>
            </body></html>
        """.trimIndent()
    }

    private fun localIp(): String {
        NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
            nif.inetAddresses.toList().forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: "127.0.0.1"
            }
        }
        return "127.0.0.1"
    }
}
