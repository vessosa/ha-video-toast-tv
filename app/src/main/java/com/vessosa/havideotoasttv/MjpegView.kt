package com.vessosa.havideotoasttv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.io.ByteArrayOutputStream
import java.io.InputStream
import okhttp3.Request

class MjpegView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val lock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 82, 91)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private var bitmap: Bitmap? = null
    private var status = "Connecting..."
    @Volatile private var running = false

    fun start(url: String, token: String) {
        stop()
        running = true
        Thread {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                Network.unsafeClient.newCall(req).execute().use { resp ->
                    val stream = resp.body?.byteStream() ?: throw IllegalStateException("empty stream")
                    readFrames(stream)
                }
            } catch (_: Exception) {
                status = "No signal"
                postInvalidate()
            }
        }.start()
    }

    fun stop() {
        running = false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(9, 9, 11))
        val frame = synchronized(lock) { bitmap }
        if (frame != null) {
            canvas.drawBitmap(frame, null, android.graphics.Rect(0, 0, width, height), paint)
        } else {
            canvas.drawText(status, width / 2f, height / 2f, textPaint)
        }
    }

    private fun readFrames(input: InputStream) {
        val buffer = ByteArrayOutputStream()
        val temp = ByteArray(4096)
        while (running) {
            val read = input.read(temp)
            if (read <= 0) break
            buffer.write(temp, 0, read)
            val bytes = buffer.toByteArray()
            var start = -1
            var end = -1
            for (i in 0 until bytes.size - 1) {
                if (start < 0 && bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte()) start = i
                if (start >= 0 && bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                    end = i + 2
                    break
                }
            }
            if (start >= 0 && end > start) {
                val bmp = BitmapFactory.decodeByteArray(bytes, start, end - start)
                if (bmp != null) synchronized(lock) { bitmap = bmp }
                val remain = bytes.copyOfRange(end, bytes.size)
                buffer.reset()
                buffer.write(remain)
                postInvalidate()
            } else if (bytes.size > 1_000_000) {
                buffer.reset()
            }
        }
    }
}
