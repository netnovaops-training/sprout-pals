package org.jasonagibson.sproutpals.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.pow

/**
 * 8-bit sound made the way 90s handhelds did it: square waves computed in code.
 * No audio files - every beep and the background tune are generated on the fly.
 * The tune is original (written for Sprout Pals), so there's nothing to license.
 */
class Chiptune {
    private val rate = 22_050
    private val worker = Executors.newSingleThreadExecutor()
    private var music: AudioTrack? = null

    enum class Sfx { A, B, C, SHOP, HATCH, ARRIVE }

    fun play(sfx: Sfx) {
        val notes: List<Pair<Int, Int>> = when (sfx) {           // (midi note, milliseconds)
            Sfx.A -> listOf(81 to 45)                              // short high blip
            Sfx.B -> listOf(76 to 40, 84 to 60)                    // "bwip" up: choose
            Sfx.C -> listOf(72 to 40, 64 to 60)                    // "boop" down: back
            Sfx.SHOP -> listOf(84 to 50, 88 to 50, 91 to 90)       // ka-ching
            Sfx.HATCH -> listOf(72 to 90, 76 to 90, 79 to 90, 84 to 240)
            Sfx.ARRIVE -> listOf(79 to 80, 84 to 80, 88 to 80, 91 to 80, 96 to 300)
        }
        worker.execute {
            val pcm = render(notes.map { (n, ms) -> Voice(n, ms, 0.25, 0.30) }, sequential = true)
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
            val pcm = song()
            music = AudioTrack(attrs(), format(), pcm.size * 2, AudioTrack.MODE_STATIC, 0).apply {
                write(pcm, 0, pcm.size)
                setLoopPoints(0, pcm.size, -1)    // loop forever
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

    // ---------- synthesis ----------

    private data class Voice(val midi: Int, val ms: Int, val duty: Double, val volume: Double)

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Square wave with a quick fade-out per note, so notes don't click. */
    private fun render(voices: List<Voice>, sequential: Boolean): ShortArray {
        val total = if (sequential) voices.sumOf { it.ms } else voices.maxOf { it.ms }
        val out = DoubleArray(rate * total / 1000)
        var start = 0
        for (v in voices) {
            val len = rate * v.ms / 1000
            if (v.midi > 0) {
                val period = rate / hz(v.midi)
                for (i in 0 until len) {
                    if (start + i >= out.size) break
                    val phase = (i % period) / period
                    val env = 1.0 - (i.toDouble() / len) * 0.7
                    out[start + i] += (if (phase < v.duty) 1.0 else -1.0) * v.volume * env
                }
            }
            if (sequential) start += len
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }

    /** The background loop: a cheerful garden tune, melody over a bouncing bass. ~13 seconds. */
    private fun song(): ShortArray {
        val eighth = 200 // ms, about 150 beats per minute
        val melody = listOf(
            72, 76, 79, 76, 77, 81, 79, 0,
            76, 79, 84, 79, 77, 76, 74, 0,
            72, 74, 76, 72, 79, 77, 76, 74,
            72, 76, 74, 71, 72, 0, 72, 0,
            76, 77, 79, 0, 81, 79, 77, 76,
            74, 76, 77, 0, 79, 77, 76, 74,
            72, 76, 79, 84, 83, 79, 77, 74,
            72, 0, 67, 71, 72, 0, 0, 0,
        )
        val bassBars = listOf(
            listOf(48, 55, 48, 55), listOf(53, 60, 53, 60), listOf(48, 55, 48, 55), listOf(55, 50, 55, 48),
            listOf(53, 60, 53, 60), listOf(50, 57, 55, 59), listOf(48, 55, 53, 55), listOf(48, 43, 48, 48),
        )
        val lead = render(melody.map { Voice(it, eighth, 0.25, 0.16) }, sequential = true)
        val bass = render(bassBars.flatten().map { Voice(it, eighth * 2, 0.5, 0.10) }, sequential = true)
        return ShortArray(minOf(lead.size, bass.size)) { (lead[it] + bass[it]).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
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
