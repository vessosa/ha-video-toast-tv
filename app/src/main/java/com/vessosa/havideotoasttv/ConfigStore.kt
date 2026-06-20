package com.vessosa.havideotoasttv

import android.content.Context

object ConfigStore {
    private const val PREFS = "ha_video_toast_tv"

    data class Config(
        val haUrl: String,
        val token: String,
        val width: Int = 0,
        val height: Int = 0,
        val duration: Int = 15,
        val gap: Int = 12,
        val margin: Int = 36,
        val maxToasts: Int = 4,
        val corner: String = "bottom-right"
    ) {
        val isConfigured: Boolean get() = haUrl.isNotBlank() && token.isNotBlank()
    }

    fun load(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            haUrl = p.getString("ha_url", "") ?: "",
            token = p.getString("token", "") ?: "",
            width = 0,
            height = 0,
            duration = p.getInt("duration", 15),
            gap = p.getInt("gap", 12),
            margin = p.getInt("margin", 36),
            maxToasts = p.getInt("max_toasts", 4),
            corner = p.getString("corner", "bottom-right") ?: "bottom-right"
        )
    }

    fun save(context: Context, config: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ha_url", config.haUrl.trim().trimEnd('/'))
            .putString("token", config.token.trim())
            .putInt("width", config.width)
            .putInt("height", config.height)
            .putInt("duration", config.duration)
            .putInt("gap", config.gap)
            .putInt("margin", config.margin)
            .putInt("max_toasts", config.maxToasts)
            .putString("corner", config.corner)
            .apply()
    }
}
