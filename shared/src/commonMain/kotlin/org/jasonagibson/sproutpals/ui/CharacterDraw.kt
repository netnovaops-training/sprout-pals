package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Stage
import kotlin.math.roundToInt

/**
 * Where a character ended up on screen, in screen units, for placing hats, stars and bubbles.
 * [headX]/[headTop] = centre and top of the head, read from the picture itself.
 */
data class CharBox(
    val left: Float, val top: Float, val width: Float, val height: Float,
    val headX: Float = left + width / 2, val headTop: Float = top,
) {
    val right get() = left + width
    val centerX get() = left + width / 2
}

/** Head of a picture: first opaque row, and the middle of the opaque pixels in the top fifth. */
private val heads = HashMap<ImageBitmap, Pair<Float, Int>>()

private fun headOf(img: ImageBitmap): Pair<Float, Int> = heads.getOrPut(img) {
    val pm = img.toPixelMap()
    val top = (0 until img.height).firstOrNull { y -> (0 until img.width).any { x -> pm[x, y].alpha > 0.5f } } ?: 0
    val rows = top until (top + img.height / 5).coerceAtMost(img.height)
    var sum = 0f; var n = 0
    for (y in rows) for (x in 0 until img.width) if (pm[x, y].alpha > 0.5f) { sum += x; n++ }
    (if (n > 0) (sum / n + 0.5f) / img.width else 0.5f) to top
}

/**
 * Draws a character picture standing on [ground], centred on [centerX] (both in screen units),
 * with [unitsPerPixel] screen units per picture pixel. Pixels stay crisp (no smoothing).
 * [faceLeft] turns the character around; [pose] picks the picture (see [CharacterArt]).
 * [layers] are extra same-size pictures drawn over it in the same place (clothes).
 */
fun DrawScope.character(
    species: Species,
    stage: Stage,
    centerX: Float,
    ground: Float,
    unitPx: Float,
    unitsPerPixel: Float,
    faceLeft: Boolean = false,
    pose: Int = 0,
    layers: (ImageBitmap) -> List<ImageBitmap> = { emptyList() },
): CharBox {
    val img = CharacterArt.of(species, stage, pose)
    val w = img.width * unitsPerPixel
    val h = img.height * unitsPerPixel
    val left = centerX - w / 2
    val top = ground - h
    val dstOffset = IntOffset((left * unitPx).roundToInt(), (top * unitPx).roundToInt())
    val dstSize = IntSize((w * unitPx).roundToInt(), (h * unitPx).roundToInt())
    val flip = CharacterArt.flips(species) && faceLeft != CharacterArt.facesLeft(species)
    val filter: ColorFilter? = null   // old age has its own pictures now (grey hair, wrinkles)
    val pictures = listOf(img) + layers(img)
    if (flip) {
        scale(-1f, 1f, pivot = Offset((left + w / 2) * unitPx, (top + h / 2) * unitPx)) {
            pictures.forEach { drawImage(it, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.None, colorFilter = filter) }
        }
    } else {
        pictures.forEach { drawImage(it, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.None, colorFilter = filter) }
    }
    val (hx, hy) = headOf(img)
    val headFrac = if (flip) 1f - hx else hx
    return CharBox(left, top, w, h, headX = left + w * headFrac, headTop = top + hy * unitsPerPixel)
}
