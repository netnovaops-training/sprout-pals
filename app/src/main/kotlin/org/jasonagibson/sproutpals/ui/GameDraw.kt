package org.jasonagibson.sproutpals.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jasonagibson.sproutpals.art.CharacterArt
import org.jasonagibson.sproutpals.engine.FruitCatch
import org.jasonagibson.sproutpals.engine.HigherLower
import org.jasonagibson.sproutpals.engine.JumpRope
import org.jasonagibson.sproutpals.engine.Kind
import org.jasonagibson.sproutpals.engine.MiniGame
import org.jasonagibson.sproutpals.engine.PetState
import org.jasonagibson.sproutpals.engine.PlantHarvest
import org.jasonagibson.sproutpals.engine.ScareCrows
import org.jasonagibson.sproutpals.engine.Slots
import org.jasonagibson.sproutpals.engine.TicTacToe
import org.jasonagibson.sproutpals.engine.TwentyOne
import kotlin.math.PI
import kotlin.math.cos

/*
 * Drawing for the mini-games, on top of the garden background. Coordinates are screen units
 * (the 96-wide LCD); [top] is the top of the garden area and [ground] where feet stand.
 */

private fun DrawScope.px(x: Int, y: Int, w: Int, h: Int, c: Long, u: Float) =
    drawRect(Color(c), Offset(x * u, y * u), Size(w * u + 0.6f, h * u + 0.6f))

/** The pet, standing at [cx], in a given pose, sized for the games. */
private fun DrawScope.pal(pet: PetState, cx: Float, ground: Float, u: Float, pose: Int, height: Float = 30f, faceLeft: Boolean = false) {
    val img = CharacterArt.of(pet.species, pet.stage, pose)
    character(pet.species, pet.stage, cx, ground, u, height / img.height, faceLeft = faceLeft, pose = pose)
}

fun DrawScope.drawMiniGame(g: MiniGame, pet: PetState, frame: Int, top: Int, ground: Int, u: Float) {
    when (g) {
        is FruitCatch -> fruitCatch(g, pet, frame, top, ground, u)
        is JumpRope -> jumpRope(g, pet, frame, ground, u)
        is TicTacToe -> ticTacToe(g, pet, frame, top, ground, u)
        is HigherLower -> higherLower(g, pet, frame, top, ground, u)
        is PlantHarvest -> plantHarvest(g, pet, frame, top, ground, u)
        is TwentyOne -> twentyOne(g, pet, frame, top, ground, u)
        is Slots -> slots(g, pet, frame, top, ground, u)
        is ScareCrows -> scareCrows(g, frame, ground, u)
    }
}

/** A small picture from rows of letters; each letter is a color in [colors], '.' is see-through. */
private fun DrawScope.grid(rows: List<String>, colors: Map<Char, Long>, x: Int, y: Int, u: Float) {
    rows.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> colors[ch]?.let { px(x + k, y + r, 1, 1, it, u) } } }
}

// ---------- Plant & Harvest ----------

private fun plotCenter(i: Int) = 10 + i * 19

private fun DrawScope.plantHarvest(g: PlantHarvest, pet: PetState, f: Int, top: Int, ground: Int, u: Float) {
    val bedY = ground - 14
    for (i in 0 until g.plots) {
        val cx = plotCenter(i)
        val gr = g.growth[i]
        px(cx - 7, bedY, 15, 3, 0xFF5C3A1A, u)                          // soil
        val rotten = gr > 100
        val stem = (gr / 9).coerceIn(1, 10)
        val leaf = if (rotten) 0xFF8A7A4AL else 0xFF5BC236L
        px(cx, bedY - stem, 1, stem, leaf, u)
        if (stem > 3) { px(cx - 2, bedY - stem + 2, 2, 1, leaf, u); px(cx + 1, bedY - stem + 3, 2, 1, leaf, u) }
        when {
            rotten -> px(cx - 2, bedY - stem - 2, 5, 3, 0xFF6A4A2AL, u)                          // wilted, brown
            gr >= 70 -> {
                // the fruit, glowing gold while it's perfect
                if (gr in 78..86 && f % 4 < 3) px(cx - 4, bedY - stem - 5, 9, 8, 0x66FFE27AL, u)
                px(cx - 2, bedY - stem - 3, 5, 4, 0xFFE0344BL, u); px(cx - 1, bedY - stem - 3, 1, 1, 0xFFFFFFFFL, u)
            }
            gr >= 45 -> px(cx - 1, bedY - stem - 2, 3, 2, 0xFF8FD46AL, u)                       // a green bud
        }
        // ripeness meter under each plant: green fill, gold band = perfect
        val meterY = bedY + 4
        px(cx - 7, meterY - 1, 15, 5, 0xFF1E2A1CL, u)                                              // dark frame
        px(cx - 6, meterY, 13 * gr.coerceAtMost(110) / 110, 3, if (rotten) 0xFF8A5A2BL else 0xFF8FD46AL, u)
        px(cx - 6 + 13 * 78 / 110, meterY - 1, 2, 5, 0xFFFFD84AL, u)                               // the gold mark
    }
    // an arrow over the chosen plant; the pal stands just beside it (not in front of it), a little smaller
    val cx = plotCenter(g.cursor)
    if (f % 4 < 3) { px(cx - 1, top + 6, 3, 1, 0xFFFFFFFFL, u); px(cx, top + 7, 1, 1, 0xFFFFFFFFL, u) }
    pal(pet, (cx + 8).toFloat(), ground.toFloat() + 1, u, if (g.callout == "PERFECT!") CharacterArt.Pose.clap(pet.species.kind, f) else CharacterArt.Pose.STAND, 17f, faceLeft = true)
}

// ---------- Fruit Catch ----------

private fun laneCenter(lane: Int) = 8 + lane * 13

private fun DrawScope.fruitCatch(g: FruitCatch, pet: PetState, f: Int, top: Int, ground: Int, u: Float) {
    for (d in g.drops) {
        val x = laneCenter(d.lane) - 3
        val y = top + 4 + d.y
        if (d.rock) {
            px(x, y + 1, 7, 5, 0xFF7D7D85, u); px(x + 1, y, 5, 7, 0xFF7D7D85, u); px(x + 1, y + 1, 2, 2, 0xFFB5B5BD, u)
        } else {
            val body = listOf(0xFFE0344BL, 0xFFF2912DL, 0xFF8E4FC9L)[(d.lane + d.y / 50) % 3]
            px(x, y + 2, 7, 5, body, u); px(x + 1, y + 1, 5, 7, body, u)
            px(x + 1, y + 2, 2, 2, 0xFFFFFFFF, u)                  // shine
            px(x + 3, y - 1, 1, 2, 0xFF6B4423, u); px(x + 4, y - 1, 2, 1, 0xFF5BC236, u)   // stem + leaf
        }
    }
    val walking = f % 4 < 2
    pal(pet, laneCenter(g.lane).toFloat(), ground.toFloat(), u, CharacterArt.Pose.walk(pet.species.kind, if (walking) 0 else 2), 28f)
}

// ---------- Jump Rope ----------

private fun DrawScope.jumpRope(g: JumpRope, pet: PetState, f: Int, ground: Int, u: Float) {
    // two posts holding the rope
    px(10, ground - 18, 3, 19, 0xFFA8743F, u); px(83, ground - 18, 3, 19, 0xFFA8743F, u)
    val left = 12; val right = 84; val anchorY = ground - 16
    // the rope's lowest point swings from overhead to the feet once per swing
    val angle = 2 * PI * (g.phase - (g.period - 1)) / g.period
    val sag = (16 + 16 * cos(angle)).toFloat()               // 32 = at the feet, 0 = overhead
    val midY = anchorY - 18 + sag * 1.15f
    var x = left
    while (x <= right) {
        val t = (x - left).toFloat() / (right - left)
        val y = anchorY + (midY - anchorY) * 4 * t * (1 - t)
        px(x, y.toInt(), 2, 1, 0xFFE0344B, u)
        x += 2
    }
    val lift = listOf(0, 3, 6, 7, 6, 3)[g.air.coerceIn(0, 5)]
    val pose = if (g.air > 0) CharacterArt.Pose.JUMP else CharacterArt.Pose.STAND
    pal(pet, 48f, (ground - lift).toFloat(), u, pose, 28f)
}

// ---------- Tic-Tac-Toe ----------

private fun DrawScope.ticTacToe(g: TicTacToe, pet: PetState, f: Int, top: Int, ground: Int, u: Float) {
    val cell = 13
    val bx = 8; val by = top + 12
    px(bx - 2, by - 2, cell * 3 + 4, cell * 3 + 4, 0xEEFFF8E4, u)
    for (i in 1..2) {
        px(bx + i * cell - 1, by, 2, cell * 3, 0xFF5C3A1A, u)
        px(bx, by + i * cell - 1, cell * 3, 2, 0xFF5C3A1A, u)
    }
    for (i in 0 until 9) {
        val cx = bx + (i % 3) * cell + 2
        val cy = by + (i / 3) * cell + 2
        if (!g.over && i == g.cursor && f % 8 < 6) px(cx - 1, cy - 1, cell - 2, cell - 2, 0x88FFD84A, u)
        when (g.board[i]) {
            'X' -> for (k in 0 until 8) { px(cx + k, cy + k, 2, 2, 0xFFE0344B, u); px(cx + 7 - k, cy + k, 2, 2, 0xFFE0344B, u) }
            'O' -> { px(cx + 2, cy, 5, 2, 0xFF3C7DD9, u); px(cx + 2, cy + 7, 5, 2, 0xFF3C7DD9, u); px(cx, cy + 2, 2, 5, 0xFF3C7DD9, u); px(cx + 7, cy + 2, 2, 5, 0xFF3C7DD9, u) }
        }
    }
    // your pal watches from the side, and cheers when it gets three in a row
    val pose = if (g.over && g.winner == 'O') CharacterArt.Pose.cheer(pet.species.kind) else CharacterArt.Pose.wave(pet.species.kind, f)
    pal(pet, 72f, ground.toFloat(), u, pose, 30f, faceLeft = true)
}

// ---------- Higher / Lower ----------

private val DIGITS = mapOf(
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"), '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
    '2' to listOf("###", "..#", "###", "#..", "###"), '3' to listOf("###", "..#", ".##", "..#", "###"),
    '4' to listOf("#.#", "#.#", "###", "..#", "..#"), '5' to listOf("###", "#..", "###", "..#", "###"),
    '6' to listOf("###", "#..", "###", "#.#", "###"), '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
    '8' to listOf("###", "#.#", "###", "#.#", "###"), '9' to listOf("###", "#.#", "###", "..#", "###"),
    '?' to listOf("###", "..#", ".##", "...", ".#."),
    'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'J' to listOf("..#", "..#", "..#", "#.#", "###"),
    'Q' to listOf("###", "#.#", "#.#", "###", "..#"), 'K' to listOf("#.#", "##.", "#..", "##.", "#.#"),
)

private fun DrawScope.bigChar(c: Char, x: Int, y: Int, scale: Int, color: Long, u: Float) {
    val g = DIGITS[c] ?: return
    g.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == '#') px(x + k * scale, y + r * scale, scale, scale, color, u) } }
}

private fun DrawScope.higherLower(g: HigherLower, pet: PetState, f: Int, top: Int, ground: Int, u: Float) {
    // two cards: the number you see, and the next one (a "?" until you guess)
    for ((i, x) in listOf(12, 50).withIndex()) {
        px(x - 1, top + 9, 32, 34, 0xFF2B2B33, u)
        px(x, top + 10, 30, 32, 0xFFFFF8E4, u)
        val ch = if (i == 0) g.current.digitToChar() else g.next?.digitToChar() ?: '?'
        val color = when {
            i == 0 -> 0xFF2B2B33
            g.lastRight == true -> 0xFF3E8F26
            g.lastRight == false -> 0xFFE0344B
            else -> 0xFF8A8F99
        }
        bigChar(ch, x + 6, top + 14, 6, color, u)
    }
    val pose = when (g.lastRight) {
        true -> CharacterArt.Pose.cheer(pet.species.kind)
        false -> if (pet.species.kind == Kind.HUMAN) CharacterArt.Pose.STAND else CharacterArt.Pose.SICK
        null -> CharacterArt.Pose.STAND
    }
    pal(pet, 46f, ground.toFloat() + 1, u, pose, 16f)
}

// ---------- 21 ----------

/** One playing card, 11 x 15: the rank in big blocky type, or the back if [faceDown]. */
private fun DrawScope.playingCard(rank: Int, x: Int, y: Int, faceDown: Boolean, u: Float) {
    px(x - 1, y - 1, 13, 17, 0xFF2B2B33, u)
    if (faceDown) {
        px(x, y, 11, 15, 0xFF3C7DD9, u)
        for (k in 0 until 5) px(x + 2 + k, y + 3 + k * 2, 3, 1, 0xFF9CC4F2, u)      // a little stripe pattern
        return
    }
    px(x, y, 11, 15, 0xFFFFF8E4, u)
    val red = rank % 2 == 0                                   // alternate red and black, like two suits
    val color = if (red) 0xFFE0344BL else 0xFF2B2B33L
    when (rank) {
        10 -> { bigChar('1', x + 1, y + 5, 1, color, u); bigChar('0', x + 5, y + 5, 1, color, u) }
        else -> bigChar(listOf('A', '2', '3', '4', '5', '6', '7', '8', '9', '?', 'J', 'Q', 'K')[rank - 1], x + 2, y + 3, 2, color, u)
    }
}

/** A hand's total in small digits, e.g. "17". */
private fun DrawScope.smallNumber(n: Int, x: Int, y: Int, color: Long, u: Float) {
    n.toString().forEachIndexed { i, ch -> bigChar(ch, x + i * 4, y, 1, color, u) }
}

private fun DrawScope.twentyOne(g: TwentyOne, pet: PetState, f: Int, top: Int, ground: Int, u: Float) {
    // the pal's hand on top, yours below; up to 6 cards fit in a row
    g.pal.forEachIndexed { i, r -> playingCard(r, 4 + i * 12, top + 6, faceDown = i == 1 && g.hidden, u) }
    g.you.forEachIndexed { i, r -> playingCard(r, 4 + i * 12, top + 28, faceDown = false, u) }
    if (!g.hidden) smallNumber(TwentyOne.total(g.pal), 4 + g.pal.size * 12 + 2, top + 11, 0xFF2B2B33, u)
    val mine = TwentyOne.total(g.you)
    smallNumber(mine, 4 + g.you.size * 12 + 2, top + 33, if (mine > 21) 0xFFE0344BL else 0xFF3E8F26L, u)
    // the pal deals from the side and cheers when it wins a hand
    val pose = if (g.callout == "YOUR PAL WINS" || g.callout == "BUST!") CharacterArt.Pose.cheer(pet.species.kind) else CharacterArt.Pose.wave(pet.species.kind, f)
    pal(pet, 86f, ground.toFloat() + 1, u, pose, 16f, faceLeft = true)
}

// ---------- Slots ----------

private val SLOT_ART = listOf(
    // strawberry
    listOf("...GG....", "..GGGG...", ".RRRRRR..", "RRWRRRRR.", "RRRRRWRR.", ".RRRRRR..", "..RWRR...", "...RR...."),
    // carrot
    listOf("....GG...", "...GGGG..", "..OOOO...", "..OOOO...", "...OOO...", "...OO....", "...OO....", "....O...."),
    // sunflower
    listOf("..Y.Y.Y..", ".YYYYYYY.", "YYBBBBYY.", ".YBBBBY..", "YYBBBBYY.", ".YYYYYYY.", "..Y.G.Y..", "....G...."),
    // tomato
    listOf("...GGG...", "..RGRGR..", ".RRRRRRR.", "RRWRRRRRR", "RRRRRRRRR", "RRRRRRRRR", ".RRRRRRR.", "..RRRRR.."),
    // star
    listOf("....Y....", "...YYY...", "YYYYYYYYY", ".YYYYYYY.", "..YYYYY..", ".YYY.YYY.", ".YY...YY.", "........."),
    // bug
    listOf("..K...K..", "...K.K...", "..PPPPP..", "KPPKPKPPK", "..PPPPP..", "KPPPPPPPK", "..PPPPP..", ".K.....K."),
)
private val SLOT_COLORS = mapOf(
    'R' to 0xFFE0344BL, 'W' to 0xFFFFF0F0L, 'G' to 0xFF4E9E3AL, 'O' to 0xFFF2912DL,
    'Y' to 0xFFFFD84AL, 'B' to 0xFF6B4423L, 'K' to 0xFF2B2B33L, 'P' to 0xFF8E4FC9L,
)

private fun DrawScope.slots(g: Slots, pet: PetState, f: Int, top: Int, ground: Int, u: Float) {
    // the machine: a wooden cabinet with three windows and a lever on the right
    px(12, top + 4, 70, 36, 0xFF8A5A2E, u)
    px(14, top + 6, 66, 32, 0xFFB5824A, u)
    for (i in 0..2) {
        val x = 17 + i * 21
        val y = top + 10
        val hold = g.held == i
        px(x - 1, y - 1, 19, 20, if (hold) 0xFFFFD84AL else 0xFF2B2B33L, u)
        px(x, y, 17, 18, 0xFFFFF8E4, u)
        grid(SLOT_ART[g.reels[i]], SLOT_COLORS, x + 4, y + 5, u)
        if (g.spin[i] > 0 && f % 2 == 0) { px(x, y + 3, 17, 1, 0x55000000L, u); px(x, y + 13, 17, 1, 0x55000000L, u) }   // a blur while it spins
        // HOLD: a gold tag under the window, one dot per pull it stays held
        if (hold) {
            px(x + 2, y + 21, 13, 4, 0xFFFFD84A, u)
            repeat(g.holdPulls) { k -> px(x + 5 + k * 5, y + 22, 2, 2, 0xFF2B2B33, u) }
        }
    }
    // lever: pulled down while the reels spin
    val down = g.spinning()
    px(84, top + 14, 3, 18, 0xFF7D7D85, u)
    px(83, if (down) top + 28 else top + 8, 5, 5, 0xFFE0344B, u)
    // the pal cheers a win from the left
    val pose = if (g.lastWin > 0 && !down) CharacterArt.Pose.cheer(pet.species.kind) else CharacterArt.Pose.STAND
    pal(pet, 6f, ground.toFloat() + 1, u, pose, 14f)
}

// ---------- Scare the Crows ----------

private val CROW = listOf("..KK...", ".KKKKO.", "KKKKKK.", ".KKKK..", "..O.O..")
private val CROW_COLORS = mapOf('K' to 0xFF1B1B22L, 'O' to 0xFFF2912DL)

private fun DrawScope.scareCrows(g: ScareCrows, f: Int, ground: Int, u: Float) {
    val bedY = ground - 8
    for (i in 0 until g.beds) {
        val cx = plotCenter(i)
        px(cx - 7, bedY, 15, 3, 0xFF5C3A1A, u)                                      // soil
        px(cx - 4, bedY - 3, 1, 3, 0xFF5BC236, u); px(cx, bedY - 4, 1, 4, 0xFF5BC236, u); px(cx + 4, bedY - 3, 1, 3, 0xFF5BC236, u)   // sprouts
        val left = g.crow[i]
        if (left > 0) {
            val hop = if (f % 4 < 2) 0 else 1
            grid(CROW, CROW_COLORS, cx - 3, bedY - 9 - hop, u)
            // how long until it pecks: a bar that shrinks and turns red
            px(cx - 5, bedY - 13, 11 * left / 24, 2, if (left < 8) 0xFFE0344BL else 0xFFFFD84AL, u)
        }
    }
    // the scarecrow you move, arms up while it waves
    val cx = plotCenter(g.cursor)
    val y = bedY - 26
    px(cx - 3, y, 7, 2, 0xFFF0D27A, u); px(cx - 2, y - 2, 5, 2, 0xFFF0D27A, u)            // straw hat
    px(cx - 2, y + 2, 5, 4, 0xFFE8C9A0, u); px(cx - 1, y + 3, 1, 1, 0xFF1B1B1B, u); px(cx + 1, y + 3, 1, 1, 0xFF1B1B1B, u)   // face
    px(cx - 2, y + 6, 5, 7, 0xFFC8323C, u)                                             // shirt
    if (g.waving > 0) { px(cx - 7, y + 2, 5, 2, 0xFFC8323C, u); px(cx + 3, y + 2, 5, 2, 0xFFC8323C, u) }   // arms up
    else { px(cx - 7, y + 7, 5, 2, 0xFFC8323C, u); px(cx + 3, y + 7, 5, 2, 0xFFC8323C, u) }
    px(cx, y + 13, 1, 11, 0xFF8A5A2E, u)                                               // the pole
}
