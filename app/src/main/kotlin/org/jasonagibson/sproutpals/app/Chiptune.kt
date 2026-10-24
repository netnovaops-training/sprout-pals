package org.jasonagibson.sproutpals.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors

/**
 * Plays the game's 8-bit sound on Android. The samples themselves are made by the shared code
 * (Jingles for the beeps, Theme for the music), so the iPhone sounds exactly the same.
 */
class Chiptune {
    private val rate = 22_050
    private val worker = Executors.newSingleThreadExecutor()
    private var music: AudioTrack? = null

    fun play(sfx: Sfx) {
        worker.execute {
            val pcm = Jingles.pcm(sfx, rate)
            AudioTrack(attrs(), format(), pcm.size * 2, AudioTrack.MODE_STATIC, 0).apply {
                write(pcm, 0, pcm.size)
                setNotificationMarkerPosition(pcm.size - 1)
                setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) = t.release()
                    override fun onPeriodicNotification(t: AudioTrack) = Unit
                })
                play()
            }
        }
    }

    fun startMusic() {
        if (music != null) return
        worker.execute {
            if (music != null) return@execute
            val (pcm, loopFrom) = Theme.render(rate)
            music = AudioTrack(attrs(), format(), pcm.size * 2, AudioTrack.MODE_STATIC, 0).apply {
                write(pcm, 0, pcm.size)
                setLoopPoints(loopFrom, pcm.size, -1)    // fanfare once, then loop the rest forever
                play()
            }
        }
    }

    fun stopMusic() {
        worker.execute {
            music?.run { stop(); release() }
            music = null
        }
    }

    private fun attrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun format() = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(rate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()
}
