package com.vessosa.havideotoasttv

sealed class ToastRequest {
    abstract val duration: Int

    data class Video(
        val camera: String,
        override val duration: Int,
        val fullscreen: Boolean
    ) : ToastRequest()

    data class Message(
        val title: String,
        val message: String,
        override val duration: Int,
        val level: Level
    ) : ToastRequest() {
        enum class Level {
            INFO,
            SUCCESS,
            WARNING,
            ERROR
        }
    }
}
