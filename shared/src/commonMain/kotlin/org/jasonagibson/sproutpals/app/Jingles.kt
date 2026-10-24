package org.jasonagibson.sproutpals.app

import kotlin.math.pow

/**
 * The button beeps and little tunes, as 8-bit square waves computed in code (no audio files), the same
 * on both phones: this makes the samples, each phone just plays them (Android AudioTrack, iPhone
 * AVAudioEngine). All tunes are original, written for Sprout Pals.
 */
object Jingles {
    /** (midi note, milliseconds) per sound; 0 = a short rest. */
    fun notes(sfx: Sfx): List<Pair<Int, Int>> = when (sfx) {
        Sfx.A -> listOf(81 to 45)                              // short high blip
        Sfx.B -> listOf(76 to 40, 84 to 60)                    // "bwip" up: choose
        Sfx.C -> listOf(72 to 40, 64 to 60)                    // "boop" down: back
        Sfx.SHOP -> listOf(84 to 50, 88 to 50, 91 to 90)       // ka-ching
        Sfx.HATCH -> listOf(72 to 90, 76 to 90, 79 to 90, 84 to 240)
        Sfx.ARRIVE -> listOf(79 to 80, 84 to 80, 88 to 80, 91 to 80, 96 to 300)
        // achievement: a quick bright arpeggio that lands and rings
        Sfx.ACHIEVE -> listOf(72 to 70, 76 to 70, 79 to 70, 84 to 90, 0 to 40, 88 to 360)
        // key item: slower and grander, rising then resolving home
        Sfx.KEY_ITEM -> listOf(67 to 130, 72 to 130, 76 to 130, 79 to 220, 77 to 110, 81 to 110, 84 to 560)
    }

    /** 16-bit mono samples for [sfx] at [rate] samples a second. */
    fun pcm(sfx: Sfx, rate: Int): ShortArray {
        val notes = notes(sfx)
        val out = DoubleArray(rate * notes.sumOf { it.second } / 1000)
        var start = 0
        for ((midi, ms) in notes) {
            val len = rate * ms / 1000
            if (midi > 0) {
                val period = rate / (440.0 * 2.0.pow((midi - 69) / 12.0))
                for (i in 0 until len) {
                    if (start + i >= out.size) break
                    val phase = (i % period) / period
                    val env = 1.0 - (i.toDouble() / len) * 0.7          // a quick fade so notes don't click
                    out[start + i] += (if (phase < 0.25) 1.0 else -1.0) * 0.30 * env
                }
            }
            start += len
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }
}
