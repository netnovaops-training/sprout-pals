package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Stage
import kotlin.math.roundToInt

/** Where a character ended up on screen, in screen units, for placing hats, stars and bubbles. */
data class CharBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val centerX get() = left + width / 2
}

/** Old age: the adult picture, a little greyer. */
private val OLD_AGE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.45f) })

/**
 * Draws a character picture standing on [ground], centred on [centerX] (both in screen units),
 * with [unitsPerPixel] screen units per picture pixel. Pixels stay crisp (no smoothing).
 * [faceLeft] turns animals around; people always face the viewer.
 */
fun DrawScope.character(
    species: Species,
    stage: Stage,
    centerX: Float,
    ground: Float,
    unitPx: Float,
    unitsPerPixel: Float,
    faceLeft: Boolean = false,
): CharBox {
    val img = CharacterArt.of(species, stage)
    val w = img.width * unitsPerPixel
    val h = img.height * unitsPerPixel
    val left = centerX - w / 2
    val top = ground - h
    val dstOffset = IntOffset((left * unitPx).roundToInt(), (top * unitPx).roundToInt())
    val dstSize = IntSize((w * unitPx).roundToInt(), (h * unitPx).roundToInt())
    val flip = CharacterArt.flips(species) && faceLeft != CharacterArt.facesLeft(species)
    val filter = if (stage == Stage.OLD) OLD_AGE else null
    if (flip) {
        scale(-1f, 1f, pivot = Offset((left + w / 2) * unitPx, (top + h / 2) * unitPx)) {
            drawImage(img, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.None, colorFilter = filter)
        }
    } else {
        drawImage(img, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.None, colorFilter = filter)
    }
    return CharBox(left, top, w, h)
}
