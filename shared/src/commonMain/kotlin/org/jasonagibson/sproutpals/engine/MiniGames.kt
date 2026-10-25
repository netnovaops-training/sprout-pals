package org.jasonagibson.sproutpals.engine

import kotlin.random.Random

/*
 * The Play menu's mini-games, as pure rules (no drawing, no Android) so they can be tested.
 * The screen calls step() 8 times a second and passes the three buttons through:
 *   A = left / higher / move,  B = the action (jump, place, ...),  C = right / lower.
 * The table games (21, SLOTS) have nothing to move, so there A deals or pulls and C cashes out (SLOTS: leaves).
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
        val NAMES = listOf("FRUIT CATCH", "JUMP ROPE", "TIC-TAC-TOE", "HIGHER/LOWER", "PLANT & HARVEST", "21", "SLOTS", "SCARE THE CROWS")
        /** [stars] is the player's balance; only SLOTS spends it. */
        fun start(index: Int, rng: Random, stars: Int = 0): MiniGame = when (index) {
            0 -> FruitCatch(rng)
            1 -> JumpRope()
            2 -> TicTacToe(rng)
            3 -> HigherLower(rng)
            4 -> PlantHarvest(rng)
            5 -> TwentyOne(rng)
            6 -> Slots(rng, stars)
            else -> ScareCrows(rng)
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

/**
 * 21 against your pal, five hands. Get closer to 21 than the pal without going over.
 * A = another card, B = stay, C = cash out early. Aces count 11 or 1, picture cards 10.
 * The pal shows its second card when you stay, then draws to 17, one card at a time.
 * A win scores 2, 21 in your first two cards 3, a tie 1.
 */
class TwentyOne(private val rng: Random) : MiniGame("21") {
    val hands = 5
    var played = 0
        private set
    var wins = 0
        private set
    /** Card ranks 1 (ace) to 13 (king). */
    val you = mutableListOf<Int>()
    val pal = mutableListOf<Int>()
    /** The pal's second card stays face down until you stay. */
    var hidden = true
        private set
    /** How the last hand went ("YOU WIN!", "BUST!", ...); blank while the hand is being played. */
    var callout = ""
        private set
    private var palTurn = false
    /** Frames until the pal's next card, or until the next hand is dealt. */
    private var wait = 0

    init { deal() }

    override val result get() = "WON $wins OF $played HANDS"

    fun yourTurn() = !over && !palTurn && callout.isEmpty()

    override fun a() {
        if (!yourTurn()) return
        you += card()
        when {
            total(you) > 21 -> settle()
            total(you) == 21 -> stay()
        }
    }

    override fun b() { if (yourTurn()) stay() }
    override fun c() { over = true }

    override fun step() {
        if (over || wait == 0 || --wait > 0) return
        when {
            callout.isNotEmpty() -> if (played >= hands) over = true else deal()
            palTurn -> if (total(pal) < 17) { pal += card(); wait = PAUSE } else settle()
        }
    }

    private fun card() = rng.nextInt(1, 14)

    private fun deal() {
        you.clear(); pal.clear()
        you += card(); pal += card(); you += card(); pal += card()
        hidden = true
        callout = ""
        palTurn = false
        if (total(you) == 21) stay()
    }

    private fun stay() { palTurn = true; hidden = false; wait = PAUSE }

    private fun settle() {
        palTurn = false
        hidden = false
        val y = total(you)
        val p = total(pal)
        val natural = y == 21 && you.size == 2
        callout = when {
            y > 21 -> "BUST!"
            natural && !(p == 21 && pal.size == 2) -> { score += 3; wins++; "21! BLACKJACK!" }
            p > 21 || y > p -> { score += 2; wins++; "YOU WIN!" }
            y == p -> { score += 1; "A TIE" }
            else -> "YOUR PAL WINS"
        }
        played++
        wait = SHOW
    }

    companion object {
        private const val PAUSE = 6
        private const val SHOW = 16

        /** Best total for a hand: aces are 11 unless that would go over 21. */
        fun total(cards: List<Int>): Int {
            val hard = cards.sumOf { minOf(it, 10) }
            return if (1 in cards && hard + 10 <= 21) hard + 10 else hard
        }
    }
}

/**
 * A garden slot machine. Each pull (A) costs 10 stars out of the player's stars; under 10, A does
 * nothing and the screen says so. B holds a reel (each press moves the hold to the next reel,
 * then off). A held reel stays put for the next 2 pulls, then lets go, and needs one free pull
 * before anything can be held again. C leaves.
 * Pays per pull: any pair 20, three alike 50, three stars 100; bugs never pay.
 *
 * The result of a pull is decided the moment the lever is pulled, so the cost and the prize move
 * together as ONE change ([takeDelta]); the app applies it to the save in a single commit
 * (never "deduct now, pay later", which is how a save race could eat stars).
 */
class Slots(private val rng: Random, stars: Int = 0) : MiniGame("SLOTS") {
    val reels = IntArray(3) { roll() }
    /** Frames each reel keeps spinning (0 = stopped). */
    val spin = IntArray(3)
    private val finalReels = IntArray(3)
    /** The player's stars as this game sees them (start + net of every pull). */
    var stars = stars
        private set
    /** Net stars won or lost over the whole game. */
    var net = 0
        private set
    /** True after a pull was refused for lack of stars; cleared by the next good pull. */
    var needStars = false
        private set
    private var delta = 0
    private var pullsMade = 0
    private var pendingWin = 0
    /** The reel being held (0-2) or -1, and how many more pulls it stays held. */
    var held = -1
        private set
    var holdPulls = 0
        private set
    /** What the last pull paid, for the screen. */
    var lastWin = 0
        private set
    /** False right after a hold runs out, until one pull without a hold. */
    private var canHold = true

    override val result get() = if (net >= 0) "SLOTS: UP $net ★!" else "SLOTS: DOWN ${-net} ★"
    /** The stars already moved pull by pull, so the end card adds nothing. */
    override fun points() = 0

    fun spinning() = spin.any { it > 0 }

    /** Stars changed since the last call (cost and prize of any pulls); the app commits it at once. */
    fun takeDelta(): Int = delta.also { delta = 0 }

    override fun a() {
        if (over || spinning()) return
        if (stars < COST) { needStars = true; return }
        needStars = false
        pullsMade++
        lastWin = 0
        for (i in 0..2) finalReels[i] = if (i == held) reels[i] else roll()
        val win = payout(finalReels[0], finalReels[1], finalReels[2])
        pendingWin = win
        stars += win - COST; net += win - COST; delta += win - COST; score = net.coerceAtLeast(0)
        for (i in 0..2) if (i != held) spin[i] = 8 + 4 * i      // the reels stop left to right
        if (held < 0) canHold = true
        else if (--holdPulls == 0) { held = -1; canHold = false }
    }

    override fun b() {
        // only between pulls, after the first one, and a hold can't be moved once it has been used
        if (over || spinning() || pullsMade == 0 || !canHold) return
        if (held >= 0 && holdPulls < HOLD_PULLS) return
        held = if (held == 2) -1 else held + 1
        holdPulls = if (held >= 0) HOLD_PULLS else 0
    }

    override fun c() { over = true }

    override fun step() {
        if (over) return
        if (!spinning()) return
        for (i in 0..2) if (spin[i] > 0) { spin[i]--; reels[i] = if (spin[i] == 0) finalReels[i] else roll() }
        if (!spinning()) lastWin = pendingWin
    }

    /** Out of 100: four crops 19 each, star 8, bug 16 - bugs and stars rarer than crops. */
    private fun roll(): Int {
        val n = rng.nextInt(100)
        return when {
            n < 76 -> n / 19
            n < 84 -> STAR
            else -> BUG
        }
    }

    companion object {
        /** Symbols: 0 strawberry, 1 carrot, 2 sunflower, 3 tomato, 4 star, 5 bug. */
        const val STAR = 4
        const val BUG = 5
        const val HOLD_PULLS = 2
        /** Stars a pull costs. */
        const val COST = 10

        fun payout(a: Int, b: Int, c: Int): Int = when {
            a == b && b == c -> when (a) { STAR -> 100; BUG -> 0; else -> 50 }
            a == b || a == c -> if (a == BUG) 0 else 20
            b == c -> if (b == BUG) 0 else 20
            else -> 0
        }
    }
}

/**
 * Crows land on the five beds. Walk the scarecrow along with A / C and press B to wave a crow
 * off before it pecks (after 3 seconds; quicker in the last stretch). 30 seconds; 5 pecks ends it.
 */
class ScareCrows(private val rng: Random) : MiniGame("SCARE THE CROWS") {
    val beds = 5
    /** Frames until the crow on each bed pecks; 0 = no crow there. */
    val crow = IntArray(beds)
    var cursor = 2
        private set
    var pecks = 0
        private set
    val maxPecks = 5
    var timeLeft = 30 * 8
        private set
    /** Frames the scarecrow's arms stay up after B (for the drawing). */
    var waving = 0
        private set
    private var t = 0

    override val result get() = if (pecks >= maxPecks) "THE CROWS GOT IN! SHOOED $score" else "SHOOED $score CROWS!"

    override fun a() { if (!over) cursor = (cursor - 1).coerceAtLeast(0) }
    override fun c() { if (!over) cursor = (cursor + 1).coerceAtMost(beds - 1) }

    override fun b() {
        if (over) return
        waving = 3
        if (crow[cursor] > 0) { crow[cursor] = 0; score++ }
    }

    override fun step() {
        if (over) return
        t++
        timeLeft--
        if (waving > 0) waving--
        for (i in 0 until beds) if (crow[i] > 0 && --crow[i] == 0) pecks++     // too slow: it pecks and flies off
        val late = timeLeft < 100
        if (t % (if (late) 7 else 11) == 0) {
            val free = (0 until beds).filter { crow[it] == 0 }
            if (free.isNotEmpty()) crow[free.random(rng)] = if (late) 18 else 24
        }
        if (pecks >= maxPecks || timeLeft <= 0) over = true
    }
}
