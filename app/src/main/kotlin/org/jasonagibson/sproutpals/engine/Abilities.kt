package org.jasonagibson.sproutpals.engine

import kotlin.random.Random

/**
 * Each character's own ability (Jason, 10-23):
 *  Kona   - hugs and sings; her hearts drop more slowly
 *  Jun    - watering lasts an hour longer; half of all bad effects miss him
 *  Rusty, Kara - the dogs chase off half the creatures' mischief
 *  Jason  - troubleshoots every bad effect away, but is too busy to visit other gardens
 *  Karta  - two friends always hang out in his garden (pictures only, not real friends)
 *  Nathan - perfectly balanced: no extra perks or catches; Karta or Jun sometimes drop by, and
 *           while they're over he gets their good abilities (never their downsides)
 *  DJ, Maui - the cats now and then bring a creature home for 4 hours: all of its perks,
 *             none of its catches, and it isn't owned or collected
 */
object Abilities {
    /** Hours a cat's guest creature stays, and the hourly chance (in 100) that one turns up. */
    const val GUEST_HOURS = 4
    const val GUEST_CHANCE = 8

    fun bringsGuests(s: Species) = s == Species.DJ || s == Species.MAUI

    /**
     * Whose good abilities apply right now: the pal's own, and for Nathan also those of
     * whoever has dropped by this hour.
     */
    fun active(s: Species, hour: Long): Set<Species> = if (s == Species.NATHAN) setOf(s) + company(s, hour) else setOf(s)
    /** One line for the Status screen. */
    fun describe(s: Species): String? = when (s) {
        Species.KONA -> "HUGS & SINGS:\nHEARTS DROP SLOWER"
        Species.JUN -> "GREEN THUMB:\nWATER LASTS +1H,\nSHRUGS OFF HALF\nOF BAD LUCK"
        Species.RUSTY, Species.KARA -> "GOOD DOG:\nCHASES OFF HALF\nTHE MISCHIEF"
        Species.JASON -> "TROUBLESHOOTER:\nNO BAD EFFECTS -\nBUT TOO BUSY\nTO VISIT"
        Species.KARTA -> "SOCIAL BUTTERFLY:\nTWO FRIENDS\nHANG OUT"
        Species.NATHAN -> "BALANCED:\nBORROWS FRIENDS'\nGOOD SIDES"
        Species.DJ, Species.MAUI -> "CAT'S GIFT:\nBRINGS A CREATURE\nHOME SOMETIMES"
        else -> null
    }

    /** Kona's hearts last half as long again. */
    fun happyEvery(s: Species, base: Int) = if (s == Species.KONA) base * 3 / 2 else base

    /** Jun's watering bonus, in minutes (Nathan gets it while Jun is over). */
    fun extraWater(s: Species, hour: Long = 0) = if (Species.JUN in active(s, hour)) 60 else 0

    fun canVisit(s: Species) = s != Species.JASON

    /**
     * A bad effect is about to happen to the pal or the garden. Returns how it was stopped
     * (null = it happens). [fromCreature] = caused by a garden creature (the dogs can chase those).
     */
    fun blocks(s: Species, fromCreature: Boolean, rng: Random, hour: Long = 0): String? {
        val who = active(s, hour)
        return when {
            Species.JASON in who -> "JASON TROUBLESHOT IT!"
            Species.JUN in who && rng.nextBoolean() -> if (s == Species.JUN) "JUN SHRUGGED IT OFF" else "JUN HELPED SHRUG IT OFF"
            fromCreature && (s == Species.RUSTY || s == Species.KARA) && rng.nextBoolean() -> "${s.title.uppercase()} CHASED IT OFF!"
            else -> null
        }
    }

    /**
     * Picture-only company in the pal's garden this hour: Karta's two friends, or Karta / Jun
     * dropping by Nathan's now and then. Changes with the hour so it feels lively.
     */
    fun company(pal: Species, hour: Long): List<Species> {
        val others = Species.entries.filter { !it.rare && it != pal }
        return when (pal) {
            Species.KARTA -> {
                val a = others[(hour % others.size).toInt()]
                val b = others[((hour / 3 + 2) % others.size).toInt()].takeIf { it != a } ?: others[((hour + 1) % others.size).toInt()]
                listOf(a, b)
            }
            Species.NATHAN -> if (hour % 3 == 0L) listOf(if (hour % 2 == 0L) Species.KARTA else Species.JUN) else emptyList()
            else -> emptyList()
        }
    }
}
