package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.engine.Holiday
import org.jasonagibson.sproutpals.engine.Season
import kotlin.math.sin

/*
 * Season touches and holiday decorations, drawn over the lawn behind the pet:
 * spring petals, autumn leaves, winter frost; Valentine hearts, the hidden spring egg,
 * Fourth of July fireworks, Halloween pumpkins and bats, December string lights on the fence.
 */
private const val TOP = 20     // the garden's top row (AREA_TOP in Lcd)
private const val GROUND = 76

/** A small white thought bubble with a tail, holding a 5x5 glyph for the mood. */
fun DrawScope.drawMoodBubble(mood: org.jasonagibson.sproutpals.engine.Mood, x: Int, y: Int, px: Float) {
    val glyph: List<String> = when (mood) {
        org.jasonagibson.sproutpals.engine.Mood.HAPPY -> listOf("..XX.", "..X.X", "..X..", "XXX..", "XX...")      // a note
        org.jasonagibson.sproutpals.engine.Mood.LOVE -> listOf(".X.X.", "XXXXX", "XXXXX", ".XXX.", "..X..")
        org.jasonagibson.sproutpals.engine.Mood.HUNGRY -> listOf(".XXX.", "X...X", "..XX.", ".....", "..X..")      // ?
        org.jasonagibson.sproutpals.engine.Mood.ALARM -> listOf("..X..", "..X..", "..X..", ".....", "..X..")       // !
        org.jasonagibson.sproutpals.engine.Mood.SWEAT -> listOf("..X..", ".XX..", "XXXX.", "XXXX.", ".XX..")      // a drop
        org.jasonagibson.sproutpals.engine.Mood.SPARKLE -> listOf("..X..", "X.X.X", ".XXX.", "X.X.X", "..X..")
        org.jasonagibson.sproutpals.engine.Mood.NONE -> return
    }
    val ink = when (mood) {
        org.jasonagibson.sproutpals.engine.Mood.LOVE -> 0xFFE0344BL
        org.jasonagibson.sproutpals.engine.Mood.SWEAT -> 0xFF3C7DD9L
        org.jasonagibson.sproutpals.engine.Mood.ALARM, org.jasonagibson.sproutpals.engine.Mood.HUNGRY -> 0xFFC0641AL
        org.jasonagibson.sproutpals.engine.Mood.SPARKLE -> 0xFFE8B923L
        else -> 0xFF1E2A1CL
    }
    block(x - 1, y - 1, 9, 9, 0xFF1E2A1CL, px)          // outline
    block(x, y, 7, 7, 0xFFFFFFFFL, px)
    block(x - 2, y + 8, 2, 2, 0xFFFFFFFFL, px)          // the little tail toward the head
    glyph.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> if (ch == 'X') block(x + 1 + c, y + 1 + r, 1, 1, ink, px) } }
}

/** Where the spring egg hides in the grass (also where the pal finds it while gardening). */
const val EGG_X = 60

fun DrawScope.drawSeasonAndHoliday(season: Season, holiday: Holiday?, f: Int, evening: Boolean, eggFound: Boolean, px: Float) {
    // drifting petals (spring) or leaves (autumn); frosty tufts (winter)
    when (season) {
        Season.SPRING, Season.AUTUMN -> {
            val colors = if (season == Season.SPRING) listOf(0xFFF2A3C0L, 0xFFFFFFFFL) else listOf(0xFFC0641AL, 0xFFE8B923L, 0xFFB5402EL)
            for (i in 0 until 7) {
                val y = TOP + (i * 11 + f / 2) % 50
                val x = (i * 23 + (4 * sin((f + i * 13) * 0.08)).toInt() + LCD_W) % LCD_W
                block(x, y, 2, 1, colors[i % colors.size], px)
            }
        }
        Season.WINTER -> for (i in 0 until 9) block((i * 11 + 3) % LCD_W, GROUND + 1, 3, 1, 0xCCF4F8FFL, px)
        Season.SUMMER -> Unit
    }

    when (holiday) {
        Holiday.VALENTINES -> for (i in 0 until 4) {
            val y = TOP + 40 - (f / 2 + i * 9) % 36
            val x = 10 + i * 22 + (2 * sin((f + i * 7) * 0.15)).toInt()
            block(x, y, 2, 1, 0xFFE0344BL, px); block(x + 3, y, 2, 1, 0xFFE0344BL, px); block(x, y + 1, 5, 1, 0xFFE0344BL, px); block(x + 1, y + 2, 3, 1, 0xFFE0344BL, px); block(x + 2, y + 3, 1, 1, 0xFFE0344BL, px)
        }
        Holiday.EGG_HUNT -> if (!eggFound) {
            // a painted egg peeking out of the grass, with a little sparkle now and then
            block(EGG_X, GROUND - 4, 4, 5, 0xFF8E6FE0L, px); block(EGG_X, GROUND - 2, 4, 1, 0xFFFFD84AL, px); block(EGG_X + 1, GROUND - 5, 2, 1, 0xFF8E6FE0L, px)
            if (f % 24 < 3) block(EGG_X + 4, GROUND - 7, 1, 1, 0xFFFFFFFFL, px)
        }
        Holiday.FIREWORKS -> if (evening) for (k in 0 until 2) {
            val t = (f + k * 20) % 40
            val cx = 24 + k * 44; val cy = TOP + 12 + k * 4
            val color = listOf(0xFFE0344BL, 0xFF3C7DD9L, 0xFFFFD84AL)[(f / 40 + k) % 3]
            if (t < 10) block(cx, cy + 20 - t * 2, 1, 2, 0xFFFFF6A0L, px)   // rising
            else if (t < 26) {
                val r = (t - 10) / 2
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)) block(cx + dx * r, cy + dy * r, 1, 1, color, px)
            }
        }
        Holiday.HALLOWEEN -> {
            for (x in listOf(30, 78)) {
                block(x, GROUND - 4, 7, 5, 0xFFF2912DL, px); block(x + 1, GROUND - 5, 5, 1, 0xFFF2912DL, px); block(x + 3, GROUND - 7, 1, 2, 0xFF4E9E3AL, px)
                if (evening) { block(x + 1, GROUND - 3, 1, 1, 0xFFFFE27AL, px); block(x + 5, GROUND - 3, 1, 1, 0xFFFFE27AL, px); block(x + 2, GROUND - 1, 3, 1, 0xFFFFE27AL, px) }
            }
            if (evening) for (i in 0 until 3) {
                val x = (f + i * 31) % (LCD_W + 10) - 5
                val y = TOP + 8 + i * 6 + (2 * sin((f + i * 9) * 0.3)).toInt()
                val flap = (f / 2 + i) % 2 == 0
                block(x, y, 2, 1, 0xFF1B1B1BL, px); block(x - 2, y - if (flap) 1 else 0, 2, 1, 0xFF1B1B1BL, px); block(x + 2, y - if (flap) 1 else 0, 2, 1, 0xFF1B1B1BL, px)
            }
        }
        Holiday.WINTER_LIGHTS -> {
            val colors = listOf(0xFFE0344BL, 0xFF5BC236L, 0xFFFFD84AL, 0xFF3C7DD9L)
            for (i in 0 until 24) {
                val x = i * 4 + 1
                val y = TOP + 40 + if (i % 2 == 0) 0 else 1
                val on = (i + f / 4) % 3 != 0
                block(x, y, 1, 1, if (on) colors[i % colors.size] else 0xFF3A3A3AL, px)
            }
        }
        null -> Unit
    }
}
