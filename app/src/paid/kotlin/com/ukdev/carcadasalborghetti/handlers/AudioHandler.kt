package com.ukdev.carcadasalborghetti.handlers

import android.media.MediaPlayer
import android.net.Uri
import com.ukdev.carcadasalborghetti.listeners.MediaCallback
import com.ukdev.carcadasalborghetti.view.ViewLayer

class AudioHandler(callback: MediaCallback, view: ViewLayer) : PaidMediaHandler(callback, view) {

    private var mediaPlayer: MediaPlayer? = null

    override fun stop() {
        mediaPlayer?.stop()
        callback.onStopPlayback()
    }

    override fun isPlaying() = mediaPlayer?.isPlaying ?: false

    override fun onLinkReady(link: String, title: String) {
        initialiseMediaPlayer(link)
    }

    private fun initialiseMediaPlayer(mediaLink: String) {
        mediaPlayer?.release()
        mediaPlayer = createMediaPlayer(Uri.parse(mediaLink))
    }

}