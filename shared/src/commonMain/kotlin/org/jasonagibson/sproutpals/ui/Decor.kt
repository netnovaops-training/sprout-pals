package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.art.Grid
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.engine.Shop

/*
 * Garden decor from the shop. Each piece has its own fixed spot so nothing overlaps:
 * the back row stands on the lawn's far edge, the front row by the path. All of it is drawn
 * behind the pet and the creatures. Lower-case letters are shadow tones.
 */
private class Piece(val grid: Grid, val pal: Map<Char, Long>, val x: Int, val baseFromGround: Int)

private val STONE = 0xFFA8A8A0L
private val STONE_D = 0xFF78786FL

private val PIECES = linkedMapOf(
    "well" to Piece(
        listOf("..RRRRR..", ".RRRRRRR.", "RRRRRRRRR", ".W.....W.", ".W..K..W.", ".W..K..W.", "SSSSSSSSS", "SsSsSsSsS", "SSSSSSSSS", "sSsSsSsSs", ".SSSSSSS."),
        mapOf('R' to 0xFFB5402EL, 'W' to 0xFF8A5A2EL, 'K' to 0xFF5C3A1AL, 'S' to STONE, 's' to STONE_D), x = 2, baseFromGround = 6,
    ),
    "birdbath" to Piece(
        listOf("SBBBBBS", ".SSSSS.", "..SsS..", "...S...", "...S...", "..SSS.."),
        mapOf('S' to STONE, 's' to STONE_D, 'B' to 0xFF6FB8F0L), x = 30, baseFromGround = 6,
    ),
    "lantern" to Piece(
        listOf("KKK", "KYK", "KKK", ".P.", ".P.", ".P.", ".P.", "PPP"),
        mapOf('K' to 0xFF2B2B2BL, 'Y' to 0xFFFFD84AL, 'P' to 0xFF5C3A1AL), x = 40, baseFromGround = 6,
    ),
    "bench" to Piece(
        listOf("WWWWWWWWWWWW", "wwwwwwwwwwww", "WWWWWWWWWWWW", "K..........K", "K..........K"),
        mapOf('W' to 0xFFB5824AL, 'w' to 0xFF8A5A2EL, 'K' to 0xFF3A2A1EL), x = 44, baseFromGround = 6,
    ),
    "scarecrow" to Piece(
        listOf("...YYY...", "..YYYYY..", ".YYYYYYY.", "...SSS...", "...SKS...", "...SSS...", "RRRRRRRRR", "...RrR...", "...RRR...", "...BBB...", "...B.B...", "...P.P...", "...P.P..."),
        mapOf('Y' to 0xFFF0D27AL, 'S' to 0xFFE8C9A0L, 'K' to 0xFF1B1B1BL, 'R' to 0xFFC8323CL, 'r' to 0xFF8E1E28L, 'B' to 0xFF3A5A9AL, 'P' to 0xFF8A5A2EL),
        x = 84, baseFromGround = 4,
    ),
    "rain_barrel" to Piece(
        listOf("MMMMM", "BBBBB", "BBbBB", "MMMMM", "BBbBB", "bbbbb"),
        mapOf('M' to 0xFF8A8A8AL, 'B' to 0xFF9A6A3AL, 'b' to 0xFF6A4220L), x = 59, baseFromGround = 1,
    ),
    "pond" to Piece(
        listOf("..BBBBBBBBBBBB..", ".BBLBBBBBBBGGBB.", "BBBBBBBBBBBGBBBB", ".bbbbbbbbbbbbbb."),
        mapOf('B' to 0xFF4F9ED8L, 'b' to 0xFF2E6FA8L, 'L' to 0xFFDCEBFFL, 'G' to 0xFF4E9E3AL), x = 17, baseFromGround = 0,
    ),
    "gnome" to Piece(
        listOf("..R..", ".RRR.", "RRRRR", ".SSS.", "WWWWW", ".BWB.", ".BBB.", ".K.K."),
        mapOf('R' to 0xFFE0344BL, 'S' to 0xFFF2C9A0L, 'W' to 0xFFF4F4F4L, 'B' to 0xFF3C7DD9L, 'K' to 0xFF3A2A1EL), x = 10, baseFromGround = 0,
    ),
    "flower_pots" to Piece(
        listOf(".R...Y.", "RGR.YGY", ".G...G.", "TTT.TTT", ".t...t."),
        mapOf('R' to 0xFFF27AA8L, 'Y' to 0xFFFFD84AL, 'G' to 0xFF4E9E3AL, 'T' to 0xFFC8643CL, 't' to 0xFF8E4428L), x = 1, baseFromGround = 0,
    ),
)

/** Draws every decor piece that's switched on. [night] lights the lantern. The scarecrow also
 *  stands in the garden while a SCARECROW WATCH is on guard ([now] = the game clock). */
fun DrawScope.drawDecor(save: Save, f: Int, ground: Int, px: Float, night: Boolean, now: Long) {
    for ((id, p) in PIECES) {
        if (!Shop.showing(save, id) && !(id == "scarecrow" && Shop.watching(save, now))) continue
        var grid = p.grid
        // the pond's highlight drifts across the water
        if (id == "pond") grid = grid.mapIndexed { r, row -> if (r == 1) row.replace('L', 'B').let { it.substring(0, 3 + (f / 6) % 6) + "L" + it.substring(4 + (f / 6) % 6) } else row }
        val top = ground - p.baseFromGround - grid.size
        shaded(grid, p.pal, p.x, top, px)
        if (id == "lantern" && night) {
            val glow = if (f % 16 < 8) 0x66FFE27AL else 0x44FFE27AL
            block(p.x - 2, top - 2, 7, 7, glow, px)
        }
    }
}

/** The decor piece's picture for the shop card, double size in a 24-unit box. */
fun DrawScope.drawDecorPreview(id: String, u: Float): Boolean {
    val p = PIECES[if (id == Shop.WATCH) "scarecrow" else id] ?: return false
    val w = p.grid.maxOf { it.length }
    val scale = if (w * 2 <= 22 && p.grid.size * 2 <= 22) 2 else 1
    shaded(p.grid, p.pal, (24 / scale - w) / 2, (24 / scale - p.grid.size) / 2, u * scale)
    return true
}
