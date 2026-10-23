package org.jasonagibson.sproutpals.engine

import kotlinx.serialization.Serializable

/*
 * The data the whole game is made of. Pure Kotlin, no Android imports, so the
 * unit tests can run a whole two-week life in milliseconds.
 */

@Serializable
enum class Kind { HUMAN, DOG, CAT }

@Serializable
enum class Species(val kind: Kind, val title: String, val rare: Boolean = false) {
    JUN(Kind.HUMAN, "Jun"),
    KONA(Kind.HUMAN, "Kona"),
    RUSTY(Kind.DOG, "Rusty"),
    KARA(Kind.DOG, "Kara"),
    DJ(Kind.CAT, "DJ"),
    MAUI(Kind.CAT, "Maui"),

    /** The secret one: 1 egg in 100. */
    JASON(Kind.HUMAN, "Jason", rare = true),
}

@Serializable
enum class Stage { EGG, BABY, KID, ADULT, OLD }

@Serializable
enum class EndReason(val line: String) {
    NATURAL("lived a full, happy life"),
    STARVED("went too long without food"),
    ILLNESS("was sick too long without medicine"),
    NEGLECT("was left alone too often"),
}

/** One pet's life. Hearts (hunger, happy) run 0..4, like the 90s original. */
@Serializable
data class PetState(
    val species: Species,
    /** When the egg appeared. */
    val bornAt: Long,
    /** When it was shaken open. Null = still an egg. The 2-week life counts from here. */
    val hatchedAt: Long? = null,
    /** Jolts so far (3 opens the egg); each one shows another crack. */
    val cracks: Int = 0,
    /** The last whole minute the engine has played through. */
    val lastTick: Long,
    val stage: Stage = Stage.EGG,
    val hunger: Int = 2,
    val happy: Int = 2,
    val weight: Int = 5,
    val poops: Int = 0,
    /** Awake minutes the oldest poop has been left out. */
    val poopAge: Int = 0,
    val sick: Boolean = false,
    val sickMinutes: Int = 0,
    val doses: Int = 0,
    val hungerTimer: Int = 0,
    val happyTimer: Int = 0,
    val poopTimer: Int = 0,
    val hungerZeroMinutes: Int = 0,
    /** Minutes the current "I need you" call has gone unanswered. */
    val needMinutes: Int = 0,
    /** Mistakes this stage; the count resets when the pet grows. */
    val careMistakes: Int = 0,
    val totalMistakes: Int = 0,
    val lightsOn: Boolean = true,
    val sleeping: Boolean = false,
    /** Minutes asleep with the lights still on, tonight. */
    val lightsOnAsleep: Int = 0,
    val ended: EndReason? = null,
    val endedAt: Long? = null,
)

/** What is left of a pet after it returns to the garden. */
@Serializable
data class MemoryCard(
    val species: Species,
    val bornAt: Long,
    val endedAt: Long,
    val reason: EndReason,
    val stage: Stage,
    val mistakes: Int,
)

/** Everything saved on the phone: the current pet plus what carries over between lives. */
@Serializable
data class Save(
    val pet: PetState,
    val points: Int = 0,
    val memories: List<MemoryCard> = emptyList(),
    /** Debug "time warp" only: milliseconds added to the real clock. */
    val timeOffset: Long = 0,
    /** Phone step counter value we last turned into points (counter resets on reboot). */
    val stepBaseline: Long = -1,
    val stepsTotal: Long = 0,
    /** Steps carried over that haven't made a whole point yet. */
    val stepRemainder: Int = 0,
    /** Garden shops reached on the walking route (see [Route]). Carries over between lives. */
    val stopsReached: Int = 0,
    /** Cosmetics collected (prize ids). Worn ones will show on friends' screens too. */
    val unlocked: List<String> = emptyList(),
    /** Button beeps and jingles. */
    val soundOn: Boolean = true,
    /** 8-bit background tune. */
    val musicOn: Boolean = true,
)
