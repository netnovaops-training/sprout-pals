package org.jasonagibson.sproutpals.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jasonagibson.sproutpals.engine.Achievements
import org.jasonagibson.sproutpals.engine.Buddy
import org.jasonagibson.sproutpals.engine.Species
import org.jasonagibson.sproutpals.engine.Garden
import org.jasonagibson.sproutpals.engine.MiniGame
import org.jasonagibson.sproutpals.engine.PlotState
import org.jasonagibson.sproutpals.engine.Outcome
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.PetState
import org.jasonagibson.sproutpals.engine.Rules
import org.jasonagibson.sproutpals.engine.Save
import org.jasonagibson.sproutpals.engine.Stage
import org.jasonagibson.sproutpals.engine.Route
import org.jasonagibson.sproutpals.engine.Shop
import org.jasonagibson.sproutpals.engine.ShopResult
import org.jasonagibson.sproutpals.engine.ShopTab
import org.jasonagibson.sproutpals.engine.Steps
import org.jasonagibson.sproutpals.engine.Stop
import org.jasonagibson.sproutpals.engine.Walk
import org.jasonagibson.sproutpals.engine.Visitors
import org.jasonagibson.sproutpals.engine.Weather
import org.jasonagibson.sproutpals.engine.WeatherEvent
import org.jasonagibson.sproutpals.engine.WeatherKind
import org.jasonagibson.sproutpals.engine.Walking
import kotlinx.datetime.TimeZone
import kotlin.random.Random

/** The 7 menu icons you can pick with A. Index 7 on screen is the attention light (not pickable). */
enum class MenuItem { STATUS, FEED, LIGHT, PLAY, MEDICINE, CLEAN, GARDEN }

/** Short animation shown after a care action. */
enum class Effect { NONE, MEAL, SNACK, PILL, CLEAN, HAPPY, NO, SHAKE, HATCH }

/** What the pet is doing when nobody's pressing buttons. WALK = walking along with you. */
enum class Move { WANDER, HOP, DANCE, SPIN, RUN, REST, GARDEN, BALL, WALK }

/** What the middle of the screen is showing on top of the pet. */
sealed interface Panel {
    data object None : Panel
    data class Feed(val choice: Int = 0) : Panel
    data class Status(val page: Int = 0) : Panel
    data class Message(val text: String) : Panel
    /** The shop, one item at a time: A next, B buy / bring out / put away, SHOP other page, C close. */
    data class Shop(val tab: ShopTab = ShopTab.CREATURES, val choice: Int = 0, val note: String = "") : Panel
    /**
     * Friends: A next, B pick. With a friend picked ([friend] set), A/B choose an action:
     * visit for 1/2/4 hours or send a heart. C goes back.
     */
    data class Friends(val choice: Int = 0, val friend: String? = null, val note: String = "") : Panel
    /** The garden bed: A next plot, B plant / water / harvest, C close. [picking] = choosing a seed. */
    data class Garden(val plot: Int = 0, val picking: Boolean = false, val seed: Int = 0, val note: String = "") : Panel
    /** The trophy list, scrolled by A. */
    data class Trophies(val top: Int = 0) : Panel
    /** The memory book: one past pal per page, A turns the page. */
    data class Book(val page: Int = 0) : Panel
    /** Pick a game: A next, B start, C back. */
    data class PlayMenu(val choice: Int = 0) : Panel
    /** "How to play" card for the chosen game; B starts it. */
    data class HowTo(val game: Int) : Panel
    /** End-of-game card: what happened and what you earned. */
    data class GameOver(val text: String) : Panel
    data object Ended : Panel
    data class Update(val info: UpdateInfo, val progress: Int? = null, val error: String? = null) : Panel
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
    /** Gardening: the spot just left of the bed, so the pet doesn't hide the crops. */
    val gardenTo = 4
    private var lastReading = -1L

    private var effectUntil = 0L
    /** The frame the current effect started on (for effects that play out over time). */
    var effectFrame = 0; private set
    /** A line shown under the garden for a few seconds (e.g. naming a newly hatched baby). */
    var caption by mutableStateOf<String?>(null); private set
    private var captionUntil = 0L
    private var messageUntil = 0L
    private val zone: TimeZone get() = localZone()
    private val rng = Random.Default

    val pet: PetState get() = save.pet
    fun now() = nowMillis() + save.timeOffset

    // ---------- clock ----------

    /** Walking with you: 2+ minutes of steps, until 2 minutes after the last one. */
    val walkMode: Boolean get() = Walking.showWalk(walk, nowMillis()) && pet.stage != Stage.EGG && !pet.sleeping && pet.ended == null
    val walkingNow: Boolean get() = Walking.movingNow(walk, nowMillis())

    /** Party hat the day before growing up. */
    val birthdaySoon: Boolean get() {
        val h = pet.hatchedAt ?: return false
        return pet.ended == null && Rules.birthdaySoon(now() - h)
    }

    /** Called 8 times a second: animation, movement, and the life engine. */
    fun tick(realNow: Long) {
        frame++
        if (realNow > effectUntil) effect = Effect.NONE
        if (caption != null && realNow > captionUntil) caption = null
        if (panel is Panel.Message && realNow > messageUntil) panel = Panel.None

        mini?.let { g ->
            g.step()
            if (g.over) finishGame(g)
        }
        val beforeX = petX
        if (mini == null) animate(realNow)
        moving = petX != beforeX

        checkHome()
        checkBuddy()
        runToasts(realNow)
        // the garden creatures' hour: perks, mischief, and the news of it
        val (visited, news) = Visitors.catchUp(save, now(), zone, rng)
        val weatherBegan = visited.weather?.started == true && save.weather?.started != true
        if (visited != save) commit(visited)
        if (weatherBegan) count("weather")
        if (news.isNotEmpty() && panel == Panel.None && mini == null) {
            show(news.takeLast(2).joinToString("\n\n"), 3500)
        }

        val before = pet
        val after = PetEngine.advance(before, now(), zone, rng)
        if (after != before) {
            var s = save.copy(pet = after)
            if (before.stage != after.stage && after.ended == null) {
                show("${after.species.title.uppercase()}\nGREW UP!", 3000)
            }
            val fullLife = after.ended == org.jasonagibson.sproutpals.engine.EndReason.NATURAL
            val newMemory = after.ended != null && save.memories.none { it.bornAt == after.bornAt }
            if (newMemory) {
                val bonus = if (fullLife) Rules.POINTS_FULL_LIFE else 0
                s = s.copy(memories = s.memories + PetEngine.memoryOf(after), points = s.points + bonus)
                panel = Panel.Ended
            }
            commit(s)
            if (before.stage != after.stage && after.ended == null && before.stage != Stage.EGG) count("birthday")
            if (newMemory && fullLife) count("full_life")
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
                // walk over to the bed, then dig
                if (petX != gardenTo) {
                    stepX(if (gardenTo < petX) -1 else 1)
                    petLift = STRIDE[f % STRIDE.size]
                } else {
                    facingLeft = false
                    petLift = DIG[f % DIG.size]
                    findSpringEgg()
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
            onSound(Sfx.HATCH)
            // the hatching scene: shell halves fly off, the baby pops up and cheers; caption underneath
            effect = Effect.HATCH
            effectFrame = frame
            effectUntil = nowMillis() + 3_500
            petX = 0
            caption = (if (after.species.rare) "✨ A RARE BABY $name! ✨" else "🐣 WELCOME, BABY $name!") +
                if (after.generation > 1) "  (GENERATION ${after.generation})" else ""
            captionUntil = nowMillis() + 6_000
            count("hatch")
            if (after.species.rare) keyItem("✨", "A RARE $name HATCHED!")
        }
    }

    /** New reading from the phone's pedometer. */
    fun onSteps(reading: Long) {
        // Only a rising count means "walking now"; the first reading after opening is just the total so far.
        if (lastReading in 0 until reading) walk = Walking.onStep(walk, nowMillis())
        lastReading = reading
        val (next, arrived) = Route.collect(Steps.apply(save, reading))
        if (next != save) commit(next)
        if (arrived.isNotEmpty()) {
            val stop = arrived.last()
            show("YOU WALKED TO\n${stop.name.uppercase()}!\n\nFOUND:\n${stop.prize.uppercase()}", 6000)
            onArrive(arrived)
            arrived.forEach { keyItem("👟", it.prize.uppercase()) }   // the key-item fanfare plays for each prize
            count("stop", arrived.size)
        }
    }

    // ---------- sound ----------

    /** Set by the activity; only called when sound is on. */
    var onSoundOut: (Sfx) -> Unit = {}
    /** Set by the activity: start (true) or stop (false) the background tune. */
    var onMusic: (Boolean) -> Unit = {}

    fun onSound(sfx: Sfx) { if (save.soundOn) onSoundOut(sfx) }

    fun toggleSound() = commit(save.copy(soundOn = !save.soundOn))

    fun toggleMusic() {
        commit(save.copy(musicOn = !save.musicOn))
        onMusic(save.musicOn)
    }

    /** Set by the activity: buzz + notification when a garden shop is reached. */
    var onArrive: (List<Stop>) -> Unit = {}

    /** Long press on the SHOP button. */
    fun secretPoints(debug: Boolean) {
        if (mini != null || panel is Panel.Ended) return
        val today = dateAt(nowMillis(), zone).toEpochDays().toLong()
        if (!debug && save.secretDay == today) {
            show("★ ;) ★\nCOME BACK\nTOMORROW", 2000)
            return
        }
        commit(save.copy(points = save.points + 50, secretDay = today))
        onSound(Sfx.ARRIVE)
        show("★ A SECRET! ★\n+50 ★", 2500)
    }

    /** Debug builds (long-press A): pretend you've been walking for 3 minutes. */
    fun debugWalk() {
        val t = nowMillis()
        walk = Walk(startedAt = t - 3 * Rules.MINUTE, lastStepAt = t)
    }

    /** Debug builds (long-press C): jump the clock to 2 hours before the next birthday. */
    /** Debug builds (long-press B): start the next kind of weather right now. */
    fun debugWeather() {
        val now = now()
        val next = WeatherKind.entries[((save.weather?.kind?.ordinal ?: -1) + 1) % WeatherKind.entries.size]
        val (s, news) = Weather.begin(save.copy(weather = WeatherEvent(next, now, now + Weather.HOURS * Rules.HOUR)), now, rng)
        commit(s)
        news?.let { show(it, 2500) }
    }

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

    fun offerUpdate(info: UpdateInfo) { if (panel == Panel.None) panel = Panel.Update(info) }
    fun updateProgress(p: Int) { (panel as? Panel.Update)?.let { panel = it.copy(progress = p) } }
    fun updateFailed(msg: String) { (panel as? Panel.Update)?.let { panel = it.copy(error = msg, progress = null) } }

    // ---------- buttons ----------

    // ---------- mini-games ----------

    /** The game being played right now, or null. While it runs, A/B/C go to the game. */
    var mini by mutableStateOf<MiniGame?>(null); private set

    private fun startGame(index: Int) {
        panel = Panel.None
        selected = -1
        mini = MiniGame.start(index, rng)
    }

    /**
     * Playing is always allowed, even with full hearts: the game still earns points, and a
     * not-yet-happy pet gets a heart back. A little exercise trims the weight too.
     */
    private fun finishGame(g: MiniGame) {
        mini = null
        val earned = g.points()
        val happier = pet.happy < Rules.MAX_HEARTS
        commit(save.copy(pet = PetEngine.played(pet), points = save.points + earned))
        onSound(if (earned > 0) Sfx.HATCH else Sfx.C)
        panel = Panel.GameOver(
            "${g.result}\n\n+$earned ★" + if (happier) "\n+1 ♥" else "",
        )
        count("play")
        count(
            when (g) {
                is org.jasonagibson.sproutpals.engine.FruitCatch -> "game_fruit"
                is org.jasonagibson.sproutpals.engine.JumpRope -> "game_rope"
                is org.jasonagibson.sproutpals.engine.TicTacToe -> "game_ttt"
                is org.jasonagibson.sproutpals.engine.HigherLower -> "game_hilo"
                is org.jasonagibson.sproutpals.engine.PlantHarvest -> "game_harvest"
                is org.jasonagibson.sproutpals.engine.TwentyOne -> "game_21"
                is org.jasonagibson.sproutpals.engine.Slots -> "game_slots"
                is org.jasonagibson.sproutpals.engine.ScareCrows -> "game_crows"
            },
        )
    }

    /** A: next icon, or next choice inside a panel. */
    fun pressA() {
        mini?.let { it.a(); return }
        when (val p = panel) {
            is Panel.PlayMenu -> panel = p.copy(choice = (p.choice + 1) % MiniGame.NAMES.size)
            is Panel.Shop -> panel = p.copy(choice = (p.choice + 1) % Shop.items(p.tab, save).size, note = "")
            is Panel.Feed -> panel = p.copy(choice = (p.choice + 1) % feedChoices())
            is Panel.Friends -> {
                val n = if (p.friend == null) friendList.size else friendActions.size
                if (n > 0) panel = p.copy(choice = (p.choice + 1) % n, note = "")
            }
            is Panel.Garden -> panel =
                if (p.picking) p.copy(seed = (p.seed + 1) % Garden.seeds(save).size)
                else p.copy(plot = (p.plot + 1) % Garden.PLOTS, note = "")
            // last page: A or B closes instead of looping back to the first (C closes any time)
            is Panel.Status -> panel = if (p.page + 1 >= STATUS_PAGES) Panel.None else p.copy(page = p.page + 1)
            is Panel.Trophies -> panel = p.copy(top = (p.top + TROPHY_ROWS).let { if (it >= Achievements.ALL.size) 0 else it })
            is Panel.Book -> if (save.memories.isNotEmpty()) panel = p.copy(page = (p.page + 1) % save.memories.size)
            Panel.None -> selected = (selected + 1) % MenuItem.entries.size
            else -> Unit
        }
    }

    /** B: choose. Returns true when the update should start downloading. */
    fun pressB(): Boolean {
        mini?.let { it.b(); return false }
        when (val p = panel) {
            is Panel.PlayMenu -> { panel = Panel.HowTo(p.choice); return false }
            is Panel.Shop -> { shopPress(p); return false }
            is Panel.HowTo -> { startGame(p.game); return false }
            is Panel.GameOver -> { panel = Panel.PlayMenu(); return false }   // B: play again
            is Panel.Feed -> {
                when (p.choice) {
                    0 -> care(PetEngine::feedMeal, Effect.MEAL)
                    1 -> care(PetEngine::feedSnack, Effect.SNACK)
                    else -> feedFromGarden()
                }
                return false
            }
            is Panel.Garden -> { gardenPress(p); return false }
            is Panel.Friends -> { friendsPress(p); return false }
            is Panel.Status -> {
                panel = when {
                    p.page == PAGE_TROPHIES -> Panel.Trophies()
                    p.page == PAGE_BOOK -> Panel.Book()
                    p.page + 1 >= STATUS_PAGES -> Panel.None
                    else -> p.copy(page = p.page + 1)
                }
                return false
            }
            is Panel.Trophies, is Panel.Book -> { panel = Panel.None; return false }
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
                if (pet.sleeping && !pet.lightsOn) count("lights_off")
                show(if (pet.lightsOn) "LIGHTS ON" else "LIGHTS OFF", 1200)
            }
            MenuItem.PLAY -> panel = if (canInteract()) Panel.PlayMenu() else blocked()
            MenuItem.MEDICINE -> care(PetEngine::medicine, Effect.PILL)
            MenuItem.CLEAN -> care(PetEngine::clean, Effect.CLEAN)
            MenuItem.GARDEN -> panel = if (pet.ended == null) Panel.Garden() else blocked()
            null -> Unit
        }
        return false
    }

    /** C: back / cancel. */
    fun pressC() {
        mini?.let { it.c(); return }
        if (panel is Panel.Ended) return
        if (panel is Panel.HowTo) { panel = Panel.PlayMenu(); return }
        (panel as? Panel.Garden)?.let { if (it.picking) { panel = it.copy(picking = false); return } }
        (panel as? Panel.Friends)?.let { if (it.friend != null) { panel = Panel.Friends(); return } }
        if (panel != Panel.None) panel = Panel.None else selected = -1
    }

    /** SHOP opens the shop; inside the shop it flips between the Creatures and Outfits pages. */
    fun pressShop() {
        if (mini != null || panel is Panel.Ended || panel is Panel.Update) return
        val p = panel
        panel = if (p is Panel.Shop) Panel.Shop(tab = ShopTab.entries[(p.tab.ordinal + 1) % ShopTab.entries.size]) else Panel.Shop()
    }

    private fun shopPress(p: Panel.Shop) {
        val item = Shop.items(p.tab, save).getOrNull(p.choice) ?: return
        val (next, result) = Shop.press(save, item, now(), zone)
        commit(next)
        // the last purchase of a repeatable item takes it off the shelf: step back onto the list
        val left = Shop.items(p.tab, next).size
        val choice = p.choice.coerceAtMost(left - 1)
        val wear = item.tab == ShopTab.OUTFITS
        val note = when (result) {
            ShopResult.BOUGHT -> {
                onSound(Sfx.ARRIVE)
                if (!item.repeatable) count("buy")
                if (!item.repeatable && !item.daily) count("own:${item.id}")
                if (item.repeatable && next.mysteryCount >= Shop.MAX_REPEAT) count("secret_charm")
                when {
                    item.repeatable -> "?????"
                    item.daily -> "ON GUARD UNTIL MIDNIGHT!"
                    wear -> "YOURS! WEARING IT"
                    else -> "YOURS! IT'S IN YOUR GARDEN"
                }
            }
            ShopResult.ACTIVE -> "ALREADY ON GUARD TODAY"
            ShopResult.SHOWN -> if (wear) "WEARING IT" else "OUT IN THE GARDEN"
            ShopResult.HIDDEN -> "PUT AWAY FOR NOW"
            ShopResult.TOO_POOR -> "NEED ${item.price - save.points} MORE ★"
            ShopResult.MAXED -> "????!"
        }
        panel = p.copy(choice = choice, note = note)
    }

    // ---------- garden ----------

    /** Meal and snack, plus garden food when the pantry has some. */
    fun feedChoices() = if (save.pantry.isEmpty()) 2 else 3

    private fun gardenPress(p: Panel.Garden) {
        val now = now()
        if (p.picking) {
            val crop = Garden.seeds(save).getOrNull(p.seed) ?: return
            commit(Garden.plant(save, p.plot, crop.id, now))
            count("plant")
            onSound(Sfx.C)
            panel = p.copy(picking = false, note = "PLANTED!\nNOW WATER IT")
            helpInGarden()
            return
        }
        val plot = Garden.plots(save)[p.plot]
        panel = when (Garden.state(plot, now)) {
            PlotState.EMPTY -> p.copy(picking = true, note = "")
            PlotState.DRY, PlotState.GROWING -> {
                commit(Garden.water(save, p.plot, now))
                if (Garden.wetMinutes(save, now) > 0) count("water")
                onSound(Sfx.C)
                helpInGarden()
                val wet = Garden.wetMinutes(save, now)
                if (wet == 0) p.copy(note = "❄ THE SOIL IS FROZEN!\nWAIT FOR THE THAW")
                else p.copy(note = "WATERED! WET FOR\n${wet / 60}H${if (wet % 60 > 0) " ${wet % 60}M" else ""}")
            }
            PlotState.RIPE -> {
                val before = save.points
                val (next, crop) = Garden.harvest(save, p.plot, now) ?: return
                val (withSeed, golden) = Garden.maybeGoldenSeed(next, rng.nextInt(100))
                commit(withSeed)
                onSound(Sfx.ARRIVE)
                helpInGarden()
                count("harvest")
                if (golden) keyItem("🌟", "A GOLDEN SEED!")
                if (crop.rare) keyItem("👑", "GOLDEN SPROUT HARVESTED!")
                p.copy(note = "+${next.points - before} ★!\n${crop.name} TO THE PANTRY")
            }
        }
    }

    /** The pet trots over to the bed and digs for a bit (when it's up and about). */
    private fun helpInGarden() {
        if (!canInteract() || walkMode) return
        move = Move.GARDEN
        moveUntil = nowMillis() + 8_000L
    }

    private fun feedFromGarden() {
        val (rest, crop) = Garden.takeFood(save) ?: return
        val (after, outcome) = PetEngine.feedHarvest(pet)
        when (outcome) {
            Outcome.OK -> {
                commit(rest.copy(pet = after))
                count("harvest_meal")
                panel = Panel.None
                play(Effect.MEAL)
                show("YUM! ${crop.name}", 1200)
            }
            Outcome.NOT_NEEDED -> { play(Effect.NO); show("I'M FULL!", 1500) }
            Outcome.ASLEEP, Outcome.CANT -> panel = blocked()
        }
    }

    // ---------- care ----------

    private fun canInteract() = pet.ended == null && pet.stage != Stage.EGG && !pet.sleeping && !away

    private fun blocked(): Panel = Panel.Message(
        when {
            away -> "OUT VISITING\n${save.awayHost}'S GARDEN"
            pet.stage == Stage.EGG -> "SHAKE TO OPEN!"
            pet.sleeping -> "ZZZ... ASLEEP"
            else -> "..."
        }
    ).also { messageUntil = nowMillis() + 1500 }

    private fun care(action: (PetState) -> Pair<PetState, Outcome>, fx: Effect) {
        if (away) { panel = blocked(); return }
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
                when (fx) {
                    Effect.MEAL -> count("meal")
                    Effect.SNACK -> count("snack")
                    Effect.CLEAN -> count("clean")
                    Effect.PILL -> if (before.sick && !after.sick) count("cure")
                    else -> Unit
                }
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
        effectUntil = nowMillis() + 2000
        if (fx != Effect.NO) petX = 0
    }

    private fun show(text: String, ms: Long) {
        panel = Panel.Message(text)
        messageUntil = nowMillis() + ms
    }

    private fun newEgg() {
        val egg = PetEngine.nextEgg(pet, now(), rng, Shop.rarePercent(save))
        commit(save.copy(pet = egg))
        if (egg.generation > 1) keyItem("🥚", "FAMILY EGG! GENERATION ${egg.generation}")
        panel = Panel.None
        selected = -1
    }

    // ---------- achievements and key items ----------

    /** A banner that slides in at the top without blocking play. [key] = key item (gold style). */
    data class Toast(val icon: String, val title: String, val line: String, val key: Boolean = false)
    var toast by mutableStateOf<Toast?>(null); private set
    private val toastQueue = ArrayDeque<Toast>()
    private var toastUntil = 0L

    /** Counts something the player did; celebrates any tier reached. */
    fun count(key: String, by: Int = 1) {
        val (s, unlocks) = Achievements.record(save, key, by)
        if (s != save) commit(s)
        unlocks.forEach { u ->
            val f = u.feat
            toastQueue += if (f.secret && u.tier == 0) Toast(f.icon, "❓ SECRET FOUND!", "${f.name}: ${f.reveal}  +${u.stars} ★", key = true)
            else Toast(f.icon, "${u.medal} ACHIEVEMENT!", "${f.name}${if (f.tiers.size > 1) " ×${f.tiers[u.tier]}" else ""}  +${u.stars} ★")
        }
    }

    /** A key item picked up: the bigger fanfare. */
    fun keyItem(icon: String, name: String) { toastQueue += Toast(icon, "🗝 KEY ITEM!", name, key = true) }

    /** Shows queued banners one after another, each with its jingle. */
    private fun runToasts(realNow: Long) {
        if (toast != null && realNow < toastUntil) return
        toast = toastQueue.removeFirstOrNull()
        toast?.let {
            toastUntil = realNow + if (it.key) 4_000 else 3_000
            onSound(if (it.key) Sfx.KEY_ITEM else Sfx.ACHIEVE)
        }
    }

    // ---------- seasons ----------

    /** During the spring egg hunt, gardening turns up the hidden egg - once a year. */
    private fun findSpringEgg() {
        val today = dateAt(now(), zone)
        if (org.jasonagibson.sproutpals.engine.Calendar.holiday(today) != org.jasonagibson.sproutpals.engine.Holiday.EGG_HUNT) return
        if (save.eggYear == today.year) return
        commit(save.copy(eggYear = today.year, points = save.points + org.jasonagibson.sproutpals.engine.Calendar.EGG_STARS))
        keyItem("🥚", "SPRING EGG FOUND!  +${org.jasonagibson.sproutpals.engine.Calendar.EGG_STARS} ★")
    }

    // ---------- buddy ----------

    /** Long hold on the hearts. */
    fun heartHeld() {
        if (pet.ended != null || pet.stage == Stage.EGG) return
        if (save.buddy?.stage(now()) != null) { toastQueue += Toast("♥", "♥ ♥ ♥", "YOUR BUDDY SAYS HI!"); return }
        // once a week
        val wait = save.buddyMadeAt + BUDDY_EVERY - now()
        if (save.buddyMadeAt > 0 && wait > 0) {
            toastQueue += Toast("♥", "♥ ♥ ♥", "TRY AGAIN IN ${wait / Rules.DAY + 1} DAY${if (wait / Rules.DAY + 1 == 1L) "" else "S"}")
            return
        }
        val pool = Species.entries.filter { !it.rare && it != pet.species }
        commit(save.copy(buddy = Buddy(pool[rng.nextInt(pool.size)], now()), buddyMadeAt = now()))
        toastQueue += Toast("✨", "✨ ??? ✨", "A NEW BUDDY TAGS ALONG!", key = true)
        count("secret_buddy")
    }

    /** A buddy at the end of its two weeks says goodbye. */
    private fun checkBuddy() {
        val b = save.buddy ?: return
        if (b.stage(now()) != null) return
        commit(save.copy(buddy = null))
        if (panel == Panel.None) show("${b.species.title.uppercase()} THE BUDDY\nWAVED GOODBYE ♥", 3000)
    }

    // ---------- visits ----------

    /** Friends' pals staying in our garden right now (from the server). */
    var visitors by mutableStateOf<List<org.jasonagibson.sproutpals.app.Friends.Visitor>>(emptyList()); private set
    /** Set by the activity: ask the server for a visit / a heart. The reply comes back via [visitStarted] etc. */
    var onVisitOut: (code: String, hours: Int) -> Unit = { _, _ -> }
    var onHeartOut: (code: String, message: String) -> Unit = { _, _ -> }

    val away: Boolean get() = save.awayUntil > nowMillis()

    /** While our pal is away: the host's own pal, shown beside ours (we're at their garden). */
    var hostPal by mutableStateOf<org.jasonagibson.sproutpals.app.Friends.Visitor?>(null); private set

    fun openFriends() {
        if (mini != null || panel is Panel.Ended || panel is Panel.Update) return
        panel = Panel.Friends(note = if (friendList.isEmpty()) "No friends yet.\nTap + ADD and type\na friend's garden code." else "")
    }

    private val friendActions = listOf("VISIT 1 HOUR" to 1, "VISIT 2 HOURS" to 2, "VISIT 4 HOURS" to 4, "SEND ♥ HI!" to 0, "SEND ♥ MISS YOU" to -1)

    fun friendActionNames() = friendActions.map { it.first }

    private fun friendsPress(p: Panel.Friends) {
        if (p.friend == null) {
            val f = friendList.getOrNull(p.choice) ?: return
            panel = Panel.Friends(friend = f.code)
            return
        }
        val (_, what) = friendActions[p.choice]
        when {
            what > 0 && away -> panel = p.copy(note = "YOUR PAL IS ALREADY\nOUT VISITING")
            what > 0 && !org.jasonagibson.sproutpals.engine.Abilities.canVisit(pet.species) ->
                panel = p.copy(note = "JASON IS BUSY\nTROUBLESHOOTING -\nHE CAN'T GO VISITING")
            what > 0 && (pet.stage == Stage.EGG || pet.ended != null) -> panel = p.copy(note = "NOBODY TO SEND YET")
            what > 0 -> { onVisitOut(p.friend, what); panel = p.copy(note = "PACKING A BAG...") }
            else -> { onHeartOut(p.friend, if (what == 0) "hi!" else "miss you"); panel = p.copy(note = "♥ SENT!"); count("heart") }
        }
    }

    /** The server said yes: our pal is off to [host]'s garden until [untilMs]. */
    fun visitStarted(host: String, untilMs: Long) {
        commit(save.copy(awayHost = host, awayUntil = untilMs))
        friendList.firstOrNull { it.code == host }?.let { hostPal = org.jasonagibson.sproutpals.app.Friends.Visitor(it.code, it.species, it.stage, "", untilMs / 1000) }
        count("visit")
        onSound(Sfx.ARRIVE)
        panel = Panel.None
        show("OFF TO $host'S\nGARDEN! BACK IN\n${((untilMs - nowMillis()) / Rules.HOUR + 1)}H", 2500)
    }

    fun visitFailed(message: String) { (panel as? Panel.Friends)?.let { panel = it.copy(note = message.uppercase()) } }

    /** Fresh news from the server: who's visiting us, and whether our pal is out. */
    fun gardenNow(g: org.jasonagibson.sproutpals.app.Friends.GardenNow) {
        visitors = g.visitors
        hostPal = g.away?.let { org.jasonagibson.sproutpals.app.Friends.Visitor(it.host, it.species, it.stage, it.outfit, it.until) }
        var s = save
        g.away?.let { a -> if (s.awayHost == null) s = s.copy(awayHost = a.host, awayUntil = a.until * 1000) }
        // each new arrival: a heart for our pal, and a hello
        val keys = g.visitors.map { "${it.code}@${it.until}" }
        val fresh = g.visitors.filter { "${it.code}@${it.until}" !in s.welcomed }
        if (fresh.isNotEmpty()) {
            s = s.copy(welcomed = (s.welcomed + keys).takeLast(30))
            if (pet.ended == null && pet.stage != Stage.EGG) s = s.copy(pet = s.pet.copy(happy = (s.pet.happy + 1).coerceAtMost(Rules.MAX_HEARTS)))
            onSound(Sfx.ARRIVE)
            if (panel == Panel.None) show("${fresh.first().code}'S ${fresh.first().species} CAME\nTO VISIT! +1 ♥", 3000)
        }
        if (s != save) commit(s)
        if (fresh.isNotEmpty()) count("host", fresh.size)
    }

    /** Home again: a heart and some stars for the trip. */
    private fun checkHome() {
        val host = save.awayHost ?: return
        if (nowMillis() < save.awayUntil) return
        var s = save.copy(awayHost = null, awayUntil = 0, points = save.points + VISIT_STARS)
        if (pet.ended == null) s = s.copy(pet = s.pet.copy(happy = (s.pet.happy + 1).coerceAtMost(Rules.MAX_HEARTS)))
        commit(s)
        onSound(Sfx.HATCH)
        if (panel == Panel.None) show("HOME FROM\n$host'S GARDEN!\n+1 ♥  +$VISIT_STARS ★", 3000)
    }

    // ---------- friends (garden codes) ----------

    /** One line of news for the code bar: "Added ABC123!", "Can't reach the garden server..." */
    var friendsNote by mutableStateOf<String?>(null)
    var friendList by mutableStateOf<List<Friends.Friend>>(emptyList())

    fun registered(code: String, token: String) = commit(save.copy(gardenCode = code, serverToken = token))

    private fun commit(s: Save) {
        save = s
        persist(s)
    }

    companion object {
        const val STATUS_PAGES = 7
        /** The last two Status pages open the trophy list and the memory book. */
        const val PAGE_TROPHIES = 5
        const val PAGE_BOOK = 6
        const val TROPHY_ROWS = 6
        /** How often a new buddy can come along. */
        const val BUDDY_EVERY = 7 * Rules.DAY
        /** Stars for coming home from a visit. */
        const val VISIT_STARS = 3
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
