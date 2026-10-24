package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.engine.WeatherKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Weather drawn over the garden while an event is on (see engine Weather):
 * rain streaks, a dark storm with lightning flashes, a rainbow arc, a warm sun glow, a heat haze.
 */
fun DrawScope.drawWeather(kind: WeatherKind, f: Int, top: Int, h: Int, px: Float) {
    when (kind) {
        WeatherKind.RAIN, WeatherKind.STORM -> {
            if (kind == WeatherKind.STORM) {
                block(0, top, LCD_W, h, 0x55202840L, px)
                // a lightning flash every few seconds
                if (f % 40 in 0..1) block(0, top, LCD_W, h, 0x88FFFFFFL, px)
                if (f % 40 in 0..3) {
                    val x = 20 + (f / 40 * 37) % 56
                    listOf(0 to 0, 1 to 2, 0 to 4, 2 to 6, 1 to 8, 3 to 10).forEach { (dx, dy) -> block(x + dx, top + 2 + dy, 1, 2, 0xFFFFF6A0L, px) }
                }
            }
            val drops = if (kind == WeatherKind.STORM) 34 else 22
            val slant = if (kind == WeatherKind.STORM) 2 else 1
            for (i in 0 until drops) {
                val x = (i * 29 + f * slant) % LCD_W
                val y = top + (i * 17 + f * 3) % h
                block(x, y, 1, 3, 0xDDB8E0FFL, px)
            }
        }
        WeatherKind.SNOW -> {
            block(0, top, LCD_W, h, 0x22DCEBFFL, px)
            // a white blanket on the lawn, and big flakes drifting down and sideways
            block(0, top + h - 10, LCD_W, 3, 0xCCF4F8FFL, px)
            for (i in 0 until 40) {
                val y = top + (i * 13 + f) % h
                val x = (i * 37 + (3 * sin((f + i * 11) * 0.12)).toInt() + LCD_W) % LCD_W
                val big = i % 3 == 0
                block(x, y, if (big) 2 else 1, if (big) 2 else 1, 0xFFFFFFFFL, px)
            }
        }
        WeatherKind.RAINBOW -> {
            val colors = listOf(0x88E0344BL, 0x88F2912DL, 0x88FFD84AL, 0x885BC236L, 0x883C7DD9L, 0x888E6FE0L)
            colors.forEachIndexed { band, c ->
                val r = 40 - band
                var a = 0.0
                while (a <= PI) {
                    val x = 48 + (r * cos(a)).toInt()
                    val y = top + 44 - (r * 0.75 * sin(a)).toInt()
                    if (y >= top) block(x, y, 1, 1, c, px)
                    a += 0.02
                }
            }
        }
        WeatherKind.SUN -> {
            block(0, top, LCD_W, h, 0x22FFD84AL, px)
            // rays turning slowly around a sun in the corner
            for (i in 0 until 8) {
                val a = i * PI / 4 + f * 0.03
                for (d in 7..10) block(86 + (d * cos(a)).toInt(), top + 6 + (d * sin(a)).toInt(), 1, 1, 0xCCFFE27AL, px)
            }
            block(83, top + 3, 6, 6, 0xFFFFD84AL, px)
        }
        WeatherKind.HEAT -> {
            block(0, top, LCD_W, h, 0x33F2912DL, px)
            // shimmering haze lines
            for (i in 0 until 5) {
                val y = top + 30 + i * 6
                for (x in 0 until LCD_W step 2) if (((x + f + i * 3) / 4) % 2 == 0) block(x, y + if ((x / 2 + f) % 4 < 2) 0 else 1, 2, 1, 0x44FFFFFFL, px)
            }
        }
    }
}
