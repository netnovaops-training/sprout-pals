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
    /** Extra minutes of life, from the garden creatures. */
    val bonusLife: Int = 0,
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
    /** Things collected: walking-route prizes and shop items (ids). */
    val unlocked: List<String> = emptyList(),
    /** Owned creatures switched off in the shop (or gone home after their day out). */
    val hidden: List<String> = emptyList(),
    /** When each creature out in the garden goes home (clock ms); switching it on again resets it. */
    val outUntil: Map<String, Long> = emptyMap(),
    /** Outfits being worn: at most one hat, one shirt, one pair of pants. */
    val wearing: List<String> = emptyList(),
    /** The garden bed (see [Garden]) and harvested crops waiting to be eaten (crop ids). */
    val plots: List<Plot> = emptyList(),
    val pantry: List<String> = emptyList(),
    /** Flowers the bees have grown on the lawn. */
    val flowers: Int = 0,
    /** The last whole hour the garden creatures were played through (see [Visitors]). */
    val visitorsAt: Long = 0,
    /** Today's weather event, if one rolled, and the day (epoch day) it was rolled for. */
    val weather: WeatherEvent? = null,
    val weatherDay: Long = -1,
    /** Our pal out visiting: whose garden, and until when (clock ms). Null/0 = home. */
    val awayHost: String? = null,
    val awayUntil: Long = 0,
    /** Visits already welcomed ("code@until"), so each arrival is celebrated once. */
    val welcomed: List<String> = emptyList(),
    /** Achievements: how many times each thing was done, and the tiers earned ("key#tier"). */
    val counts: Map<String, Int> = emptyMap(),
    val achieved: List<String> = emptyList(),
    /** Phone notifications already sent and not yet dealt with (see Reminders). */
    val notified: List<String> = emptyList(),
    /** Times the last creature-page item was bought. */
    val mysteryCount: Int = 0,
    /** Day number of the last SHOP long press. */
    val secretDay: Long = -1,
    /** Button beeps and jingles. */
    val soundOn: Boolean = true,
    /** 8-bit background tune. */
    val musicOn: Boolean = true,
    /** This garden's 6-letter code on the friends server (null until registered). */
    val gardenCode: String? = null,
    /** Secret for the friends server. Stays on this phone; never shown. */
    val serverToken: String? = null,
)
