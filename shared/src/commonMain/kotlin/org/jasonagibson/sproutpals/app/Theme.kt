package org.jasonagibson.sproutpals.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * The background music: an original adventure theme in the style of 90s handheld RPGs
 * (written for Sprout Pals - no borrowed melodies). Four channels like the old hardware:
 * two square-wave leads (thin 12.5% lead with vibrato, 25% harmony stabs), a triangle bass
 * and a noise drum kit. A two-bar fanfare plays once, then the 16-bar march loops.
 *
 * Notes are (midi, length in 16th steps); 0 = rest. One step = [STEP_MS].
 */
object Theme {
    private const val STEP_MS = 100   // a 16th note: 150 beats a minute
    private const val BAR = 16

    // G major. Chords per bar, written as the bass root and the chord's third (for the stabs).
    private const val G = 43; private const val E = 40; private const val C = 48; private const val D = 50
    private const val B = 47; private const val A = 45

    private val FANFARE = listOf(
        67 to 2, 67 to 2, 67 to 2, 74 to 10,
        72 to 2, 71 to 2, 69 to 2, 79 to 10,
    )
    private val VERSE = listOf(
        79 to 4, 74 to 2, 71 to 2, 74 to 4, 79 to 4,
        78 to 4, 76 to 2, 74 to 2, 71 to 6, 0 to 2,
        72 to 2, 74 to 2, 76 to 4, 79 to 4, 76 to 4,
        74 to 6, 78 to 2, 81 to 8,
        79 to 4, 81 to 2, 83 to 2, 86 to 4, 83 to 4,
        81 to 2, 79 to 2, 78 to 4, 76 to 4, 79 to 4,
        76 to 4, 72 to 4, 74 to 4, 78 to 4,
        79 to 12, 0 to 4,
    )
    private val BRIDGE = listOf(
        76 to 2, 76 to 2, 0 to 2, 76 to 2, 79 to 4, 76 to 4,
        74 to 2, 74 to 2, 0 to 2, 74 to 2, 78 to 4, 74 to 4,
        71 to 4, 74 to 4, 78 to 4, 83 to 4,
        79 to 8, 76 to 8,
        72 to 2, 76 to 2, 79 to 2, 84 to 6, 83 to 2, 81 to 2,
        81 to 4, 78 to 4, 74 to 4, 78 to 4,
        76 to 4, 81 to 4, 78 to 4, 74 to 4,
        79 to 4, 74 to 2, 79 to 2, 83 to 8,
    )
    /** One chord per half bar: (bass root, third for the harmony stab). */
    private val CHORDS_FANFARE = listOf(G to 71, G to 71, D to 66, G to 71)
    private val CHORDS_LOOP = listOf(
        G to 71, G to 71, E to 67, E to 67, C to 64, C to 64, D to 66, D to 66,
        G to 71, G to 71, E to 67, E to 67, C to 64, D to 66, G to 71, G to 71,
        C to 64, C to 64, D to 66, D to 66, B to 62, B to 62, E to 67, E to 67,
        C to 64, C to 64, D to 66, D to 66, A to 60, D to 66, G to 71, G to 71,
    )

    /** The whole piece as 16-bit samples, and the sample where the loop starts (after the fanfare). */
    fun render(rate: Int): Pair<ShortArray, Int> {
        val stepLen = rate * STEP_MS / 1000
        val fanfareSteps = 2 * BAR
        val totalSteps = fanfareSteps + 16 * BAR
        val out = DoubleArray(totalSteps * stepLen)

        // lead: thin square with a little vibrato on long notes
        var step = 0
        for ((n, len) in FANFARE + VERSE + BRIDGE) {
            if (n > 0) square(out, step * stepLen, len * stepLen, n, duty = 0.125, vol = 0.15, rate = rate, vibrato = len >= 6)
            step += len
        }
        // harmony stabs on the off-beats, and the bass bouncing root-octave in eighths
        (CHORDS_FANFARE + CHORDS_LOOP).forEachIndexed { half, (root, third) ->
            val s0 = half * BAR / 2
            for (k in 0 until 4) {
                val at = s0 + k * 2
                triangle(out, at * stepLen, 2 * stepLen, if (k % 2 == 0) root else root + 12, vol = 0.22, rate = rate)
            }
            for (k in listOf(2, 6)) square(out, (s0 + k) * stepLen, stepLen + stepLen / 2, third, duty = 0.25, vol = 0.07, rate = rate)
        }
        // drums: kick on 1 and 3, snare on 2 and 4, soft hats on the eighths (skipped in the fanfare's long notes)
        var seed = 0x7ACE
        fun noise(at: Int, len: Int, vol: Double, decay: Double) {
            for (i in 0 until len) {
                if (at + i >= out.size) break
                seed = (seed shr 1) xor (-(seed and 1) and 0xB400)
                val env = (1.0 - i.toDouble() / len).pow(decay)
                out[at + i] += (if (seed and 1 == 1) 1.0 else -1.0) * vol * env
            }
        }
        for (s in 0 until totalSteps) {
            val inBar = s % BAR
            if (s < fanfareSteps && inBar !in listOf(0, 8)) continue
            when (inBar) {
                0, 8 -> kick(out, s * stepLen, rate)
                4, 12 -> noise(s * stepLen, stepLen, 0.10, 2.0)
            }
            if (s >= fanfareSteps && inBar % 2 == 0) noise(s * stepLen, stepLen / 4, 0.03, 3.0)
        }
        val pcm = ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
        return pcm to fanfareSteps * stepLen
    }

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun square(out: DoubleArray, at: Int, len: Int, midi: Int, duty: Double, vol: Double, rate: Int, vibrato: Boolean = false) {
        var phase = 0.0
        val gate = (len * 0.9).toInt()   // a tiny gap so repeated notes stay separate
        for (i in 0 until gate) {
            if (at + i >= out.size) break
            val t = i.toDouble() / rate
            val bend = if (vibrato && t > 0.15) 2.0.pow(0.25 * sin(2 * PI * 6 * t) / 12) else 1.0
            phase = (phase + hz(midi) * bend / rate) % 1.0
            val env = if (i < gate - 200) 1.0 - 0.35 * i / gate else (gate - i) / 200.0
            out[at + i] += (if (phase < duty) 1.0 else -1.0) * vol * env
        }
    }

    private fun triangle(out: DoubleArray, at: Int, len: Int, midi: Int, vol: Double, rate: Int) {
        val gate = (len * 0.85).toInt()
        for (i in 0 until gate) {
            if (at + i >= out.size) break
            val p = (i * hz(midi) / rate) % 1.0
            val env = if (i < gate - 150) 1.0 else (gate - i) / 150.0
            out[at + i] += (4 * abs(p - 0.5) - 1) * vol * env
        }
    }

    /** A thump: a quick downward pitch sweep. */
    private fun kick(out: DoubleArray, at: Int, rate: Int) {
        val len = rate / 12
        var phase = 0.0
        for (i in 0 until len) {
            if (at + i >= out.size) break
            val f = 140.0 - 100.0 * i / len
            phase += f / rate
            out[at + i] += sin(2 * PI * phase) * 0.35 * (1.0 - i.toDouble() / len)
        }
    }
}
