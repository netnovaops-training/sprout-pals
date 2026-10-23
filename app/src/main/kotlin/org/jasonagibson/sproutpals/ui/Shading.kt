package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.art.Grid

/**
 * SNES-style drawing of a character sprite:
 *  - a dark OUTLINE around the silhouette, tinted from the color it borders (selective outlining)
 *  - a HIGHLIGHT on pixels whose top edge is exposed (light from above)
 *  - a SHADOW on pixels whose bottom edge is exposed
 * So each flat palette color becomes 3 tones - that's where SNES sprites get their depth -
 * without hand-shading every drawing.
 */
fun DrawScope.shaded(g: Grid, pal: Map<Char, Long>, x: Int, y: Int, px: Float, flip: Boolean = false) {
    val w = g.maxOf { it.length }
    fun at(r: Int, c: Int): Char? {
        val row = g.getOrNull(r) ?: return null
        return row.getOrNull(c)?.takeIf { pal.containsKey(it) }
    }
    fun put(r: Int, c: Int, argb: Long) {
        val col = if (flip) w - 1 - c else c
        drawRect(Color(argb), Offset((x + col) * px, (y + r) * px), Size(px + 0.6f, px + 0.6f))
    }
    // outline first, so the sprite draws over it
    for (r in -1..g.size) for (c in -1..w) {
        if (at(r, c) != null) continue
        val n = listOf(at(r - 1, c), at(r + 1, c), at(r, c - 1), at(r, c + 1)).firstOrNull { it != null } ?: continue
        put(r, c, tone(pal.getValue(n), 0.35))
    }
    for (r in g.indices) for (c in 0 until w) {
        val ch = at(r, c) ?: continue
        val base = pal.getValue(ch)
        val shade = when {
            at(r + 1, c) != ch -> tone(base, 0.78)   // bottom edge of this color: shadow
            at(r - 1, c) != ch -> tone(base, 1.18)   // top edge: highlight
            else -> base
        }
        put(r, c, shade)
    }
}

/** Scales a color's brightness: below 1 darker, above 1 lighter. Keeps alpha. */
internal fun tone(argb: Long, k: Double): Long {
    val a = (argb shr 24) and 0xFF
    fun ch(shift: Int) = (((argb shr shift) and 0xFF) * k).toLong().coerceIn(0, 255)
    return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
