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
import org.jasonagibson.sproutpals.engine.TicTacToe
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
    }
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
