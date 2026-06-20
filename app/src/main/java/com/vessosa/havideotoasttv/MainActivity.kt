package com.vessosa.havideotoasttv

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : android.app.Activity() {
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var status: TextView
    private lateinit var qrImage: ImageView
    private lateinit var setupUrl: TextView
    private lateinit var saveButton: Button
    private lateinit var findButton: Button
    private lateinit var overlayButton: Button
    private lateinit var testButton: Button
    private lateinit var fullscreenButton: Button
    private var setupServer: SetupServer? = null
    private var initialFocusApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadConfig()
        setupServer = SetupServer(this) { runOnUiThread { loadConfig(); startServiceNow() } }
        startSetupServer()
        handleDeepLink(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onDestroy() {
        setupServer?.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !initialFocusApplied && ::saveButton.isInitialized) {
            initialFocusApplied = true
            saveButton.postDelayed({ saveButton.requestFocus() }, 200)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(52, 36, 52, 36)
            setBackgroundColor(Color.rgb(9, 9, 11))
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 0, 0, 0)
        }
        root.addView(left, LinearLayout.LayoutParams(0, -1, 1.25f))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 0.75f))

        left.addView(label("HA Video Toast TV", 30f, Color.WHITE))
        status = label("", 16f, Color.rgb(161, 161, 170))
        left.addView(status)
        left.addView(label("Home Assistant URL", 15f, Color.rgb(244, 244, 245)))
        urlInput = input("http://192.168.1.100:8123")
        left.addView(urlInput)
        left.addView(label("Long-lived access token", 15f, Color.rgb(244, 244, 245)))
        tokenInput = input("Paste token").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        left.addView(tokenInput)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        saveButton = button("Save + Start") { save(); startServiceNow() }
        findButton = button("Find HA") { scan() }
        overlayButton = button("Overlay Permission") { openOverlaySettings() }
        row.addView(saveButton)
        row.addView(findButton)
        row.addView(overlayButton)
        left.addView(row)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        testButton = button("Test Toast") { testToast(false) }
        fullscreenButton = button("Fullscreen Test") { testToast(true) }
        row2.addView(testButton)
        row2.addView(fullscreenButton)
        left.addView(row2)

        qrImage = ImageView(this)
        right.addView(qrImage, LinearLayout.LayoutParams(300, 300))
        setupUrl = label("", 16f, Color.rgb(103, 232, 249)).apply { gravity = Gravity.CENTER }
        right.addView(setupUrl)
        right.addView(label("Scan to configure from phone/browser", 14f, Color.rgb(161, 161, 170)).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        wireFocusOrder()
        saveButton.post { saveButton.requestFocus() }
    }

    private fun label(text: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(0, 8, 0, 8)
        }

    private fun input(hintText: String): EditText =
        EditText(this).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            hint = hintText
            setSingleLine(true)
            textSize = 18f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(82, 82, 91))
            setBackgroundColor(Color.rgb(24, 24, 27))
            setPadding(18, 8, 18, 8)
            layoutParams = LinearLayout.LayoutParams(-1, 58).apply { bottomMargin = 12 }
        }

    private fun button(text: String, onClick: (View) -> Unit): Button =
        Button(this).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            this.text = text
            isAllCaps = false
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = tvButtonBackground()
            stateListAnimator = null
            gravity = Gravity.CENTER
            setPadding(26, 0, 26, 0)
            setOnClickListener(onClick)
            minWidth = 260
            layoutParams = LinearLayout.LayoutParams(-2, 72).apply {
                setMargins(0, 14, 18, 0)
            }
        }

    private fun wireFocusOrder() {
        urlInput.nextFocusDownId = tokenInput.id
        tokenInput.nextFocusUpId = urlInput.id
        tokenInput.nextFocusDownId = saveButton.id

        saveButton.nextFocusUpId = tokenInput.id
        saveButton.nextFocusRightId = findButton.id
        saveButton.nextFocusDownId = testButton.id

        findButton.nextFocusLeftId = saveButton.id
        findButton.nextFocusRightId = overlayButton.id
        findButton.nextFocusUpId = tokenInput.id
        findButton.nextFocusDownId = fullscreenButton.id

        overlayButton.nextFocusLeftId = findButton.id
        overlayButton.nextFocusUpId = tokenInput.id
        overlayButton.nextFocusDownId = fullscreenButton.id

        testButton.nextFocusUpId = saveButton.id
        testButton.nextFocusRightId = fullscreenButton.id

        fullscreenButton.nextFocusUpId = findButton.id
        fullscreenButton.nextFocusLeftId = testButton.id
    }

    private fun tvButtonBackground(): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(Color.rgb(6, 182, 212), Color.WHITE, 4))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.rgb(8, 145, 178), Color.WHITE, 4))
            addState(intArrayOf(), rounded(Color.rgb(39, 39, 42), Color.rgb(6, 182, 212), 2))
        }

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }

    private fun loadConfig() {
        val cfg = ConfigStore.load(this)
        urlInput.setText(cfg.haUrl)
        tokenInput.setText(cfg.token)
        refreshStatus()
    }

    private fun save() {
        val old = ConfigStore.load(this)
        ConfigStore.save(this, old.copy(
            haUrl = urlInput.text.toString(),
            token = tokenInput.text.toString()
        ))
        val cfg = ConfigStore.load(this)
        status.text = "Saved. Configured: ${cfg.isConfigured}    Overlay: ${Settings.canDrawOverlays(this)}"
    }

    private fun refreshStatus() {
        val cfg = ConfigStore.load(this)
        val overlay = Settings.canDrawOverlays(this)
        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        status.text = "Configured: ${cfg.isConfigured}    Overlay: $overlay    Notifications: $notifications"
    }

    private fun startServiceNow() {
        save()
        ContextCompat.startForegroundService(
            this,
            Intent(this, HAListenerService::class.java).setAction(HAListenerService.ACTION_RELOAD)
        )
        status.text = "Saved. Listener is reloading with the current Home Assistant config."
    }

    private fun openOverlaySettings() {
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ))
    }

    private fun startSetupServer() {
        val url = setupServer?.start().orEmpty()
        setupUrl.text = url
        qrImage.setImageBitmap(Qr.bitmap(url, 300))
    }

    private fun scan() {
        status.text = "Scanning local subnet for Home Assistant..."
        Discovery.scan(this) { results ->
            runOnUiThread {
                if (results.isNotEmpty()) {
                    urlInput.setText(results.first())
                    status.text = "Found: ${results.joinToString()}"
                } else {
                    status.text = "No Home Assistant found on this subnet"
                }
            }
        }
    }

    private fun testToast(fullscreen: Boolean) {
        val cam = "camera.test"
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        OverlayToastManager(this).show(cam, if (fullscreen) 8 else 12, fullscreen)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "havideotoasttv") return
        val haUrl = data.getQueryParameter("ha_url").orEmpty()
        val token = data.getQueryParameter("token").orEmpty()
        if (haUrl.isNotBlank() || token.isNotBlank()) {
            val old = ConfigStore.load(this)
            ConfigStore.save(this, old.copy(
                haUrl = if (haUrl.isNotBlank()) haUrl else old.haUrl,
                token = if (token.isNotBlank()) token else old.token
            ))
            loadConfig()
            startServiceNow()
        }
    }
}
