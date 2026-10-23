package org.jasonagibson.sproutpals.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jasonagibson.sproutpals.art.Sprites
import org.jasonagibson.sproutpals.engine.Kind
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Stage

/*
 * The opening scene, redrawn from Jason's reference picture in our own pixel art.
 *
 * WHY NOT JUST PLAY THE VIDEO: it was AI-generated, and the dogs and cats morph into each
 * other - they trade places and colors between frames. Here every character is drawn from
 * its OWN sprite and palette at its OWN fixed spot, so Rusty is always the brown collie on
 * the left and DJ is always the tuxedo cat. They only bob, hop and wag in place.
 */

private const val W = 160
private const val H = 112

/** Fixed spot per character: x, ground line, and an animation phase so they don't move in lockstep. */
internal data class Spot(val species: Species, val x: Int, val ground: Int, val phase: Int)

internal val CAST = listOf(
    // back row: the people
    Spot(Species.KONA, 12, 76, 0),
    Spot(Species.JASON, 64, 76, 1),
    Spot(Species.JUN, 116, 76, 2),
    // front row: dogs on the outside, cats in the middle
    Spot(Species.RUSTY, 2, 108, 3),
    Spot(Species.MAUI, 44, 108, 1),
    Spot(Species.DJ, 84, 108, 0),
    Spot(Species.KARA, 126, 108, 2),
)

private fun DrawScope.px(x: Int, y: Int, w: Int, h: Int, c: Long, p: Float) =
    drawRect(Color(c), Offset(x * p, y * p), Size(w * p + 0.6f, h * p + 0.6f))

private fun DrawScope.drawTitleScene(frame: Int) {
    val p = size.width / W

    // sky with a soft band, clouds drifting
    px(0, 0, W, 24, 0xFF6FC0EE, p); px(0, 24, W, 20, 0xFF93D3F5, p)
    val cloud = (frame / 8) % W
    px((cloud + 16) % W, 6, 18, 4, 0xFFF4F7FA, p); px((cloud + 20) % W, 4, 10, 2, 0xFFF4F7FA, p)
    px((cloud + 100) % W, 12, 14, 4, 0xFFF4F7FA, p); px((cloud + 103) % W, 10, 8, 2, 0xFFF4F7FA, p)

    // trees at both edges: trunk, two-tone canopy
    for (tx in listOf(6, 148)) {
        px(tx, 16, 6, 30, 0xFF6B4423, p); px(tx + 2, 16, 2, 30, 0xFF4E3119, p)
        px(tx - 6, 4, 20, 16, 0xFF2F8A3A, p); px(tx - 4, 0, 16, 6, 0xFF3FA04A, p); px(tx - 2, 2, 8, 4, 0xFF5BC236, p)
    }

    // fence behind the right side
    for (x in 88 until 146 step 8) { px(x, 26, 6, 18, 0xFFA8743F, p); px(x + 4, 26, 2, 18, 0xFF8A5A2B, p) }
    px(88, 30, 56, 2, 0xFF8A5A2B, p); px(88, 38, 56, 2, 0xFF8A5A2B, p)

    // lawn with mowing stripes
    px(0, 44, W, H - 44, 0xFF5BC236, p)
    for (y in 48 until H step 8) px(0, y, W, 2, 0xFF4FB02E, p)

    // flowers that sway
    val sway = if (frame % 8 < 4) 0 else 1
    for ((fx, fy, c) in listOf(Triple(4, 82, 0xFFE0344BL), Triple(30, 90, 0xFFFFD84AL), Triple(152, 84, 0xFF8E6FE0L), Triple(72, 60, 0xFF3C7DD9L), Triple(110, 58, 0xFFE0344BL), Triple(156, 100, 0xFFFFD84AL), Triple(56, 100, 0xFFF2A3C0L))) {
        px(fx + 1, fy + 2, 2, 4, 0xFF2F8A3A, p)
        px(fx - 1 + sway, fy - 1, 6, 4, c, p); px(fx + 1 + sway, fy, 2, 2, 0xFFFFF3B0, p)
    }

    // the cast: same sprite + same palette every frame, only a small bob/hop
    for (spot in CAST) {
        val body = Sprites.body(spot.species, Stage.ADULT)
        val pal = Sprites.palette(spot.species, Stage.ADULT)
        val beat = (frame + spot.phase * 2) % 8
        val lift = if (spot.species.kind == Kind.HUMAN) listOf(0, 1, 2, 2, 1, 0, 0, 0)[beat] else listOf(0, 2, 4, 5, 4, 2, 0, 0)[beat]
        val y = spot.ground - body.size + 1 - lift
        // the two people on the right face inward; animals on the right look left
        val flip = spot.x > W / 2 - 16
        shaded(body, pal, spot.x, y, p, flip = flip)
    }
}

private val rainbow = listOf(0xFFE0344B, 0xFFF2A3C0, 0xFFF2B33D, 0xFF5BC236, 0xFF3C7DD9, 0xFF8E6FE0)

@Composable
fun TitleScreen(onStart: () -> Unit) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(125); frame++ } }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF2E5E3A)).clickable(onClick = onStart).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // "SPROUT PALS" in blocky rainbow letters, like the reference picture's title
        Row {
            "SPROUT PALS".forEachIndexed { i, ch ->
                val bounce = (-listOf(0, 2, 4, 2, 0, 0, 0, 0, 0, 0, 0, 0)[(frame + i) % 12]).dp
                BasicText(
                    ch.toString(),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 40.sp, color = Color(rainbow[i % rainbow.size])),
                    modifier = Modifier.padding(top = 4.dp + bounce, bottom = 4.dp - bounce),
                )
            }
        }
        BasicText(
            "A VIRTUAL PET ADVENTURE",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFF3E9C6)),
        )
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFE6E8CF)).padding(10.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(W / H.toFloat()).border(3.dp, Color(0xFF1E2A1C))) {
                drawTitleScene(frame)
            }
        }
        BasicText(
            if (frame % 8 < 6) "TAP TO START" else "",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFFF3E9C6)),
        )
    }
}
