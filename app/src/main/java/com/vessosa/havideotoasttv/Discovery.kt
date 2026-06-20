package com.vessosa.havideotoasttv

import android.content.Context
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Discovery {
    fun scan(context: Context, onDone: (List<String>) -> Unit) {
        Thread {
            val prefix = subnetPrefix(context)
            if (prefix == null) {
                onDone(emptyList())
                return@Thread
            }
            val found = Collections.synchronizedList(mutableListOf<String>())
            val pool = Executors.newFixedThreadPool(32)
            val latch = CountDownLatch(254)
            for (i in 1..254) {
                val url = "http://$prefix.$i:8123"
                pool.execute {
                    try {
                        val c = URL("$url/api/").openConnection() as HttpURLConnection
                        c.connectTimeout = 250
                        c.readTimeout = 250
                        if (c.responseCode in 200..499) found += url
                        c.disconnect()
                    } catch (_: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(12, TimeUnit.SECONDS)
            pool.shutdownNow()
            onDone(found.sorted())
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun subnetPrefix(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ip = wm?.connectionInfo?.ipAddress ?: return null
        val parts = listOf(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }
}
