package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.art.Grid
import org.jasonagibson.sproutpals.engine.Garden
import org.jasonagibson.sproutpals.engine.PlotState
import org.jasonagibson.sproutpals.engine.Save

/*
 * The garden bed at the right of the lawn: three plots, each showing its crop as it grows
 * (seed, sprout, leafy, ripe). Wet soil is dark, dry soil is pale - a pale plot needs water.
 * Lower-case letters are shadow tones; the shaded() pass adds the outline.
 */
const val BED_X = 66
const val BED_W = 27
private const val PLOT_W = 9

private val SEED: Grid = listOf(".s.s.")
private val SPROUT: Grid = listOf("..G..", ".GgG.", "..g..")
private val LEAFY: Grid = listOf("G...G", "GG.GG", ".GgG.", "..g..")

private val RIPE: Map<String, Grid> = mapOf(
    "radish" to listOf(".G.G.", "..G..", ".RRR.", "RRRRr", ".Rrr."),
    "carrot" to listOf("G.G.G", ".GGG.", "..G..", ".OOO.", "..o.."),
    "strawberry" to listOf(".G.G.", "GRGRG", "RrGRr", "GGRGG", ".GgG."),
    "tomato" to listOf("..W..", "GRWRG", "RrWRr", ".GWG.", "..W.."),
    "pumpkin" to listOf("..g..", ".OGO.", "OOOOO", "OoOoO", ".ooo."),
    "berries" to listOf(".GBG.", "GBGBG", "BGGGB", "GBGBG", ".GgG."),
    "sunflower" to listOf(".YYY.", "YOOOY", "YOOOY", ".YYY.", "..G..", ".GG..", "..GG.", "..G..", "..g.."),
    "golden" to listOf("..Y..", ".YyY.", "YyYyY", ".YyY.", "..G..", ".GgG.", "..g.."),
)

private val PAL = mapOf(
    's' to 0xFFE8D9A8L, 'G' to 0xFF5BC236L, 'g' to 0xFF3E8E2AL,
    'R' to 0xFFE0344BL, 'r' to 0xFFA82238L, 'O' to 0xFFF2912DL, 'o' to 0xFFC0641AL,
    'W' to 0xFF9A6A3AL,
    'B' to 0xFF5A3FA8L, 'Y' to 0xFFFFD84AL, 'y' to 0xFFE8B923L,
)

/** Left edge of plot [i] (screen units). */
fun plotX(i: Int) = BED_X + 1 + i * PLOT_W

/** Draws the bed. [selected] = the plot the Garden panel is pointing at (blinking marker). */
fun DrawScope.drawGardenBed(save: Save, now: Long, f: Int, ground: Int, px: Float, selected: Int?) {
    // frame
    block(BED_X, ground - 2, BED_W, 4, 0xFF8A5A2B, px)
    Garden.plots(save).forEachIndexed { i, plot ->
        val x = plotX(i)
        val state = Garden.state(plot, now)
        val wet = state == PlotState.GROWING
        block(x, ground - 1, PLOT_W - 1, 2, if (wet) 0xFF4A2E16 else 0xFFB08850, px)
        val crop = plot.crop ?: return@forEachIndexed
        val g = when (Garden.growth(plot, now)) {
            0 -> SEED
            1 -> SPROUT
            2 -> LEAFY
            else -> RIPE[crop] ?: LEAFY
        }
        val w = g.maxOf { it.length }
        shaded(g, PAL, x + (PLOT_W - 1 - w) / 2, ground - 1 - g.size, px)
        // ripe: a twinkle over it; dry: a little water drop asking for a drink
        if (state == PlotState.RIPE && f % 8 < 4) block(x + PLOT_W / 2 - 1, ground - 3 - g.size - 2, 1, 1, 0xFFFFF6A0, px)
        if (state == PlotState.DRY && f % 16 < 10) {
            val dx = x + PLOT_W / 2 - 1; val dy = ground - 4 - g.size - 3
            block(dx, dy, 1, 1, 0xFF6FB8F0, px); block(dx - 1, dy + 1, 3, 2, 0xFF6FB8F0, px)
        }
    }
    if (selected != null && f % 4 < 3) {
        val x = plotX(selected) + PLOT_W / 2 - 2
        val y = ground - 14
        block(x, y, 3, 1, 0xFFFFFFFF, px); block(x + 1, y + 1, 1, 1, 0xFFFFFFFF, px)
    }
}
