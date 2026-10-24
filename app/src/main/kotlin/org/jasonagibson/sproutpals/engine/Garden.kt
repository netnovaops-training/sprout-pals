package org.jasonagibson.sproutpals.engine

import kotlinx.serialization.Serializable

/**
 * The garden bed: three plots. Plant a seed, keep the soil wet, harvest when ripe.
 * Crops only grow while the soil is wet; one watering keeps it wet for [WET_MINUTES].
 * A harvest pays points and puts the crop in the pantry, where the Feed menu can serve it.
 * All times are real clock milliseconds, so the bed keeps growing while the app is closed.
 */
/** [regrows]: picking leaves the plant, half-grown again. [rare]: needs a golden seed to plant. */
data class Crop(val id: String, val name: String, val growMinutes: Int, val points: Int, val regrows: Boolean = false, val rare: Boolean = false)

@Serializable
data class Plot(
    val crop: String? = null,
    /** Wet minutes grown as of [updatedAt] (the last planting or watering). */
    val grown: Int = 0,
    val wetUntil: Long = 0,
    val updatedAt: Long = 0,
)

enum class PlotState { EMPTY, DRY, GROWING, RIPE }

object Garden {
    const val PLOTS = 3
    const val WET_MINUTES = 180
    private const val MINUTE = 60_000L

    val CROPS = listOf(
        Crop("radish", "RADISH", 60, 2),
        Crop("carrot", "CARROT", 120, 4),
        Crop("strawberry", "STRAWBERRY", 180, 6),
        Crop("tomato", "TOMATO", 240, 8),
        Crop("pumpkin", "PUMPKIN", 360, 12),
        Crop("berries", "BERRY BUSH", 150, 4, regrows = true),
        Crop("sunflower", "SUNFLOWER", 420, 15),
        Crop("golden", "GOLDEN SPROUT", 480, 40, rare = true),
    )

    /** Chance (in 100) that a harvest turns up a golden seed. */
    const val GOLDEN_SEED_PERCENT = 4

    /** The seeds on offer: every ordinary seed, plus golden if you have one. */
    fun seeds(save: Save) = CROPS.filter { !it.rare || save.goldenSeeds > 0 }

    fun crop(id: String?) = CROPS.firstOrNull { it.id == id }

    /** The bed in a save, always [PLOTS] long (older saves have none). */
    fun plots(save: Save) = save.plots + List((PLOTS - save.plots.size).coerceAtLeast(0)) { Plot() }

    /** Wet minutes grown by [now]: what was grown at the last watering, plus wet time since. */
    fun grownAt(p: Plot, now: Long): Int {
        val c = crop(p.crop) ?: return 0
        val wet = ((minOf(now, p.wetUntil) - p.updatedAt) / MINUTE).toInt().coerceAtLeast(0)
        return (p.grown + wet).coerceAtMost(c.growMinutes)
    }

    fun state(p: Plot, now: Long): PlotState {
        val c = crop(p.crop) ?: return PlotState.EMPTY
        return when {
            grownAt(p, now) >= c.growMinutes -> PlotState.RIPE
            now >= p.wetUntil -> PlotState.DRY
            else -> PlotState.GROWING
        }
    }

    /** 0 seed, 1 sprout, 2 leafy, 3 ripe. */
    fun growth(p: Plot, now: Long): Int {
        val c = crop(p.crop) ?: return 0
        val g = grownAt(p, now)
        return if (g >= c.growMinutes) 3 else (g * 3 / c.growMinutes).coerceIn(0, 2)
    }

    /** Minutes of wet soil still needed. */
    fun minutesLeft(p: Plot, now: Long): Int {
        val c = crop(p.crop) ?: return 0
        return (c.growMinutes - grownAt(p, now)).coerceAtLeast(0)
    }

    private fun Save.withPlot(i: Int, p: Plot) = copy(plots = plots(this).toMutableList().also { it[i] = p })

    /** Plants into an empty plot. Seeds are free; a fresh seed starts dry - water it! */
    fun plant(save: Save, i: Int, cropId: String, now: Long): Save {
        val c = crop(cropId) ?: return save
        if (plots(save)[i].crop != null || (c.rare && save.goldenSeeds <= 0)) return save
        val s = if (c.rare) save.copy(goldenSeeds = save.goldenSeeds - 1) else save
        return s.withPlot(i, Plot(crop = cropId, updatedAt = now))
    }

    fun water(save: Save, i: Int, now: Long): Save {
        val p = plots(save)[i]
        val wet = wetMinutes(save, now)
        if (p.crop == null || wet == 0) return save   // frozen soil: watering does nothing
        return save.withPlot(i, p.copy(grown = grownAt(p, now), updatedAt = now, wetUntil = now + wet * MINUTE))
    }

    /** Picks a ripe crop: points, and one into the pantry. Returns null if it isn't ripe. */
    fun harvest(save: Save, i: Int, now: Long): Pair<Save, Crop>? {
        val p = plots(save)[i]
        val c = crop(p.crop) ?: return null
        if (state(p, now) != PlotState.RIPE) return null
        val pantry = save.pantry + c.id
        // a berry bush stays planted and comes back half-grown (dry - it wants water again)
        val after = if (c.regrows) Plot(crop = c.id, grown = c.growMinutes / 2, updatedAt = now) else Plot()
        return save.withPlot(i, after).copy(points = save.points + harvestPoints(save, c, now), pantry = pantry) to c
    }

    /** After a harvest: maybe a golden seed turned up in the soil. */
    fun maybeGoldenSeed(save: Save, roll: Int): Pair<Save, Boolean> =
        if (roll < GOLDEN_SEED_PERCENT) save.copy(goldenSeeds = save.goldenSeeds + 1) to true else save to false

    /** How long one watering keeps the soil wet: an hour more each for the ladybug and the rain barrel; half in a heat wave. */
    fun wetMinutes(save: Save, now: Long = 0L): Int {
        val m = WET_MINUTES + (if (Shop.showing(save, "ladybug")) 60 else 0) + (if (Shop.showing(save, "rain_barrel")) 60 else 0) +
            Abilities.extraWater(save.pet.species)
        return when (Weather.now(save, now)) {
            WeatherKind.HEAT -> if (save.pet.species == Species.JASON) m else m / 2   // Jason troubleshoots the heat
            WeatherKind.SNOW -> 0   // frozen soil: the water just ices over
            else -> m
        }
    }

    /** Flowers on the lawn: the bees' plus two from the flower pots. */
    fun flowers(save: Save) = save.flowers + if (Shop.showing(save, "flower_pots")) 2 else 0

    /** A crop's stars: +1 for every two flowers, +1 for the gnome; weather: +2 in warm sun, doubled under a rainbow. */
    fun harvestPoints(save: Save, c: Crop, now: Long = 0L): Int {
        val base = c.points + flowers(save) / 2 + (if (Shop.showing(save, "gnome")) 1 else 0)
        return when (Weather.now(save, now)) {
            WeatherKind.RAINBOW -> base * 2
            WeatherKind.SUN -> base + 2
            else -> base
        }
    }

    // ----- creature mischief (see Visitors) -----

    /** A ripe crop gets eaten: the plot is cleared. */
    fun loseRipe(save: Save, now: Long): Pair<Save, Crop>? {
        val i = plots(save).indexOfFirst { state(it, now) == PlotState.RIPE }
        if (i < 0) return null
        return save.withPlot(i, Plot()) to crop(plots(save)[i].crop)!!
    }

    /** A growing crop gets nibbled: it loses [minutes] of growth. */
    fun setBack(save: Save, now: Long, minutes: Int): Pair<Save, Crop>? {
        val i = plots(save).indexOfFirst { it.crop != null && state(it, now) != PlotState.RIPE && grownAt(it, now) > 0 }
        if (i < 0) return null
        val p = plots(save)[i]
        return save.withPlot(i, p.copy(grown = (grownAt(p, now) - minutes).coerceAtLeast(0), updatedAt = now)) to crop(p.crop)!!
    }

    /** A just-planted seed (nothing grown yet) gets dug up. */
    fun digUpSeed(save: Save, now: Long): Pair<Save, Crop>? {
        val i = plots(save).indexOfFirst { it.crop != null && grownAt(it, now) == 0 }
        if (i < 0) return null
        return save.withPlot(i, Plot()) to crop(plots(save)[i].crop)!!
    }

    /** Takes the first crop out of the pantry for a meal. */
    fun takeFood(save: Save): Pair<Save, Crop>? {
        val id = save.pantry.firstOrNull() ?: return null
        val c = crop(id) ?: return null
        return save.copy(pantry = save.pantry.drop(1)) to c
    }
}
