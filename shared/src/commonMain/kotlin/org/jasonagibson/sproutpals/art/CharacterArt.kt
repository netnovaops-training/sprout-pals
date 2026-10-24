package org.jasonagibson.sproutpals.art

import androidx.compose.ui.graphics.ImageBitmap
import org.jasonagibson.sproutpals.engine.Kind
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Stage

/**
 * The characters, as pictures cut from Jason's character sheet (see art/make_sheet_sprites.py).
 *
 * Files are res/drawable-nodpi/char_<name>_<stage>_<pose>.png: 4 life stages x 7 poses.
 * Every character only ever uses its own files, so nobody can turn into someone else.
 *
 * Poses (all face right as drawn):
 *   people: 0 stand, 1 wave, 2 big wave/cheer, 3 step, 4 landing squash, 5 jump stretch, 6 sick
 *   dogs:   0 stand, 1 walk, 2 trot, 3 run,     4 landing squash, 5 jump stretch, 6 sick
 *   cats:   0 sit,   1 walk, 2 leap, 3 meow,    4 landing squash, 5 jump stretch, 6 sick
 */
object CharacterArt {
    const val POSES = 7

    private val cache = HashMap<String, ImageBitmap>()

    /**
     * Turns a picture name ("char_kona_adult_2") into the picture. Each phone sets this once at startup:
     * Android reads res/drawable-nodpi, the iPhone reads the PNG files bundled with the app.
     */
    lateinit var loader: (String) -> ImageBitmap

    private fun stageName(stage: Stage) = when (stage) {
        Stage.BABY -> "baby"; Stage.KID -> "kid"; Stage.OLD -> "old"; else -> "adult"
    }

    fun of(species: Species, stage: Stage, pose: Int = 0): ImageBitmap {
        val name = "char_${species.name.lowercase()}_${stageName(stage)}_${pose.coerceIn(0, POSES - 1)}"
        return cache.getOrPut(name) { loader(name) }
    }

    /** All sprites face right; anyone can be turned around to walk left. */
    fun facesLeft(@Suppress("UNUSED_PARAMETER") species: Species) = false

    fun flips(@Suppress("UNUSED_PARAMETER") species: Species) = true

    /** The pose to use for a kind of action, by kind of character. */
    object Pose {
        const val STAND = 0
        const val LAND = 4
        const val JUMP = 5
        const val SICK = 6

        /** Fully cared for: people clap (cheer and wave in turn), dogs bound, cats meow. */
        fun clap(k: Kind, f: Int) = when (k) { Kind.HUMAN -> if ((f / 2) % 2 == 0) 2 else 1; Kind.CAT -> if ((f / 4) % 2 == 0) 3 else 0; Kind.DOG -> if ((f / 2) % 2 == 0) 3 else 2 }
        fun wave(k: Kind, f: Int) = when (k) { Kind.HUMAN -> if ((f / 3) % 2 == 0) 1 else 2; Kind.CAT -> 3; Kind.DOG -> if ((f / 3) % 2 == 0) 0 else 1 }
        fun walk(k: Kind, f: Int) = when (k) { Kind.HUMAN -> if ((f / 2) % 2 == 0) 3 else 0; Kind.CAT -> 1; Kind.DOG -> if ((f / 2) % 2 == 0) 1 else 2 }
        fun run(k: Kind, f: Int) = when (k) { Kind.HUMAN -> if ((f / 2) % 2 == 0) 3 else 0; Kind.CAT -> if ((f / 2) % 2 == 0) 1 else 2; Kind.DOG -> if ((f / 2) % 2 == 0) 2 else 3 }
        fun cheer(k: Kind) = when (k) { Kind.HUMAN -> 2; Kind.CAT -> 3; Kind.DOG -> 3 }
    }
}
