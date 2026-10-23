package org.jasonagibson.sproutpals.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import org.jasonagibson.sproutpals.art.Grid
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.engine.Shop
import org.jasonagibson.sproutpals.engine.Slot
import org.jasonagibson.sproutpals.engine.Stage

/*
 * Shop outfits. Nothing here is drawn per character, so a character added later wears all of it.
 * - Hats: a small grid sat on the head (found from the picture: CharBox.headX / headTop).
 * - Shirts and pants (people only): the character's own clothes, recoloured. In the chest band
 *   and the leg band we find the main clothing colour and repaint every pixel close to it, keeping
 *   its light and shade - so the new shirt follows waving arms and the pants follow walking legs,
 *   while skin, hair, scarves, outlines and shoes stay as they were.
 */
private class Hat(val grid: Grid, val pal: Map<Char, Long>, val sink: Int = 3)

private val HATS = mapOf(
    "flower_crown" to Hat(
        listOf(".P.Y.P.W.P.", "PYPGYPGWPYP", "GGGGGGGGGGG"),
        mapOf('P' to 0xFFF27AA8L, 'Y' to 0xFFFFD84AL, 'W' to 0xFFFFFFFFL, 'G' to 0xFF4E9E3AL),
        sink = 2,
    ),
    "bow" to Hat(
        listOf("PP...PP", "PPPKPPP", "PPpKpPP", "PP...PP"),
        mapOf('P' to 0xFFF27AA8L, 'p' to 0xFFC4507EL, 'K' to 0xFFE0344BL),
        sink = 1,
    ),
    "beanie" to Hat(
        listOf("....WW....", "...RRRR...", "..RRRRRR..", ".RRRRRRRR.", "WWWWWWWWWW", "wWwWwWwWwW"),
        mapOf('R' to 0xFF3C7DD9L, 'W' to 0xFFF4EBDCL, 'w' to 0xFFC9BFB0L),
    ),
    "sun_hat" to Hat(
        listOf("....SSSSS....", "...SSSSSSS...", "...RRRRRRR...", "SSSSSSSSSSSSS", ".sssssssssss."),
        mapOf('S' to 0xFFF0D27AL, 's' to 0xFFC9A852L, 'R' to 0xFFE0344BL),
    ),
    "cowboy_hat" to Hat(
        listOf("...B.....B...", "...BBBBBBB...", "...BBbBbBB...", "...YYYYYYY...", "B..BBBBBBB..B", ".BBBBBBBBBBB."),
        mapOf('B' to 0xFF8A5A2EL, 'b' to 0xFF6A4220L, 'Y' to 0xFFE8B923L),
    ),
    "crown" to Hat(
        listOf("Y..Y..Y..Y", "YY.YRYY.YY", "YYYYYYYYYY", "YBYYRYYBYY", "yyyyyyyyyy"),
        mapOf('Y' to 0xFFFFD84AL, 'y' to 0xFFC99A1EL, 'R' to 0xFFE0344BL, 'B' to 0xFF3C7DD9L),
        sink = 2,
    ),
)

/** A shop outfit's picture in a 24x24-unit box: the hat, or a little shirt or pair of pants. */
fun DrawScope.drawOutfitPreview(id: String, u: Float) {
    // drawn at double size (u2) in a 12x12 grid
    val u2 = u * 2
    HATS[id]?.let { hat ->
        val w = hat.grid.maxOf { it.length }
        shaded(hat.grid, hat.pal, (12 - w) / 2, (12 - hat.grid.size) / 2, u2)
        return
    }
    val cloth = CLOTHES[id]
    if (cloth == null) {   // the mystery item
        shaded(listOf(".QQQ.", "Q...Q", "...Q.", "..Q..", ".....", "..Q.."), mapOf('Q' to 0xFFE8B923L), 3, 3, u2)
        return
    }
    val shape = if (Shop.byId(id)?.slot == Slot.PANTS) listOf("CCCCCCCC", "CCCCCCCC", "CCC..CCC", "CCC..CCC", "CCC..CCC", "CCC..CCC")
        else listOf("CCC..CCC", "CCCCCCCC", "CCCCCCCC", ".CCCCCC.", ".CCCCCC.", ".CCCCCC.")
    shape.forEachIndexed { y, row -> row.forEachIndexed { x, ch ->
        if (ch != 'C') return@forEachIndexed
        var c = cloth.color
        if (cloth.pattern == Pattern.STRIPES && y % 2 == 1) c = cloth.second
        if (cloth.pattern == Pattern.CHECK && (x + y) % 2 == 0) c = darker(c)
        block(2 + x, 3 + y, 1, 1, c, u2)
    } }
}

/** Draws the worn hat (if any) on the head. */
fun DrawScope.drawHat(save: Save, box: CharBox, px: Float) {
    val hat = HATS[Shop.worn(save, Slot.HAT) ?: return] ?: return
    val w = hat.grid.maxOf { it.length }
    shaded(hat.grid, hat.pal, box.headX.toInt() - w / 2, box.headTop.toInt() - hat.grid.size + hat.sink, px)
}

private enum class Pattern { PLAIN, CHECK, STRIPES }
private class Cloth(val color: Long, val pattern: Pattern = Pattern.PLAIN, val second: Long = 0)

private val CLOTHES = mapOf(
    "green_tee" to Cloth(0xFF4E9E3AL),
    "flannel" to Cloth(0xFFC8323CL, Pattern.CHECK),
    "raincoat" to Cloth(0xFFF5C82EL),
    "sweater" to Cloth(0xFF3C7DD9L, Pattern.STRIPES, 0xFFF4EBDCL),
    "jeans" to Cloth(0xFF3A5A9AL),
    "work_pants" to Cloth(0xFF8A6A42L),
)

/** Chest band and leg band per life stage, as fractions of the picture height (top = 0). */
private fun bands(stage: Stage): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> = when (stage) {
    // chest bands start just under the mouth, so lips never pick up the shirt colour
    Stage.BABY -> 0.63f..0.85f to 0.85f..0.95f
    Stage.KID -> 0.51f..0.79f to 0.79f..0.94f
    else -> 0.43f..0.76f to 0.76f..0.94f
}

private val layerCache = HashMap<Triple<ImageBitmap, String, Stage>, ImageBitmap>()

/** The recoloured shirt and pants layers for this picture (empty if nothing is worn). */
fun clothesLayers(img: ImageBitmap, stage: Stage, save: Save): List<ImageBitmap> {
    val (chest, legs) = bands(stage)
    return listOfNotNull(
        Shop.worn(save, Slot.SHIRT)?.let { id -> CLOTHES[id]?.let { layerCache.getOrPut(Triple(img, id, stage)) { recolor(img, chest, it) } } },
        Shop.worn(save, Slot.PANTS)?.let { id -> CLOTHES[id]?.let { layerCache.getOrPut(Triple(img, id, stage)) { recolor(img, legs, it) } } },
    )
}

/** The same colour at 72% brightness (the dark squares of a check). */
private fun darker(c: Long): Long {
    fun ch(shift: Int) = (((c shr shift) and 0xFF) * 72 / 100) shl shift
    return 0xFF000000L or ch(16) or ch(8) or ch(0)
}

private fun r(c: Int) = (c shr 16) and 0xFF
private fun g(c: Int) = (c shr 8) and 0xFF
private fun b(c: Int) = c and 0xFF
private fun lum(c: Int) = (0.3f * r(c) + 0.59f * g(c) + 0.11f * b(c)) / 255f
private fun skin(c: Int) = r(c) > g(c) && g(c) > b(c) && r(c) - b(c) >= 40 && g(c) - b(c) >= 12
private fun dist(a: Int, c: Int): Int { val dr = r(a) - r(c); val dg = g(a) - g(c); val db = b(a) - b(c); return dr * dr + dg * dg + db * db }

private fun recolor(img: ImageBitmap, band: ClosedFloatingPointRange<Float>, cloth: Cloth): ImageBitmap {
    val pm = img.toPixelMap()
    val w = img.width; val h = img.height
    val y0 = (band.start * h).toInt(); val y1 = (band.endInclusive * h).toInt().coerceAtMost(h)
    val src = IntArray(w * h) { pm[it % w, it / w].toArgb() }
    fun usable(c: Int) = (c ushr 24) > 128 && lum(c) > 0.16f && !skin(c)

    // main clothing colour = the biggest group of similar colours in the band
    val buckets = HashMap<Int, MutableList<Int>>()
    for (y in y0 until y1) for (x in 0 until w) {
        val c = src[y * w + x]
        if (usable(c)) buckets.getOrPut((r(c) / 48 shl 8) or (g(c) / 48 shl 4) or (b(c) / 48)) { mutableListOf() }.add(c)
    }
    val main = buckets.values.maxByOrNull { it.size } ?: return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    val mr = main.map(::r).average().toInt(); val mg = main.map(::g).average().toInt(); val mb = main.map(::b).average().toInt()
    val mainColor = (0xFF shl 24) or (mr shl 16) or (mg shl 8) or mb
    val mainLum = lum(mainColor).coerceAtLeast(0.2f)

    val out = IntArray(w * h)
    for (y in y0 until y1) for (x in 0 until w) {
        val c = src[y * w + x]
        if (!usable(c) || dist(c, mainColor) > 75 * 75) continue
        val base = when {
            cloth.pattern == Pattern.STRIPES && (y / 2) % 2 == 1 -> cloth.second.toInt()
            else -> cloth.color.toInt()
        }
        var shade = (lum(c) / mainLum).coerceIn(0.55f, 1.35f)
        if (cloth.pattern == Pattern.CHECK && ((x / 2) + (y / 2)) % 2 == 0) shade *= 0.72f
        fun ch(v: Int) = (v * shade).toInt().coerceIn(0, 255)
        out[y * w + x] = (0xFF shl 24) or (ch(r(base)) shl 16) or (ch(g(base)) shl 8) or ch(b(base))
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
