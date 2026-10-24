package org.jasonagibson.sproutpals.engine

/**
 * The walking adventure: real garden shops around Clifton Park, NY, 50 walked miles apart.
 * Reach one and your pal finds a unique cosmetic there. Starts close to home and works
 * outward to Ballston Spa, Saratoga, Latham, Cohoes, Schenectady and Albany.
 *
 * Shop list researched 2026-10-23 from Yelp / Yellow Pages / store locators; places change,
 * so this list is easy to edit. Distances are walked steps, not map miles.
 */
data class Stop(val name: String, val town: String, val prizeId: String, val prize: String)

object Route {
    const val MILES_APART = 50
    /** A typical adult walking stride: about 2,000 steps to the mile. */
    const val STEPS_PER_MILE = 2_000L
    const val STEPS_PER_STOP = MILES_APART * STEPS_PER_MILE

    val STOPS = listOf(
        Stop("Sokolowski Greenhouses", "Clifton Park", "seedling_hat", "Seedling Hat"),
        Stop("Hewitt's Garden Center", "Clifton Park", "sunflower_pin", "Sunflower Pin"),
        Stop("The Home Depot", "Clifton Park", "orange_apron", "Orange Work Apron"),
        Stop("Lowe's", "Halfmoon", "blue_cap", "Blue Ball Cap"),
        Stop("Native Sun Flowers", "Halfmoon", "daisy_crown", "Daisy Crown"),
        Stop("Tractor Supply Garden Center", "Halfmoon", "red_bandana", "Red Bandana"),
        Stop("Brookside Nursery", "Ballston Spa", "watering_can", "Tiny Watering Can"),
        Stop("Malta Farm & Garden", "Malta", "farmer_hat", "Farmer's Straw Hat"),
        Stop("Sunnyside Gardens", "Saratoga Springs", "sun_visor", "Sunny Visor"),
        Stop("Lowe's", "Saratoga Springs", "tool_belt", "Tool Belt"),
        Stop("Olde Saratoga Home & Garden", "Saratoga Springs", "jockey_cap", "Jockey Cap"),
        Stop("Kulak Nursery", "Rexford", "pine_backpack", "Pine Sapling Backpack"),
        Stop("Faddegon's Nursery", "Latham", "mum_bouquet", "Mum Bouquet"),
        Stop("The Home Depot", "Latham", "hard_hat", "Hard Hat"),
        Stop("Lowe's", "Latham", "garden_gloves", "Garden Gloves"),
        Stop("Valoze Greenhouses", "Cohoes", "rose", "Rose in Bloom"),
        Stop("Hewitt's Garden Center", "Schenectady", "hanging_basket", "Hanging Basket"),
        Stop("Lowe's", "Glenville", "trowel", "Trusty Trowel"),
        Stop("Lowe's", "Schenectady", "rain_boots", "Rain Boots"),
        Stop("The Home Depot", "Schenectady", "paint_overalls", "Paint-Splat Overalls"),
        Stop("The Home Depot", "Albany", "pumpkin_hat", "Pumpkin Hat"),
        Stop("Lowe's", "Colonie", "golden_trowel", "Golden Trowel"),
    )

    fun miles(steps: Long) = steps.toDouble() / STEPS_PER_MILE

    /** How many stops these lifetime steps have reached. */
    fun reached(steps: Long): Int = (steps / STEPS_PER_STOP).toInt().coerceAtMost(STOPS.size)

    fun next(steps: Long): Stop? = STOPS.getOrNull(reached(steps))

    fun milesToNext(steps: Long): Double? {
        if (next(steps) == null) return null
        val target = (reached(steps) + 1) * STEPS_PER_STOP
        return (target - steps).toDouble() / STEPS_PER_MILE
    }

    /**
     * Hands out prizes for stops reached since last time. Returns the updated save and
     * the newly reached stops (usually none, sometimes one; several if catching up).
     */
    fun collect(save: Save): Pair<Save, List<Stop>> {
        val now = reached(save.stepsTotal)
        if (now <= save.stopsReached) return save to emptyList()
        val fresh = STOPS.subList(save.stopsReached, now)
        val unlocked = save.unlocked + fresh.map { it.prizeId }.filter { it !in save.unlocked }
        return save.copy(stopsReached = now, unlocked = unlocked) to fresh
    }
}
