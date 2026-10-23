package org.jasonagibson.sproutpals.engine

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Tuning numbers in one place. Times are minutes unless the name says otherwise. */
object Rules {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    // Life plan, counted from the moment the egg is shaken open:
    // baby/kid/adult 3 days each, old ~5 days = 14 days.
    const val KID_AT = 3 * DAY
    const val ADULT_AT = KID_AT + 3 * DAY
    const val OLD_AT = ADULT_AT + 3 * DAY
    const val LIFE_END = 14 * DAY
    const val SHAKES_TO_HATCH = 3

    /** Chance (in 100) that an egg is the rare one. */
    const val RARE_PERCENT = 1
    const val RARE_STEP = 5
    const val RARE_PERCENT_MAX = 15
    /** Item id for the shop's last listing. */
    const val MYSTERY_ITEM = "mystery"

    // Sleeps 22:00-07:00 phone time. Nothing counts down while asleep.
    const val SLEEP_FROM_HOUR = 22
    const val WAKE_HOUR = 7

    const val MAX_HEARTS = 4
    const val MAX_POOPS = 4
    const val POOP_MAKES_SICK = 180
    const val MISTAKE_GRACE = 15
    const val STARVE_AFTER = 12 * 60
    const val ILLNESS_AFTER = 24 * 60
    const val MAX_MISTAKES = 10
    const val LIGHTS_GRACE = 15
    const val DOSES_TO_CURE = 2
    /** About one random cold every 4 awake days. */
    const val RANDOM_SICK_PER_MINUTE = 1.0 / (4 * 15 * 60)

    // Points: steps and loving care both pay into the shop.
    const val STEPS_PER_POINT = 100
    const val POINTS_FEED_HUNGRY = 1
    const val POINTS_CLEAN = 1
    const val POINTS_CURE = 2
    const val POINTS_PLAY = 1
    const val POINTS_FULL_LIFE = 20

    fun hungerEvery(s: Stage) = when (s) { Stage.BABY -> 60; Stage.KID -> 120; Stage.ADULT -> 180; else -> 150 }
    fun happyEvery(s: Stage) = when (s) { Stage.BABY -> 75; Stage.KID -> 120; else -> 150 }
    /** Every 4 awake hours, at every age (Jason, 10-23)... */
    @Suppress("UNUSED_PARAMETER")
    fun poopEvery(s: Stage) = 240
    /** ...plus a surprise one or two a day: about 1.5 extra per 15 awake hours. */
    const val RANDOM_POOP_PER_MINUTE = 1.5 / (15 * 60)
    /** Poop left out this long (awake minutes) starts to smell. */
    const val SMELLY_AFTER = 30

    /** Party hat for the last day before each birthday (growing into kid, adult, old). */
    fun birthdaySoon(age: Long): Boolean =
        listOf(KID_AT, ADULT_AT, OLD_AT).any { age >= it - DAY && age < it }

    /** [age] = time since hatching. */
    fun stageAt(age: Long) = when {
        age < KID_AT -> Stage.BABY
        age < ADULT_AT -> Stage.KID
        age < OLD_AT -> Stage.ADULT
        else -> Stage.OLD
    }

    fun isSleepTime(t: Long, zone: ZoneId): Boolean {
        val h = Instant.ofEpochMilli(t).atZone(zone).hour
        return h >= SLEEP_FROM_HOUR || h < WAKE_HOUR
    }
}

enum class Outcome { OK, NOT_NEEDED, ASLEEP, CANT }

/**
 * The rules of life, as pure functions: (state, time) -> new state.
 * The app calls [advance] every second and after being closed for a while;
 * it replays each missed minute, so "closed for 6 hours" plays out exactly
 * like "watched for 6 hours".
 */
object PetEngine {

    private val REGULAR = Species.entries.filter { !it.rare }

    /** The species for a new egg: one of the six with equal odds, or now and then the rare one. */
    fun pickSpecies(rng: Random, rarePercent: Int = Rules.RARE_PERCENT): Species =
        if (rng.nextInt(100) < rarePercent) Species.JASON else REGULAR[rng.nextInt(REGULAR.size)]

    fun newPet(now: Long, rng: Random, rarePercent: Int = Rules.RARE_PERCENT): PetState =
        PetState(species = pickSpecies(rng, rarePercent), bornAt = now, lastTick = now)

    /** Plays minutes forward up to [to]. Caps catch-up at 20 days so a lost phone can't freeze the app. */
    fun advance(state: PetState, to: Long, zone: ZoneId, rng: Random): PetState {
        if (state.ended != null) return state
        if (state.hatchedAt == null) return state  // eggs don't age; they wait for a shake
        var s = state
        var t = maxOf(s.lastTick, to - 20 * Rules.DAY)
        while (t + Rules.MINUTE <= to) {
            t += Rules.MINUTE
            s = minute(s, t, zone, rng)
            if (s.ended != null) break
        }
        return s.copy(lastTick = t)
    }

    /**
     * One jolt from shaking the phone. Returns the egg with one more crack, or - on the
     * last one - the newly hatched baby, whose 2-week life starts [now].
     */
    fun shake(s: PetState, now: Long): PetState {
        if (s.stage != Stage.EGG || s.ended != null) return s
        val cracks = s.cracks + 1
        if (cracks < Rules.SHAKES_TO_HATCH) return s.copy(cracks = cracks)
        return s.copy(
            cracks = cracks, hatchedAt = now, lastTick = now, stage = Stage.BABY,
            hunger = 2, happy = 2, hungerTimer = 0, happyTimer = 0, poopTimer = 0,
        )
    }

    private fun minute(s0: PetState, t: Long, zone: ZoneId, rng: Random): PetState {
        var s = s0
        val hatched = s.hatchedAt ?: return s  // an egg just waits to be shaken
        val age = t - hatched
        if (age >= Rules.LIFE_END + s.bonusLife * Rules.MINUTE) return end(s, EndReason.NATURAL, t)

        val stage = Rules.stageAt(age)
        if (stage != s.stage) s = s.copy(stage = stage, careMistakes = 0)

        if (Rules.isSleepTime(t, zone)) {
            s = s.copy(sleeping = true)
            if (s.lightsOn) {
                val lit = s.lightsOnAsleep + 1
                s = s.copy(lightsOnAsleep = lit)
                if (lit == Rules.LIGHTS_GRACE) {
                    s = s.copy(happy = (s.happy - 1).coerceAtLeast(0)).mistake()
                }
            }
            return checkNeglect(s, t)
        }
        if (s.sleeping) s = s.copy(sleeping = false, lightsOn = true, lightsOnAsleep = 0)

        // Timers that tick while awake.
        var hunger = s.hunger
        var hungerTimer = s.hungerTimer + 1
        if (hungerTimer >= Rules.hungerEvery(s.stage)) { hunger = (hunger - 1).coerceAtLeast(0); hungerTimer = 0 }
        var happy = s.happy
        var happyTimer = s.happyTimer + 1
        if (happyTimer >= Rules.happyEvery(s.stage)) { happy = (happy - 1).coerceAtLeast(0); happyTimer = 0 }
        var poops = s.poops
        var poopTimer = s.poopTimer + 1
        if (poopTimer >= Rules.poopEvery(s.stage)) { poops = (poops + 1).coerceAtMost(Rules.MAX_POOPS); poopTimer = 0 }
        else if (rng.nextDouble() < Rules.RANDOM_POOP_PER_MINUTE) poops = (poops + 1).coerceAtMost(Rules.MAX_POOPS)

        var sick = s.sick
        var poopAge = if (poops > 0) s.poopAge + 1 else 0
        if (poopAge >= Rules.POOP_MAKES_SICK && !sick) { sick = true; poopAge = 0 }
        if (!sick && rng.nextDouble() < Rules.RANDOM_SICK_PER_MINUTE) sick = true

        s = s.copy(
            hunger = hunger, hungerTimer = hungerTimer, happy = happy, happyTimer = happyTimer,
            poops = poops, poopTimer = poopTimer, poopAge = poopAge, sick = sick,
            sickMinutes = if (sick) s.sickMinutes + 1 else 0,
            hungerZeroMinutes = if (hunger == 0) s.hungerZeroMinutes + 1 else 0,
        )

        // An empty heart that nobody answers for 15 minutes is a care mistake.
        if (s.hunger == 0 || s.happy == 0) {
            val waited = s.needMinutes + 1
            s = s.copy(needMinutes = waited)
            if (waited == Rules.MISTAKE_GRACE) s = s.mistake()
        } else if (s.needMinutes != 0) {
            s = s.copy(needMinutes = 0)
        }

        if (s.hungerZeroMinutes >= Rules.STARVE_AFTER) return end(s, EndReason.STARVED, t)
        if (s.sickMinutes >= Rules.ILLNESS_AFTER) return end(s, EndReason.ILLNESS, t)
        return checkNeglect(s, t)
    }

    private fun checkNeglect(s: PetState, t: Long) =
        if (s.careMistakes >= Rules.MAX_MISTAKES) end(s, EndReason.NEGLECT, t) else s

    private fun PetState.mistake() = copy(careMistakes = careMistakes + 1, totalMistakes = totalMistakes + 1)

    private fun end(s: PetState, why: EndReason, t: Long) = s.copy(ended = why, endedAt = t)

    fun memoryOf(s: PetState) = MemoryCard(
        species = s.species, bornAt = s.bornAt, endedAt = s.endedAt ?: s.lastTick,
        reason = s.ended ?: EndReason.NATURAL, stage = s.stage, mistakes = s.totalMistakes,
    )

    // ---- Care actions. Each returns the new state and what happened. ----

    private fun canCare(s: PetState): Outcome? = when {
        s.ended != null || s.stage == Stage.EGG -> Outcome.CANT
        s.sleeping -> Outcome.ASLEEP
        else -> null
    }

    fun feedMeal(s: PetState): Pair<PetState, Outcome> {
        canCare(s)?.let { return s to it }
        if (s.hunger >= Rules.MAX_HEARTS) return s to Outcome.NOT_NEEDED
        return s.copy(hunger = s.hunger + 1, weight = s.weight + 1, hungerZeroMinutes = 0) to Outcome.OK
    }

    /** Food from the garden: fills like a meal and cheers like a snack, without the extra weight. */
    fun feedHarvest(s: PetState): Pair<PetState, Outcome> {
        canCare(s)?.let { return s to it }
        if (s.hunger >= Rules.MAX_HEARTS && s.happy >= Rules.MAX_HEARTS) return s to Outcome.NOT_NEEDED
        return s.copy(
            hunger = (s.hunger + 1).coerceAtMost(Rules.MAX_HEARTS),
            happy = (s.happy + 1).coerceAtMost(Rules.MAX_HEARTS),
            hungerZeroMinutes = 0,
        ) to Outcome.OK
    }

    fun feedSnack(s: PetState): Pair<PetState, Outcome> {
        canCare(s)?.let { return s to it }
        return s.copy(happy = (s.happy + 1).coerceAtMost(Rules.MAX_HEARTS), weight = s.weight + 2) to Outcome.OK
    }

    /** After a mini-game: a heart back (if not already full) and a little exercise. Never refuses. */
    fun played(s: PetState): PetState =
        if (s.ended != null || s.stage == Stage.EGG) s
        else s.copy(happy = (s.happy + 1).coerceAtMost(Rules.MAX_HEARTS), weight = (s.weight - 1).coerceAtLeast(1))

    fun play(s: PetState): Pair<PetState, Outcome> {
        canCare(s)?.let { return s to it }
        if (s.happy >= Rules.MAX_HEARTS) return s to Outcome.NOT_NEEDED
        return s.copy(happy = s.happy + 1, weight = (s.weight - 1).coerceAtLeast(1)) to Outcome.OK
    }

    fun clean(s: PetState): Pair<PetState, Outcome> {
        if (s.ended != null || s.stage == Stage.EGG) return s to Outcome.CANT
        if (s.poops == 0) return s to Outcome.NOT_NEEDED
        return s.copy(poops = 0, poopAge = 0) to Outcome.OK
    }

    /** Two doses cure. The first dose returns OK with the pet still sick. */
    fun medicine(s: PetState): Pair<PetState, Outcome> {
        canCare(s)?.let { return s to it }
        if (!s.sick) return s to Outcome.NOT_NEEDED
        val doses = s.doses + 1
        return if (doses >= Rules.DOSES_TO_CURE) s.copy(sick = false, doses = 0, sickMinutes = 0) to Outcome.OK
        else s.copy(doses = doses) to Outcome.OK
    }

    fun toggleLights(s: PetState): PetState = s.copy(lightsOn = !s.lightsOn)

    /** Poop has been left out long enough to stink. */
    fun smelly(s: PetState) = s.ended == null && s.poops > 0 && s.poopAge >= Rules.SMELLY_AFTER

    /** True when the attention icon should light up. */
    fun needsAttention(s: PetState): Boolean {
        if (s.ended != null || s.stage == Stage.EGG) return false
        if (s.sleeping) return s.lightsOn
        return s.hunger == 0 || s.happy == 0 || s.poops > 0 || s.sick
    }
}

/** Turns phone step-counter readings into points. Pure, so it is tested like the engine. */
object Steps {
    /**
     * [reading] is the phone's steps-since-boot counter. A reading lower than the
     * baseline means the phone rebooted, so the whole reading is new steps.
     */
    fun apply(save: Save, reading: Long): Save {
        if (reading < 0) return save
        if (save.stepBaseline < 0) return save.copy(stepBaseline = reading)
        val fresh = if (reading >= save.stepBaseline) reading - save.stepBaseline else reading
        if (fresh == 0L) return save
        val pool = save.stepRemainder + fresh
        val points = (pool / Rules.STEPS_PER_POINT).toInt()
        return save.copy(
            stepBaseline = reading,
            stepsTotal = save.stepsTotal + fresh,
            stepRemainder = (pool % Rules.STEPS_PER_POINT).toInt(),
            points = save.points + points,
        )
    }
}

/**
 * Is the person out walking right now? Built from pedometer step times.
 * A walk is a run of steps with no pause longer than 2 minutes (so a red light doesn't end it).
 */
data class Walk(val startedAt: Long? = null, val lastStepAt: Long? = null)

object Walking {
    /** Walk mode starts after this much walking... */
    const val SHOW_AFTER = 2 * Rules.MINUTE
    /** ...and ends after this long with no steps. A shorter pause keeps the walk going. */
    const val STOP_AFTER = 2 * Rules.MINUTE
    /** Steps this recent count as "walking this moment" (the egg wiggles along). */
    const val MOVING_WINDOW = 15_000L

    fun onStep(w: Walk, t: Long): Walk =
        if (w.lastStepAt == null || t - w.lastStepAt > STOP_AFTER) Walk(startedAt = t, lastStepAt = t)
        else w.copy(lastStepAt = t)

    fun movingNow(w: Walk, now: Long) = w.lastStepAt != null && now - w.lastStepAt < MOVING_WINDOW

    /** True once there's been 2 minutes of walking, until 2 minutes after the last step. */
    fun showWalk(w: Walk, now: Long): Boolean {
        val start = w.startedAt ?: return false
        val last = w.lastStepAt ?: return false
        return now - start >= SHOW_AFTER && now - last < STOP_AFTER
    }
}
