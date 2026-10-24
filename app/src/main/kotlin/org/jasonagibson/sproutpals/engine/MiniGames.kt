package org.jasonagibson.sproutpals.engine

import kotlin.random.Random

/*
 * The Play menu's mini-games, as pure rules (no drawing, no Android) so they can be tested.
 * The screen calls step() 8 times a second and passes the three buttons through:
 *   A = left / higher / move,  B = the action (jump, place, ...),  C = right / lower.
 * Every game ends on its own (time, a miss, a win) and reports a score; the app turns the
 * score into points and a happier pet. Playing is always allowed - even with full hearts.
 */
sealed class MiniGame(val title: String) {
    var over = false
        protected set
    var score = 0
        protected set
    /** One line for the end-of-game card. */
    abstract val result: String
    open fun step() {}
    open fun a() {}
    open fun b() {}
    open fun c() {}

    /** Points earned, rounded so a quick game still gives something; capped so games don't replace care. */
    open fun points(): Int = (score / 2).coerceIn(if (score > 0) 1 else 0, 10)

    companion object {
        val NAMES = listOf("FRUIT CATCH", "JUMP ROPE", "TIC-TAC-TOE", "HIGHER/LOWER", "PLANT & HARVEST")
        fun start(index: Int, rng: Random): MiniGame = when (index) {
            0 -> FruitCatch(rng)
            1 -> JumpRope()
            2 -> TicTacToe(rng)
            3 -> HigherLower(rng)
            else -> PlantHarvest(rng)
        }
    }
}

/** Fruit drops in 7 lanes; move the pet under it with A/C. Rocks: 3 hits and it's over. 30 seconds. */
class FruitCatch(private val rng: Random) : MiniGame("FRUIT CATCH") {
    data class Drop(val lane: Int, var y: Int, val rock: Boolean)

    val lanes = 7
    val floor = 44
    var lane = 3
        private set
    var lives = 3
        private set
    val drops = mutableListOf<Drop>()
    var timeLeft = 30 * 8
        private set
    private var t = 0

    override val result get() = if (lives == 0) "OUCH! CAUGHT $score" else "CAUGHT $score FRUIT!"

    override fun a() { if (!over) lane = (lane - 1).coerceAtLeast(0) }
    override fun c() { if (!over) lane = (lane + 1).coerceAtMost(lanes - 1) }

    override fun step() {
        if (over) return
        t++
        timeLeft--
        // falls faster as time runs out
        val speed = if (timeLeft < 80) 2 else 1
        drops.forEach { it.y += speed }
        val spawnEvery = if (timeLeft < 120) 7 else 10
        if (t % spawnEvery == 0) drops += Drop(rng.nextInt(lanes), 0, rock = rng.nextInt(5) == 0)
        val landed = drops.filter { it.y >= floor }
        for (d in landed) {
            if (d.lane == lane) { if (d.rock) lives-- else score++ }
        }
        drops.removeAll(landed)
        if (lives <= 0 || timeLeft <= 0) over = true
    }
}

/** The rope swings round; press B as it comes to the feet. Each clear jump scores; it speeds up. */
class JumpRope : MiniGame("JUMP ROPE") {
    /** 0..period-1; the rope is at the feet in the last few frames of each swing. */
    var phase = 0
        private set
    var period = 16
        private set
    /** Frames left in the air (0 = on the ground). */
    var air = 0
        private set
    private var landedThisSwing = false

    override val result get() = if (score == 0) "TRIPPED!" else "$score JUMPS IN A ROW!"
    override fun points() = (score / 3).coerceIn(if (score > 0) 1 else 0, 10)

    fun ropeAtFeet() = phase >= period - 3

    override fun b() { if (!over && air == 0) air = 5 }

    override fun step() {
        if (over) return
        if (air > 0) air--
        phase++
        if (ropeAtFeet() && air == 0 && phase == period - 1) { over = true; return }   // caught the feet
        if (phase >= period) {
            phase = 0
            score++
            // every 5 jumps the rope gets quicker, down to a floor
            if (score % 5 == 0 && period > 9) period--
        }
    }
}

/** You are X, your pal is O. A moves the cursor, B places. Your pal plays a fair, beatable game. */
class TicTacToe(private val rng: Random) : MiniGame("TIC-TAC-TOE") {
    val board = CharArray(9) { ' ' }
    var cursor = 4
        private set
    var winner = ' '
        private set
    private var palThinking = 0

    override val result get() = when (winner) { 'X' -> "YOU WIN!"; 'O' -> "YOUR PAL WINS!"; else -> "A DRAW!" }
    override fun points() = when (winner) { 'X' -> 3; ' ' -> 1; else -> 1 }

    override fun a() { if (!over) cursor = nextFree(cursor) }
    override fun c() { if (!over) cursor = prevFree(cursor) }

    override fun b() {
        if (over || palThinking > 0 || board[cursor] != ' ') return
        board[cursor] = 'X'
        if (!finish()) palThinking = 6        // the pal "thinks" for a moment before moving
    }

    override fun step() {
        if (over || palThinking == 0) return
        palThinking--
        if (palThinking == 0) {
            board[palMove()] = 'O'
            if (!finish()) cursor = nextFree(cursor - 1)
        }
    }

    private fun finish(): Boolean {
        val w = winnerOf(board)
        if (w != ' ' || board.none { it == ' ' }) {
            winner = w
            score = if (w == 'X') 3 else 1
            over = true
        }
        return over
    }

    /** Win if it can, block you if it must, otherwise the centre, a corner, or anything - with a little slack. */
    private fun palMove(): Int {
        val free = board.indices.filter { board[it] == ' ' }
        fun winsWith(i: Int, c: Char) = board.copyOf().also { it[i] = c }.let { winnerOf(it) == c }
        free.firstOrNull { winsWith(it, 'O') }?.let { return it }
        // 1 time in 4 it forgets to block - your pal is a pet, not a computer
        if (rng.nextInt(4) != 0) free.firstOrNull { winsWith(it, 'X') }?.let { return it }
        if (4 in free) return 4
        return free.filter { it in listOf(0, 2, 6, 8) }.ifEmpty { free }.random(rng)
    }

    private fun nextFree(from: Int): Int {
        for (k in 1..9) { val i = (from + k).mod(9); if (board[i] == ' ') return i }
        return from
    }

    private fun prevFree(from: Int): Int {
        for (k in 1..9) { val i = (from - k).mod(9); if (board[i] == ' ') return i }
        return from
    }

    companion object {
        private val LINES = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
        fun winnerOf(b: CharArray): Char =
            LINES.firstOrNull { (x, y, z) -> b[x] != ' ' && b[x] == b[y] && b[y] == b[z] }?.let { b[it[0]] } ?: ' '
    }
}

/**
 * Five plants grow along the bed, each at its own pace. A/C move along the row, B picks.
 * Ripe (70-90) scores 1, perfect (78-86) scores 2; too early wastes it, past 100 it rots.
 * A picked or rotten plant is re-sown. 30 seconds.
 */
class PlantHarvest(private val rng: Random) : MiniGame("PLANT & HARVEST") {
    val plots = 5
    /** 0..100+ per plot; 70-90 ripe, 78-86 perfect, over 100 rotten. */
    val growth = IntArray(plots) { rng.nextInt(0, 40) }
    private val speed = IntArray(plots) { rng.nextInt(1, 4) }
    var cursor = 2
        private set
    var timeLeft = 30 * 8
        private set
    var perfects = 0
        private set
    var rotted = 0
        private set
    /** What the last pick was, for a moment on screen: "PERFECT!", "RIPE", "TOO EARLY", "ROTTEN". */
    var callout = ""
        private set
    private var calloutFor = 0

    override val result get() = "PICKED $score ★ WORTH" + if (perfects > 0) " ($perfects PERFECT)" else ""
    override fun points() = (score / 2).coerceIn(if (score > 0) 1 else 0, 10)

    override fun a() { if (!over) cursor = (cursor - 1).coerceAtLeast(0) }
    override fun c() { if (!over) cursor = (cursor + 1).coerceAtMost(plots - 1) }

    override fun b() {
        if (over) return
        val g = growth[cursor]
        when {
            g in 78..86 -> { score += 2; perfects++; say("PERFECT!") }
            g in 70..90 -> { score += 1; say("RIPE") }
            g > 100 -> { rotted++; say("ROTTEN") }
            else -> say("TOO EARLY")
        }
        resow(cursor)
    }

    override fun step() {
        if (over) return
        timeLeft--
        if (calloutFor > 0 && --calloutFor == 0) callout = ""
        for (i in 0 until plots) {
            growth[i] += speed[i]
            if (growth[i] > 130) { rotted++; resow(i) }   // left far too long: it withers away
        }
        if (timeLeft <= 0) over = true
    }

    private fun resow(i: Int) { growth[i] = 0; speed[i] = rng.nextInt(1, 4) }
    private fun say(s: String) { callout = s; calloutFor = 8 }
}

/** The 90s classic: a number 1-9 shows; will the next one be higher (A) or lower (C)? Five rounds. */
class HigherLower(private val rng: Random) : MiniGame("HIGHER/LOWER") {
    var current = rng.nextInt(1, 10)
        private set
    var next: Int? = null
        private set
    var round = 1
        private set
    var lastRight: Boolean? = null
        private set
    private var reveal = 0
    val rounds = 5

    override val result get() = "$score OUT OF $rounds RIGHT!"
    override fun points() = score

    override fun a() = guess(higher = true)
    override fun c() = guess(higher = false)

    private fun guess(higher: Boolean) {
        if (over || reveal > 0) return
        var n: Int
        do { n = rng.nextInt(1, 10) } while (n == current)
        next = n
        val right = if (higher) n > current else n < current
        lastRight = right
        if (right) score++
        reveal = 10                     // show the answer for a moment
    }

    override fun step() {
        if (over || reveal == 0) return
        reveal--
        if (reveal == 0) {
            current = next ?: current
            next = null
            lastRight = null
            if (round >= rounds) over = true else round++
        }
    }
}
