package com.vessosa.havideotoasttv

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class OverlayToastManager(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val entries = mutableListOf<Entry>()

    fun show(request: ToastRequest) {
        when (request) {
            is ToastRequest.Video -> showVideo(request)
            is ToastRequest.Message -> showMessage(request)
        }
    }

    fun show(camera: String, duration: Int, fullscreen: Boolean) {
        show(ToastRequest.Video(camera, duration, fullscreen))
    }

    private fun showVideo(request: ToastRequest.Video) {
        if (request.fullscreen) {
            showFullscreen(request.camera, request.duration)
            return
        }
        entries.firstOrNull { it.key == "video:${request.camera}" }?.let {
            it.remainingMs = request.duration * 1000
            it.progress.max = request.duration * 1000
            return
        }
        val config = ConfigStore.load(context)
        while (entries.size >= config.maxToasts) entries.firstOrNull()?.close()
        val metrics = toastMetrics(config)
        val view = buildVideoToast(request.camera, metrics, request.duration)
        val entry = Entry(
            key = "video:${request.camera}",
            root = view.root,
            video = view.video,
            progress = view.progress,
            width = view.width,
            height = view.height,
            remainingMs = request.duration * 1000
        )
        entries += entry
        wm.addView(view.root, paramsFor(config, metrics, entryOffset(metrics, entries.lastIndex), view.width, view.height))
        view.video?.start(Network.streamUrl(config.haUrl, request.camera), config.token)
        tick(entry)
        relayout()
    }

    private fun showMessage(request: ToastRequest.Message) {
        val config = ConfigStore.load(context)
        while (entries.size >= config.maxToasts) entries.firstOrNull()?.close()
        val metrics = toastMetrics(config)
        val view = buildMessageToast(request, metrics)
        val entry = Entry(
            key = "message:${System.nanoTime()}",
            root = view.root,
            video = null,
            progress = view.progress,
            width = view.width,
            height = view.height,
            remainingMs = request.duration * 1000
        )
        entries += entry
        wm.addView(view.root, paramsFor(config, metrics, entryOffset(metrics, entries.lastIndex), view.width, view.height))
        tick(entry)
        relayout()
    }

    fun closeAll() {
        entries.toList().forEach { it.close() }
    }

    private fun buildVideoToast(camera: String, metrics: ToastMetrics, duration: Int): ToastViews {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 182, 212))
            setPadding(2, 2, 2, 2)
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(24, 24, 27))
        }
        root.addView(inner, LinearLayout.LayoutParams(-1, -1))
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 8, 0)
        }
        inner.addView(header, LinearLayout.LayoutParams(-1, metrics.headerHeight))
        val title = TextView(context).apply {
            text = camera.removePrefix("camera.").replace('_', ' ').replaceFirstChar { it.uppercase() }
            setTextColor(Color.rgb(244, 244, 245))
            textSize = metrics.titleSp
            setSingleLine(true)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        val video = MjpegView(context)
        inner.addView(video, LinearLayout.LayoutParams(metrics.videoWidth, metrics.videoHeight))
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = duration * 1000
            progress = duration * 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(6, 182, 212))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(39, 39, 42))
        }
        inner.addView(progress, LinearLayout.LayoutParams(-1, metrics.progressHeight))
        return ToastViews(
            root = root,
            video = video,
            progress = progress,
            width = metrics.videoWidth + 4,
            height = metrics.videoHeight + metrics.headerHeight + metrics.progressHeight + 4
        )
    }

    private fun buildMessageToast(request: ToastRequest.Message, metrics: ToastMetrics): ToastViews {
        val accent = colorFor(request.level)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(accent)
            setPadding(2, 2, 2, 2)
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(24, 24, 27))
            setPadding(metrics.messagePadding, metrics.messagePadding, metrics.messagePadding, metrics.messagePadding)
        }
        root.addView(inner, LinearLayout.LayoutParams(-1, 0, 1f))
        val title = TextView(context).apply {
            text = request.title
            setTextColor(Color.rgb(244, 244, 245))
            textSize = metrics.messageTitleSp
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(2f, 1.08f)
            gravity = Gravity.CENTER_VERTICAL
        }
        inner.addView(title, LinearLayout.LayoutParams(-1, metrics.messageTitleHeight))
        if (request.message.isNotBlank()) {
            val message = TextView(context).apply {
                text = request.message
                setTextColor(Color.rgb(212, 212, 216))
                textSize = metrics.messageBodySp
                includeFontPadding = true
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(2f, 1.05f)
            }
            inner.addView(message, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = request.duration * 1000
            progress = request.duration * 1000
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(39, 39, 42))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, metrics.progressHeight))
        return ToastViews(
            root = root,
            video = null,
            progress = progress,
            width = metrics.messageWidth,
            height = metrics.messageHeight
        )
    }

    private fun showFullscreen(camera: String, duration: Int) {
        val config = ConfigStore.load(context)
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val video = MjpegView(context)
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(context).apply {
            text = camera
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(36, 24, 36, 24)
        }
        root.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(root, lp)
        video.start(Network.streamUrl(config.haUrl, camera), config.token)
        val close = Runnable {
            video.stop()
            runCatching { wm.removeView(root) }
        }
        root.setOnClickListener { close.run() }
        root.setOnKeyListener { _, _, _ ->
            close.run()
            true
        }
        handler.postDelayed(close, duration * 1000L)
    }

    private fun tick(entry: Entry) {
        if (!entries.contains(entry)) return
        entry.remainingMs = max(0, entry.remainingMs - 100)
        entry.progress.progress = entry.remainingMs
        if (entry.remainingMs <= 0) {
            entry.close()
        } else {
            handler.postDelayed({ tick(entry) }, 100)
        }
    }

    private fun relayout() {
        val config = ConfigStore.load(context)
        val metrics = toastMetrics(config)
        entries.forEachIndexed { index, entry ->
            runCatching {
                wm.updateViewLayout(
                    entry.root,
                    paramsFor(config, metrics, entryOffset(metrics, index), entry.width, entry.height)
                )
            }
        }
    }

    private fun entryOffset(metrics: ToastMetrics, index: Int): Int {
        if (index <= 0) return 0
        return entries.take(index).sumOf { it.width } + metrics.gap * index
    }

    private fun paramsFor(
        config: ConfigStore.Config,
        metrics: ToastMetrics,
        offset: Int,
        width: Int,
        height: Int
    ): WindowManager.LayoutParams {
        val right = config.corner.contains("right")
        val bottom = config.corner.contains("bottom")
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (bottom) Gravity.BOTTOM else Gravity.TOP) or
                (if (right) Gravity.RIGHT else Gravity.LEFT)
            x = metrics.margin + offset
            y = metrics.margin
        }
    }

    private fun toastMetrics(config: ConfigStore.Config): ToastMetrics {
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Rect().also { wm.defaultDisplay.getRectSize(it) }
        }
        val screenW = max(bounds.width(), 1)
        val screenH = max(bounds.height(), 1)
        val requestedHeight = config.height.takeIf { it > 0 }
        val requestedWidth = config.width.takeIf { it > 0 }
        val targetHeight = requestedHeight ?: (screenH * 0.28f).roundToInt()
        val maxHeight = (screenH * 0.32f).roundToInt()
        val minHeight = min(340, (screenH * 0.24f).roundToInt())
        val videoH = targetHeight.coerceIn(minHeight, maxHeight)
        val targetWidth = requestedWidth ?: (videoH * 16f / 9f).roundToInt()
        val maxWidth = (screenW * 0.58f).roundToInt()
        val videoW = min(targetWidth, maxWidth).coerceAtLeast(320)
        val messageMaxW = max(320, min(screenW - 64, 640))
        val messageMinW = min(360, messageMaxW)
        val messageW = (screenW * 0.34f).roundToInt().coerceIn(messageMinW, messageMaxW)
        val scale = screenH / 2160f
        return ToastMetrics(
            videoWidth = videoW,
            videoHeight = (videoW * 9f / 16f).roundToInt().coerceAtMost(videoH),
            headerHeight = max(36, (54 * scale).roundToInt()),
            progressHeight = max(4, (8 * scale).roundToInt()),
            margin = max(18, (48 * scale).roundToInt()),
            gap = max(8, (18 * scale).roundToInt()),
            titleSp = if (screenH >= 1600) 18f else 15f,
            messageWidth = messageW,
            messageHeight = max(176, (screenH * 0.16f).roundToInt()).coerceAtMost(280),
            messagePadding = max(18, (30 * scale).roundToInt()),
            messageTitleHeight = max(62, (86 * scale).roundToInt()),
            messageTitleSp = if (screenH >= 1600) 23f else 20f,
            messageBodySp = if (screenH >= 1600) 19f else 16f
        )
    }

    private fun colorFor(level: ToastRequest.Message.Level): Int =
        when (level) {
            ToastRequest.Message.Level.INFO -> Color.rgb(6, 182, 212)
            ToastRequest.Message.Level.SUCCESS -> Color.rgb(34, 197, 94)
            ToastRequest.Message.Level.WARNING -> Color.rgb(245, 158, 11)
            ToastRequest.Message.Level.ERROR -> Color.rgb(239, 68, 68)
        }

    private inner class Entry(
        val key: String,
        val root: View,
        val video: MjpegView?,
        val progress: ProgressBar,
        val width: Int,
        val height: Int,
        var remainingMs: Int
    ) {
        fun close() {
            video?.stop()
            entries.remove(this)
            runCatching { wm.removeView(root) }
            relayout()
        }
    }

    private data class ToastViews(
        val root: LinearLayout,
        val video: MjpegView?,
        val progress: ProgressBar,
        val width: Int,
        val height: Int
    )

    private data class ToastMetrics(
        val videoWidth: Int,
        val videoHeight: Int,
        val headerHeight: Int,
        val progressHeight: Int,
        val margin: Int,
        val gap: Int,
        val titleSp: Float,
        val messageWidth: Int,
        val messageHeight: Int,
        val messagePadding: Int,
        val messageTitleHeight: Int,
        val messageTitleSp: Float,
        val messageBodySp: Float
    )
}
