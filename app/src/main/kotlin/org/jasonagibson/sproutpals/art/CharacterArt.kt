package org.jasonagibson.sproutpals.art

import android.content.res.Resources
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import org.jasonagibson.sproutpals.R
import org.jasonagibson.sproutpals.engine.Kind
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Stage

/**
 * The characters, as pictures cut from Jason's reference art (see art/make_sprites.py).
 *
 * Every character has exactly one picture per life stage, made by shrinking the same cutout,
 * so a character can never turn into someone else between frames - the problem with the AI video.
 * Old age reuses the adult picture, drawn a little greyer (see [Stage.OLD] handling in the screen code).
 */
object CharacterArt {
    private val ids: Map<Pair<Species, String>, Int> = mapOf(
        (Species.JUN to "baby") to R.drawable.char_jun_baby,
        (Species.JUN to "kid") to R.drawable.char_jun_kid,
        (Species.JUN to "adult") to R.drawable.char_jun_adult,
        (Species.KONA to "baby") to R.drawable.char_kona_baby,
        (Species.KONA to "kid") to R.drawable.char_kona_kid,
        (Species.KONA to "adult") to R.drawable.char_kona_adult,
        (Species.JASON to "baby") to R.drawable.char_jason_baby,
        (Species.JASON to "kid") to R.drawable.char_jason_kid,
        (Species.JASON to "adult") to R.drawable.char_jason_adult,
        (Species.RUSTY to "baby") to R.drawable.char_rusty_baby,
        (Species.RUSTY to "kid") to R.drawable.char_rusty_kid,
        (Species.RUSTY to "adult") to R.drawable.char_rusty_adult,
        (Species.KARA to "baby") to R.drawable.char_kara_baby,
        (Species.KARA to "kid") to R.drawable.char_kara_kid,
        (Species.KARA to "adult") to R.drawable.char_kara_adult,
        (Species.DJ to "baby") to R.drawable.char_dj_baby,
        (Species.DJ to "kid") to R.drawable.char_dj_kid,
        (Species.DJ to "adult") to R.drawable.char_dj_adult,
        (Species.MAUI to "baby") to R.drawable.char_maui_baby,
        (Species.MAUI to "kid") to R.drawable.char_maui_kid,
        (Species.MAUI to "adult") to R.drawable.char_maui_adult,
    )
    private val cache = HashMap<Int, ImageBitmap>()
    private lateinit var res: Resources

    fun init(resources: Resources) { res = resources }

    fun of(species: Species, stage: Stage): ImageBitmap {
        val key = when (stage) { Stage.BABY -> "baby"; Stage.KID -> "kid"; else -> "adult" }
        val id = ids.getValue(species to key)
        return cache.getOrPut(id) { ImageBitmap.imageResource(res, id) }
    }

    /**
     * Which way the picture faces as drawn in the reference. People face the viewer (never flipped);
     * Rusty runs right; Kara, Maui and DJ look left.
     */
    fun facesLeft(species: Species) = species in setOf(Species.KARA, Species.MAUI, Species.DJ)

    fun flips(species: Species) = species.kind != Kind.HUMAN
}
