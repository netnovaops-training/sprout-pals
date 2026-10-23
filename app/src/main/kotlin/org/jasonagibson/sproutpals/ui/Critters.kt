package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.art.Grid
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.engine.Shop
import kotlin.math.abs
import kotlin.math.sin

/*
 * Garden visitors from the shop. Each is a small pixel drawing with two frames (wings, legs) and
 * its own little routine. Ground and fence animals draw BEHIND the pet; flyers draw IN FRONT.
 * Lower-case letters are shadow tones; the shaded() pass adds the dark outline.
 */
private object Art {
    val BUTTERFLY = listOf(
        listOf("OO...OO", "OOO.OOO", ".OoKoO.", "..oKo..", ".OO.OO.", "OO...OO"),
        listOf("..O.O..", "..OKO..", "..oKo..", "..OKO..", "..oKo..", "...K..."),
    )
    val BUTTERFLY_PAL = mapOf('O' to 0xFFF2912DL, 'o' to 0xFFC0641AL, 'K' to 0xFF1B1B1BL)

    val BEE = listOf(
        listOf(".WW.W.", "YKYKY.", "YKYKYK", ".YKY.."),
        listOf("......", "YKYKY.", "YKYKYK", ".YKY.."),
    )
    val BEE_PAL = mapOf('W' to 0xFFDCEBFFL, 'Y' to 0xFFFFD84AL, 'K' to 0xFF1B1B1BL)

    val LADYBUG = listOf(
        listOf("..KK...", ".RKRR..", "RRKRRR.", "RKRKRR.", ".K.K.K."),
        listOf("..KK...", ".RKRR..", "RRKRRR.", "RKRKRR.", "K.K.K.."),
    )
    val LADYBUG_PAL = mapOf('R' to 0xFFE0344BL, 'K' to 0xFF1B1B1BL)

    val BLUEBIRD = listOf(
        listOf("...BB..", "..BBBK.", ".BBBBBO", "BBWWBB.", ".bBBb..", "..O.O.."),
        listOf(".......", "...BB..", "..BBBK.", ".BBBBBO", "BBWWBB.", "..O.O.."),
    )
    val BLUEBIRD_PAL = mapOf('B' to 0xFF3C7DD9L, 'b' to 0xFF2A5AA8L, 'W' to 0xFFF2A35CL, 'K' to 0xFF1B1B1BL, 'O' to 0xFFE8B923L)

    val SQUIRREL = listOf(
        listOf("TT.......", "TTT..BB..", ".TT.BBBK.", ".TTBBBBBN", "..TBBBb..", "..BB.BB.."),
        listOf("TT.......", "TTT..BB..", ".TT.BBBK.", ".TTBBBBBN", "..TBBBb..", "...BBB..."),
    )
    val SQUIRREL_PAL = mapOf('T' to 0xFFC0702EL, 'B' to 0xFF9A5A2EL, 'b' to 0xFF7A4420L, 'K' to 0xFF1B1B1BL, 'N' to 0xFF3A2A1EL)

    val BUNNY = listOf(
        listOf("....W.W.", "....W.W.", "...WWWW.", "..WWWKW.", "WWWWWWWP", ".WWWWWW.", ".WW..WW."),
        listOf("....W.W.", "....W.W.", "...WWWW.", "..WWWKW.", "WWWWWWWP", ".WWWWWW.", "W......W"),
    )
    val BUNNY_PAL = mapOf('W' to 0xFFD8CFC4L, 'K' to 0xFF1B1B1BL, 'P' to 0xFFF2A3C0L)

    val DEER = listOf(
        listOf("........A..A", "........AAA.", ".......BBBB.", ".......BBKBN", "BBBBBBBBBB..", "BBWBBBBBBB..", "BBBBBBBBB...", ".B.B...B.B..", ".B.B...B.B..", ".K.K...K.K.."),
        listOf("............", "........A..A", "........AAA.", ".......BBBB.", "BBBBBBBBBBKB", "BBWBBBBBBBBN", "BBBBBBBBB...", ".B.B...B.B..", ".B.B...B.B..", ".K.K...K.K.."),
    )
    val DEER_PAL = mapOf('A' to 0xFF8A6A4AL, 'B' to 0xFFB5824AL, 'W' to 0xFFF4EBDCL, 'K' to 0xFF2B2018L, 'N' to 0xFF1B1B1BL)

    val HUMMINGBIRD = listOf(
        listOf("..WW...", ".GGGGK.", "GGRGGOO", ".GGG..."),
        listOf(".......", ".GGGGK.", "GGRGGOO", ".GWW..."),
    )
    val HUMMINGBIRD_PAL = mapOf('W' to 0xFFDCEBFFL, 'G' to 0xFF3E9E6AL, 'R' to 0xFFE0344BL, 'K' to 0xFF1B1B1BL, 'O' to 0xFF5C3A1AL)
}

private fun DrawScope.dot(x: Int, y: Int, c: Long, u: Float) =
    drawRect(Color(c), Offset(x * u, y * u), Size(u + 0.6f, u + 0.6f))

/** Back and forth between a and b at [speed] units per frame (a "ping-pong" walk). */
private fun pingPong(f: Int, a: Int, b: Int, speed: Float): Pair<Int, Boolean> {
    val span = (b - a).coerceAtLeast(1)
    val t = (f * speed).toInt() % (span * 2)
    return if (t < span) (a + t) to false else (b - (t - span)) to true
}

private fun DrawScope.sprite(g: Grid, pal: Map<Char, Long>, x: Int, y: Int, u: Float, flip: Boolean = false) =
    shaded(g, pal, x, y, u, flip)

/** A shop item's picture, centred in a 24x24-unit box and gently animated. */
fun DrawScope.drawItemPreview(id: String, f: Int, u: Float) {
    val (frames, pal) = when (id) {
        "ladybug" -> Art.LADYBUG to Art.LADYBUG_PAL
        "butterfly" -> Art.BUTTERFLY to Art.BUTTERFLY_PAL
        "bee" -> Art.BEE to Art.BEE_PAL
        "bluebird" -> Art.BLUEBIRD to Art.BLUEBIRD_PAL
        "bunny" -> Art.BUNNY to Art.BUNNY_PAL
        "squirrel" -> Art.SQUIRREL to Art.SQUIRREL_PAL
        "hummingbird" -> Art.HUMMINGBIRD to Art.HUMMINGBIRD_PAL
        "deer" -> Art.DEER to Art.DEER_PAL
        "fireflies" -> {
            for (i in 0 until 6) {
                val glow = abs(((f / 2 + i * 3) % 10) - 5)
                if (glow < 4) dot(3 + i * 3, 4 + (i * 7) % 14, 0xFFF4FF8AL, u)
                if (glow < 2) dot(3 + i * 3, 4 + (i * 7) % 14 + 1, 0x88F4FF8AL, u)
            }
            return
        }
        else -> { drawOutfitPreview(id, u); return }
    }
    // small sprites at double size; the deer is big enough as it is
    val g = frames[(f / 3) % frames.size]
    val scale = if (g.maxOf { it.length } * 2 <= 22) 2 else 1
    val gw = g.maxOf { it.length } * scale
    val gh = g.size * scale
    shaded(g, pal, (24 - gw) / 2 / scale, (24 - gh) / 2 / scale, u * scale)
}

/** Ground and fence visitors: drawn before the pet so it stands in front of them. */
fun DrawScope.drawBackCritters(save: Save, f: Int, top: Int, ground: Int, u: Float) {
    if (Shop.showing(save, "deer")) {
        val grazing = (f / 40) % 3 == 0
        sprite(Art.DEER[if (grazing) 1 else 0], Art.DEER_PAL, 76, top + 31, u, flip = true)
    }
    if (Shop.showing(save, "squirrel")) {
        val (x, back) = pingPong(f, 2, 70, 0.9f)
        sprite(Art.SQUIRREL[(f / 2) % 2], Art.SQUIRREL_PAL, x, top + 33, u, flip = back)
    }
    if (Shop.showing(save, "bluebird")) {
        val hop = (f / 24) % 4
        val pecking = f % 24 < 6
        sprite(Art.BLUEBIRD[if (pecking) 1 else 0], Art.BLUEBIRD_PAL, 6 + hop * 4, top + 35, u)
    }
    if (Shop.showing(save, "bunny")) {
        val (x, back) = pingPong(f, 0, 82, 0.5f)
        val hopping = (f / 4) % 2 == 0
        sprite(Art.BUNNY[if (hopping) 1 else 0], Art.BUNNY_PAL, x, ground - 6 - (if (hopping) 2 else 0), u, flip = back)
    }
    if (Shop.showing(save, "ladybug")) {
        val x = 92 - (f / 3) % 100
        sprite(Art.LADYBUG[(f / 2) % 2], Art.LADYBUG_PAL, x, ground + 1, u, flip = true)
    }
}

/** Flyers: drawn after the pet, so they flit in front of it. [headX]/[headY] = top-centre of the pet. */
fun DrawScope.drawFrontCritters(save: Save, f: Int, top: Int, ground: Int, u: Float, headX: Int, headY: Int, night: Boolean) {
    if (Shop.showing(save, "butterfly")) {
        val x = headX + (16 * sin(f * 0.11)).toInt() - 3
        val y = (headY - 6 + (5 * sin(f * 0.23)).toInt()).coerceAtLeast(top + 2)
        sprite(Art.BUTTERFLY[(f / 2) % 2], Art.BUTTERFLY_PAL, x, y, u)
    }
    if (Shop.showing(save, "hummingbird")) {
        val x = headX + 12 + (2 * sin(f * 0.4)).toInt()
        val y = (headY + 2 + (2 * sin(f * 0.3)).toInt()).coerceAtLeast(top + 2)
        sprite(Art.HUMMINGBIRD[f % 2], Art.HUMMINGBIRD_PAL, x, y, u, flip = true)
    }
    if (Shop.showing(save, "bee")) {
        // zips between the flowers along the bottom of the lawn
        val x = (f % 120) - 12
        val y = ground - 12 + (3 * sin(f * 0.6)).toInt()
        sprite(Art.BEE[f % 2], Art.BEE_PAL, x, y, u)
    }
    if (Shop.showing(save, "fireflies") && night) {
        for (i in 0 until 7) {
            val x = (i * 29 + (6 * sin((f + i * 17) * 0.05)).toInt()).mod(96)
            val y = top + 18 + (i * 11) % 34 + (3 * sin((f + i * 9) * 0.07)).toInt()
            val glow = abs(((f / 3 + i * 5) % 12) - 6)
            if (glow < 4) {
                dot(x, y, 0xFFF4FF8AL, u)
                if (glow < 2) { dot(x - 1, y, 0x88F4FF8AL, u); dot(x + 1, y, 0x88F4FF8AL, u); dot(x, y - 1, 0x88F4FF8AL, u); dot(x, y + 1, 0x88F4FF8AL, u) }
            }
        }
    }
}
