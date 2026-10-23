package org.jasonagibson.sproutpals.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jasonagibson.sproutpals.engine.Outcome
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.PetState
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.engine.Stage
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Steps
import org.jasonagibson.sproutpals.engine.Stop
import org.jasonagibson.sproutpals.engine.Walk
import org.jasonagibson.sproutpals.engine.Walking
import java.time.ZoneId
import kotlin.random.Random

/** The 7 menu icons you can pick with A. Index 7 on screen is the attention light (not pickable). */
enum class MenuItem { STATUS, FEED, LIGHT, PLAY, MEDICINE, CLEAN, GARDEN }

/** Short animation shown after a care action. */
enum class Effect { NONE, MEAL, SNACK, PILL, CLEAN, HAPPY, NO, SHAKE }

/** What the pet is doing when nobody's pressing buttons. WALK = walking along with you. */
enum class Move { WANDER, HOP, DANCE, SPIN, RUN, REST, GARDEN, BALL, WALK }

/** What the middle of the screen is showing on top of the pet. */
sealed interface Panel {
    data object None : Panel
    data class Feed(val choice: Int = 0) : Panel
    data class Status(val page: Int = 0) : Panel
    data class Message(val text: String) : Panel
    data object Shop : Panel
    data object Garden : Panel
    data object Ended : Panel
    data class Update(val info: Updater.Available, val progress: Int? = null, val error: String? = null) : Panel
}

/**
 * Screen state + button handling. The rules themselves live in [PetEngine];
 * this class only decides what each button press means right now.
 */
class Game(initial: Save, private val persist: (Save) -> Unit) {
    var save by mutableStateOf(initial); private set
    var selected by mutableIntStateOf(-1); private set
    var panel by mutableStateOf<Panel>(Panel.None); private set
    var effect by mutableStateOf(Effect.NONE); private set
    var frame by mutableIntStateOf(0); private set
    var petX by mutableIntStateOf(0); private set
    /** Pixels the pet is lifted off the ground this frame (hops, dance bounce). */
    var petLift by mutableIntStateOf(0); private set
    var facingLeft by mutableStateOf(false); private set
    var move by mutableStateOf(Move.WANDER); private set
    /** True on frames where the pet actually changed position (for walking poses). */
    var moving by mutableStateOf(false); private set
    /** Out-walking state from the pedometer: drives walk mode and the excited egg. */
    var walk by mutableStateOf(Walk()); private set

    private var moveUntil = 0L
    private var wanderTo = 0
    /** Gardening: the spot next to the bed, and how far the seedling has grown (0-3). */
    val gardenTo = 20
    var gardenPhase by mutableIntStateOf(0); private set
    /** What's left growing in the bed between gardening sessions. */
    var lastSeedling by mutableIntStateOf(0); private set
    private var lastReading = -1L

    private var effectUntil = 0L
    private var messageUntil = 0L
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val rng = Random.Default

    val pet: PetState get() = save.pet
    fun now() = System.currentTimeMillis() + save.timeOffset

    // ---------- clock ----------

    /** Walking with you: 2+ minutes of steps, until 2 minutes after the last one. */
    val walkMode: Boolean get() = Walking.showWalk(walk, System.currentTimeMillis()) && pet.stage != Stage.EGG && !pet.sleeping && pet.ended == null
    val walkingNow: Boolean get() = Walking.movingNow(walk, System.currentTimeMillis())

    /** Party hat the day before growing up. */
    val birthdaySoon: Boolean get() {
        val h = pet.hatchedAt ?: return false
        return pet.ended == null && Rules.birthdaySoon(now() - h)
    }

    /** Called 8 times a second: animation, movement, and the life engine. */
    fun tick(realNow: Long) {
        frame++
        if (realNow > effectUntil) effect = Effect.NONE
        if (panel is Panel.Message && realNow > messageUntil) panel = Panel.None

        val beforeX = petX
        animate(realNow)
        moving = petX != beforeX

        val before = pet
        val after = PetEngine.advance(before, now(), zone, rng)
        if (after != before) {
            var s = save.copy(pet = after)
            if (before.stage != after.stage && after.ended == null) {
                show("${after.species.title.uppercase()}\nGREW UP!", 3000)
            }
            if (after.ended != null && save.memories.none { it.bornAt == after.bornAt }) {
                val bonus = if (after.ended == org.jasonagibson.sproutpals.engine.EndReason.NATURAL) Rules.POINTS_FULL_LIFE else 0
                s = s.copy(memories = s.memories + PetEngine.memoryOf(after), points = s.points + bonus)
                panel = Panel.Ended
            }
            commit(s)
        }
    }

    // ---------- movement: a little routine picked every few seconds ----------

    private fun animate(realNow: Long) {
        petLift = 0
        if (pet.stage == Stage.EGG || pet.sleeping || pet.ended != null || effect != Effect.NONE) return
        val f = frame
        if (walkMode) {
            // walking with you: drift back to centre, face forward, bounce in step
            move = Move.WALK
            petX += if (petX < 0) 1 else if (petX > 0) -1 else 0
            facingLeft = false
            petLift = STRIDE[f % STRIDE.size]
            return
        }
        if (move == Move.WALK || realNow > moveUntil) pickMove(realNow)
        when (move) {
            Move.WANDER -> {
                // stroll to a spot, look around for a moment, pick another spot
                if (petX == wanderTo) {
                    if (f % 12 == 0) wanderTo = (MIN_X..MAX_X).random()
                } else {
                    stepX(if (wanderTo < petX) -1 else 1)
                    petLift = STRIDE[f % STRIDE.size]
                }
            }
            Move.HOP -> {
                stepX(if (facingLeft) -1 else 1)
                petLift = HOP[f % HOP.size]
            }
            Move.DANCE -> {
                if (f % 4 == 0) facingLeft = !facingLeft
                petLift = BOUNCE[f % BOUNCE.size]
                petX += if (f % 8 < 4) 1 else -1
            }
            Move.SPIN -> { if (f % 2 == 0) facingLeft = !facingLeft; petLift = BOUNCE[f % BOUNCE.size] }
            Move.RUN -> { stepX(if (facingLeft) -2 else 2); petLift = STRIDE[f % STRIDE.size] }
            Move.GARDEN -> {
                // walk over to the bed, then dig and plant: the seedling grows a step every ~2 s
                if (petX != gardenTo) {
                    stepX(if (gardenTo < petX) -1 else 1)
                    petLift = STRIDE[f % STRIDE.size]
                } else {
                    facingLeft = false
                    petLift = DIG[f % DIG.size]
                    if (f % 16 == 0 && gardenPhase < 3) { gardenPhase++; lastSeedling = gardenPhase }
                }
            }
            Move.BALL -> { facingLeft = true; petLift = HOP[f % HOP.size] }
            Move.REST, Move.WALK -> Unit
        }
    }

    /** Moves sideways, turning around at the edges of the garden. */
    private fun stepX(dx: Int) {
        val next = petX + dx
        if (next < MIN_X || next > MAX_X) { facingLeft = !facingLeft; return }
        petX = next
        if (dx < 0) facingLeft = true else if (dx > 0) facingLeft = false
    }

    private fun pickMove(realNow: Long) {
        val low = pet.sick || pet.happy == 0 || pet.hunger == 0
        val choices = when {
            low -> listOf(Move.REST, Move.REST, Move.WANDER)
            pet.stage == Stage.BABY -> listOf(Move.WANDER, Move.HOP, Move.HOP, Move.DANCE, Move.BALL, Move.REST)
            pet.stage == Stage.OLD -> listOf(Move.WANDER, Move.WANDER, Move.REST, Move.DANCE, Move.GARDEN)
            else -> listOf(Move.WANDER, Move.HOP, Move.DANCE, Move.SPIN, Move.RUN, Move.GARDEN, Move.GARDEN, Move.BALL, Move.REST)
        }
        // Just back from a walk: it heads for the garden first.
        move = if (this.move == Move.WALK && !low) Move.GARDEN else choices.random()
        if (move == Move.HOP || move == Move.RUN) facingLeft = petX > 0
        if (move == Move.WANDER) wanderTo = (MIN_X..MAX_X).random()
        if (move == Move.GARDEN) { gardenPhase = 0; lastSeedling = 0 }
        moveUntil = realNow + if (move == Move.GARDEN) 14_000L else (3_000L..6_000L).random()
    }

    /** One jolt from shaking the phone (or B in debug builds). Three jolts open the egg. */
    fun onShake() {
        val before = pet
        if (before.stage != Stage.EGG || before.ended != null) return
        val after = PetEngine.shake(before, now())
        commit(save.copy(pet = after))
        play(Effect.SHAKE)
        if (after.stage == Stage.BABY) {
            val name = after.species.title.uppercase()
            onSound(Chiptune.Sfx.HATCH)
            show(if (after.species.rare) "★ RARE! ★\nIT'S $name!" else "IT HATCHED!\nIT'S $name!", 4000)
        }
    }

    /** New reading from the phone's pedometer. */
    fun onSteps(reading: Long) {
        // Only a rising count means "walking now"; the first reading after opening is just the total so far.
        if (lastReading in 0 until reading) walk = Walking.onStep(walk, System.currentTimeMillis())
        lastReading = reading
        val (next, arrived) = Route.collect(Steps.apply(save, reading))
        if (next != save) commit(next)
        if (arrived.isNotEmpty()) {
            val stop = arrived.last()
            show("YOU WALKED TO\n${stop.name.uppercase()}!\n\nFOUND:\n${stop.prize.uppercase()}", 6000)
            onArrive(arrived)
            onSound(Chiptune.Sfx.ARRIVE)
        }
    }

    // ---------- sound ----------

    /** Set by the activity; only called when sound is on. */
    var onSoundOut: (Chiptune.Sfx) -> Unit = {}
    /** Set by the activity: start (true) or stop (false) the background tune. */
    var onMusic: (Boolean) -> Unit = {}

    fun onSound(sfx: Chiptune.Sfx) { if (save.soundOn) onSoundOut(sfx) }

    fun toggleSound() = commit(save.copy(soundOn = !save.soundOn))

    fun toggleMusic() {
        commit(save.copy(musicOn = !save.musicOn))
        onMusic(save.musicOn)
    }

    /** Set by the activity: buzz + notification when a garden shop is reached. */
    var onArrive: (List<Stop>) -> Unit = {}

    /** Debug builds (long-press A): pretend you've been walking for 3 minutes. */
    fun debugWalk() {
        val t = System.currentTimeMillis()
        walk = Walk(startedAt = t - 3 * Rules.MINUTE, lastStepAt = t)
    }

    /** Debug builds (long-press C): jump the clock to 2 hours before the next birthday. */
    fun debugBirthday() {
        // Moves the hatch time back instead of the clock forward, so the pet doesn't
        // live through days of nobody feeding it.
        val h = pet.hatchedAt ?: return
        val age = now() - h
        val next = listOf(Rules.KID_AT, Rules.ADULT_AT, Rules.OLD_AT).firstOrNull { it > age } ?: return
        commit(save.copy(pet = pet.copy(hatchedAt = h - (next - 2 * Rules.HOUR - age))))
    }

    /** Debug builds: long-press the title to jump an hour ahead. */
    fun timeWarp() = commit(save.copy(timeOffset = save.timeOffset + Rules.HOUR))

    fun offerUpdate(info: Updater.Available) { if (panel == Panel.None) panel = Panel.Update(info) }
    fun updateProgress(p: Int) { (panel as? Panel.Update)?.let { panel = it.copy(progress = p) } }
    fun updateFailed(msg: String) { (panel as? Panel.Update)?.let { panel = it.copy(error = msg, progress = null) } }

    // ---------- buttons ----------

    /** A: next icon, or next choice inside a panel. */
    fun pressA() {
        when (val p = panel) {
            is Panel.Feed -> panel = p.copy(choice = (p.choice + 1) % 2)
            is Panel.Status -> panel = p.copy(page = (p.page + 1) % STATUS_PAGES)
            Panel.None -> selected = (selected + 1) % MenuItem.entries.size
            else -> Unit
        }
    }

    /** B: choose. Returns true when the update should start downloading. */
    fun pressB(): Boolean {
        when (val p = panel) {
            is Panel.Feed -> { if (p.choice == 0) care(PetEngine::feedMeal, Effect.MEAL) else care(PetEngine::feedSnack, Effect.SNACK); return false }
            is Panel.Status -> { panel = p.copy(page = (p.page + 1) % STATUS_PAGES); return false }
            Panel.Ended -> { newEgg(); return false }
            is Panel.Update -> return p.progress == null
            Panel.None -> Unit
            else -> { panel = Panel.None; return false }
        }
        when (MenuItem.entries.getOrNull(selected)) {
            MenuItem.STATUS -> panel = Panel.Status()
            MenuItem.FEED -> panel = if (canInteract()) Panel.Feed() else blocked()
            MenuItem.LIGHT -> {
                commit(save.copy(pet = PetEngine.toggleLights(pet)))
                show(if (pet.lightsOn) "LIGHTS ON" else "LIGHTS OFF", 1200)
            }
            MenuItem.PLAY -> care(PetEngine::play, Effect.HAPPY)
            MenuItem.MEDICINE -> care(PetEngine::medicine, Effect.PILL)
            MenuItem.CLEAN -> care(PetEngine::clean, Effect.CLEAN)
            MenuItem.GARDEN -> panel = Panel.Garden
            null -> Unit
        }
        return false
    }

    /** C: back / cancel. */
    fun pressC() {
        if (panel is Panel.Ended) return
        if (panel != Panel.None) panel = Panel.None else selected = -1
    }

    fun pressShop() { if (panel !is Panel.Ended && panel !is Panel.Update) panel = Panel.Shop }

    // ---------- care ----------

    private fun canInteract() = pet.ended == null && pet.stage != Stage.EGG && !pet.sleeping

    private fun blocked(): Panel = Panel.Message(
        when {
            pet.stage == Stage.EGG -> "SHAKE TO OPEN!"
            pet.sleeping -> "ZZZ... ASLEEP"
            else -> "..."
        }
    ).also { messageUntil = System.currentTimeMillis() + 1500 }

    private fun care(action: (PetState) -> Pair<PetState, Outcome>, fx: Effect) {
        val before = pet
        val (after, outcome) = action(before)
        when (outcome) {
            Outcome.OK -> {
                var earned = 0
                when (fx) {
                    Effect.MEAL -> if (before.hunger <= 1) earned = Rules.POINTS_FEED_HUNGRY
                    Effect.CLEAN -> earned = Rules.POINTS_CLEAN
                    Effect.PILL -> if (before.sick && !after.sick) earned = Rules.POINTS_CURE
                    Effect.HAPPY -> earned = Rules.POINTS_PLAY
                    else -> Unit
                }
                commit(save.copy(pet = after, points = save.points + earned))
                panel = Panel.None
                play(fx)
                if (fx == Effect.PILL) show(if (after.sick) "ONE MORE DOSE" else "ALL BETTER!", 1500)
            }
            Outcome.NOT_NEEDED -> {
                play(Effect.NO)
                show(when (fx) { Effect.MEAL -> "I'M FULL!"; Effect.CLEAN -> "ALREADY CLEAN"; Effect.PILL -> "NOT SICK"; Effect.HAPPY -> "SO HAPPY ALREADY"; else -> "NO THANKS" }, 1500)
            }
            Outcome.ASLEEP, Outcome.CANT -> panel = blocked()
        }
    }

    private fun play(fx: Effect) {
        effect = fx
        effectUntil = System.currentTimeMillis() + 2000
        if (fx != Effect.NO) petX = 0
    }

    private fun show(text: String, ms: Long) {
        panel = Panel.Message(text)
        messageUntil = System.currentTimeMillis() + ms
    }

    private fun newEgg() {
        commit(save.copy(pet = PetEngine.newPet(now(), rng)))
        panel = Panel.None
        selected = -1
    }

    private fun commit(s: Save) {
        save = s
        persist(s)
    }

    companion object {
        const val STATUS_PAGES = 5
        /** Screen pixels left/right of home the pet may roam (sprites are 32 wide on a 96-wide screen). */
        const val MIN_X = -30
        const val MAX_X = 30
        /** Lift patterns, one entry per frame at 8 frames a second. */
        private val STRIDE = listOf(0, 1, 2, 1, 0, 1, 2, 1)
        private val HOP = listOf(0, 2, 4, 5, 6, 5, 3, 1)
        private val BOUNCE = listOf(0, 1, 2, 3, 2, 1, 0, 0)
        private val DIG = listOf(0, 0, 1, 2, 2, 1, 0, 0)
    }
}
