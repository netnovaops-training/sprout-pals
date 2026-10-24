package org.jasonagibson.sproutpals.engine

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * What the shop creatures do while they're out in the garden. Once an hour each one rolls for
 * its perk and for its catch; perks are likelier than catches, so having them is better overall.
 * Every creature out also adds a little to the pal's life ([LIFE_MINUTES] per hour; the deer more).
 * Pure Kotlin like the rest of the engine: random numbers come in, a new save and news go out.
 */
object Visitors {
    private const val HOUR = 60 * 60_000L
    /** Hours caught up at most after the app was closed for a long time. */
    private const val MAX_HOURS = 48
    /** Extra life per creature per hour out, in minutes (the deer gives [DEER_LIFE]). */
    const val LIFE_MINUTES = 1
    const val DEER_LIFE = 3
    /** Most flowers the bees can grow on the lawn; each pair adds a point to every harvest. */
    const val MAX_FLOWERS = 10

    /** One line on the shop card: an icon and what it does. [good] = perk, else a catch. */
    data class Perk(val icon: String, val text: String, val good: Boolean = true)

    private val LIFE = Perk("⏳", "Longer life: +24 min a day")

    /** What each creature does, for the shop card. Matches the rolls in [hour]. */
    val PERKS: Map<String, List<Perk>> = mapOf(
        "ladybug" to listOf(Perk("💧", "Eats aphids: watering lasts 4h, not 3"), LIFE, Perk("🥫", "Now and then spoils a pantry crop", false)),
        "butterfly" to listOf(Perk("♥", "Cheers your pal: sometimes +1 heart"), LIFE, Perk("🍽", "Pal chases it: sometimes -1 food", false)),
        "bee" to listOf(Perk("🌼", "Grows flowers: harvests pay more ★"), LIFE, Perk("🐝", "Rarely stings: your pal gets sick", false)),
        "fireflies" to listOf(Perk("🌙", "Sweet dreams: +1 heart at night"), LIFE, Perk("😵", "Can keep pal up late: -1 heart", false)),
        "bluebird" to listOf(Perk("🍓", "Brings berries to your pantry"), LIFE, Perk("🌾", "Sometimes pecks a ripe crop away", false)),
        "bunny" to listOf(Perk("♥", "Cuddles: sometimes +1 heart"), LIFE, Perk("🥕", "Nibbles crops: sets them back 30m", false)),
        "squirrel" to listOf(Perk("★", "Brings nuts: +2 ★"), LIFE, Perk("🌱", "Digs up seeds you just planted", false)),
        "hummingbird" to listOf(Perk("✚", "Can heal your pal when sick"), LIFE, Perk("💢", "Zips by and startles: -1 heart", false)),
        "deer" to listOf(Perk("⏳", "Longest life: +72 min a day"), Perk("🦌", "Calm garden, gentle company"), Perk("🍅", "Eats crops, ripe or growing", false)),

        // decor: perks only, no catch
        "flower_pots" to listOf(Perk("🌼", "+2 flowers: harvests pay more ★")),
        "gnome" to listOf(Perk("★", "Lucky: +1 ★ on every harvest")),
        "birdbath" to listOf(Perk("🍓", "Bluebird brings berries 2x as often")),
        "rain_barrel" to listOf(Perk("💧", "Watering lasts 1 hour longer")),
        "lantern" to listOf(Perk("🏮", "Glows at night"), Perk("🌙", "Fireflies can't keep your pal up")),
        "bench" to listOf(Perk("🪑", "A cozy spot - just for looks")),
        "pond" to listOf(Perk("💦", "Cool water: no more bee stings")),
        "well" to listOf(Perk("✨", "About one wish a day: +3 ★")),
        "scarecrow" to listOf(Perk("🌾", "Birds and deer raid crops half as often")),
    )

    /** Rolls every whole hour since the last time; returns the new save and what happened. */
    fun catchUp(save: Save, now: Long, zone: ZoneId, rng: Random): Pair<Save, List<String>> {
        if (save.visitorsAt == 0L) return save.copy(visitorsAt = now) to emptyList()
        val hours = ((now - save.visitorsAt) / HOUR).toInt()
        if (hours <= 0) return save to emptyList()
        var s = save
        val news = mutableListOf<String>()
        val first = maxOf(0, hours - MAX_HOURS)
        for (h in first until hours) {
            val at = save.visitorsAt + (h + 1) * HOUR
            val hourOfDay = Instant.ofEpochMilli(at).atZone(zone).hour
            val night = hourOfDay >= 22 || hourOfDay < 7
            s = Weather.plan(s, at, zone, rng)
            val (w, weatherNews) = Weather.begin(s, at, rng)
            s = w
            if (weatherNews != null) news += weatherNews
            val (next, said) = hour(s, at, night, rng)
            s = next
            news += said
        }
        return s.copy(visitorsAt = save.visitorsAt + hours * HOUR) to news
    }

    /** One hour of garden life. */
    fun hour(save: Save, at: Long, night: Boolean, rng: Random): Pair<Save, List<String>> {
        var s = save
        val news = mutableListOf<String>()
        val showing = Shop.ITEMS.filter { it.tab == ShopTab.CREATURES && !it.repeatable && Shop.showing(save, it.id) }.map { it.id }
        // after their day out, creatures go home until switched on again in the shop
        val (home, out) = showing.partition { (s.outUntil[it] ?: Long.MAX_VALUE) <= at }
        if (home.isNotEmpty()) {
            s = s.copy(hidden = s.hidden + home, outUntil = s.outUntil - home.toSet())
            news += home.joinToString(", ") { Shop.byId(it)!!.name } + "\nWENT HOME. SAY HI\nIN THE SHOP!"
        }
        // creatures from before the time limit get their day starting now
        val fresh = out.filter { it !in s.outUntil }
        if (fresh.isNotEmpty()) s = s.copy(outUntil = s.outUntil + fresh.associateWith { at + Shop.OUT_HOURS * HOUR })
        fun chance(pct: Int) = rng.nextInt(100) < pct
        fun has(id: String) = Shop.showing(s, id)

        // decor that does something on its own
        if (has("well") && chance(4)) { s = s.copy(points = s.points + 3); news += "A WISH CAME TRUE\nAT THE WELL! +3 ★" }

        // in a storm the creatures hide: no perks, no mischief
        if (out.isEmpty() || Weather.now(s, at) == WeatherKind.STORM) return s to news
        val alive = s.pet.ended == null && s.pet.hatchedAt != null
        fun pet(f: (PetState) -> PetState) { if (alive) s = s.copy(pet = f(s.pet)) }
        val awake = !night
        // decor that changes what the creatures do
        val raid = if (has("scarecrow")) 2 else 1          // scarecrow: birds and deer raid half as often
        val berry = if (has("birdbath")) 12 else 6         // birdbath: berries twice as often
        val stings = !has("pond")                          // pond: no more stings
        val lateNights = !has("lantern")                   // lantern: fireflies can't keep the pal up
        // the pal's own ability may stop the mischief (Jason, Jun, the dogs - see Abilities)
        fun ok(): Boolean = Abilities.blocks(s.pet.species, fromCreature = true, rng)?.let { news += it; false } ?: true
        fun bad(pct: Int) = chance(pct) && ok()

        // a little longer life for every creature out
        if (alive) pet { it.copy(bonusLife = it.bonusLife + out.sumOf { id -> if (id == "deer") DEER_LIFE else LIFE_MINUTES }) }

        for (id in out) when (id) {
            "ladybug" -> if (s.pantry.isNotEmpty() && bad(2)) {
                s = s.copy(pantry = s.pantry.drop(1)); news += "A LADYBUG GOT INTO\nTHE PANTRY!"
            }
            "butterfly" -> {
                if (alive && awake && chance(10)) { pet { it.copy(happy = (it.happy + 1).coerceAtMost(Rules.MAX_HEARTS)) } }
                if (alive && awake && bad(3)) { pet { it.copy(hunger = (it.hunger - 1).coerceAtLeast(0)) }; news += "CHASED THE BUTTERFLY.\nSO HUNGRY NOW!" }
            }
            "bee" -> {
                if (s.flowers < MAX_FLOWERS && chance(15)) s = s.copy(flowers = s.flowers + 1)
                if (stings && alive && awake && !s.pet.sick && bad(2)) { pet { it.copy(sick = true) }; news += "OUCH! A BEE STING.\nTIME FOR MEDICINE" }
            }
            "fireflies" -> if (night && alive) {
                if (chance(10)) pet { it.copy(happy = (it.happy + 1).coerceAtMost(Rules.MAX_HEARTS)) }
                if (lateNights && bad(3)) { pet { it.copy(happy = (it.happy - 1).coerceAtLeast(0)) }; news += "THE FIREFLIES KEPT\nYOUR PAL UP LATE" }
            }
            "bluebird" -> {
                if (chance(berry)) { s = s.copy(pantry = s.pantry + "strawberry"); news += "THE BLUEBIRD BROUGHT\nA BERRY!" }
                if (bad(4 / raid)) Garden.loseRipe(s, at)?.let { s = it.first; news += "THE BLUEBIRD PECKED\nYOUR ${it.second.name}!" }
            }
            "bunny" -> {
                if (alive && awake && chance(8)) pet { it.copy(happy = (it.happy + 1).coerceAtMost(Rules.MAX_HEARTS)) }
                if (bad(4)) Garden.setBack(s, at, 30)?.let { s = it.first; news += "THE BUNNY NIBBLED\nYOUR ${it.second.name}" }
            }
            "squirrel" -> {
                if (chance(8)) { s = s.copy(points = s.points + 2); news += "THE SQUIRREL BROUGHT\nNUTS! +2 ★" }
                if (bad(4)) Garden.digUpSeed(s, at)?.let { s = it.first; news += "THE SQUIRREL DUG UP\nA ${it.second.name} SEED!" }
            }
            "hummingbird" -> {
                if (alive && s.pet.sick && chance(15)) { pet { it.copy(sick = false, sickMinutes = 0, doses = 0) }; news += "THE HUMMINGBIRD\nMADE YOUR PAL BETTER!" }
                if (alive && awake && bad(3)) pet { it.copy(happy = (it.happy - 1).coerceAtLeast(0)) }
            }
            "deer" -> if (rng.nextInt(100 * raid) < 5 && ok()) {
                (Garden.loseRipe(s, at) ?: Garden.setBack(s, at, 60))?.let { s = it.first; news += "THE DEER ATE SOME\nOF YOUR ${it.second.name}!" }
            }
        }
        return s to news
    }
}
