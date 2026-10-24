package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenTest {
    private val pet = PetState(species = Species.DJ, bornAt = 0, lastTick = 0)   // no ability: plain garden rules
    private val min = 60_000L
    private val t0 = 1_000_000_000L

    @Test fun `a planted seed is dry and does not grow until watered`() {
        val s = Garden.plant(Save(pet = pet), 0, "radish", t0)
        val p = Garden.plots(s)[0]
        assertEquals(PlotState.DRY, Garden.state(p, t0 + 500 * min))
        assertEquals(60, Garden.minutesLeft(p, t0 + 500 * min))
    }

    @Test fun `it grows only while wet, and ripens`() {
        var s = Garden.plant(Save(pet = pet), 1, "carrot", t0)   // 120 wet minutes
        s = Garden.water(s, 1, t0)
        assertEquals(PlotState.GROWING, Garden.state(Garden.plots(s)[1], t0 + 60 * min))
        assertEquals(1, Garden.growth(Garden.plots(s)[1], t0 + 60 * min))
        assertEquals(PlotState.RIPE, Garden.state(Garden.plots(s)[1], t0 + 120 * min))
    }

    @Test fun `dry spells pause growth and a later watering carries on`() {
        var s = Garden.plant(Save(pet = pet), 0, "pumpkin", t0)  // 360 wet minutes
        s = Garden.water(s, 0, t0)                               // wet for 180
        val later = t0 + 600 * min
        assertEquals(PlotState.DRY, Garden.state(Garden.plots(s)[0], later))
        assertEquals(180, Garden.minutesLeft(Garden.plots(s)[0], later))
        s = Garden.water(s, 0, later)
        assertEquals(PlotState.RIPE, Garden.state(Garden.plots(s)[0], later + 180 * min))
    }

    @Test fun `harvest pays points, fills the pantry and clears the plot`() {
        var s = Garden.plant(Save(pet = pet, points = 1), 2, "radish", t0)
        s = Garden.water(s, 2, t0)
        assertNull(Garden.harvest(s, 2, t0 + 30 * min))
        val (after, crop) = Garden.harvest(s, 2, t0 + 60 * min)!!
        assertEquals("radish", crop.id)
        assertEquals(3, after.points)
        assertEquals(listOf("radish"), after.pantry)
        assertEquals(PlotState.EMPTY, Garden.state(Garden.plots(after)[2], t0 + 61 * min))
        val (eaten, _) = Garden.takeFood(after)!!
        assertTrue(eaten.pantry.isEmpty())
    }

    @Test fun `a berry bush stays planted after picking, half grown`() {
        var s = Garden.plant(Save(pet = pet), 0, "berries", t0)
        s = Garden.water(s, 0, t0)
        val (after, _) = Garden.harvest(s, 0, t0 + 150 * min)!!
        val p = Garden.plots(after)[0]
        assertEquals("berries", p.crop)
        assertEquals(75, Garden.minutesLeft(p, t0 + 150 * min))
    }

    @Test fun `golden seeds are rare, needed to plant gold, and used up`() {
        val none = Save(pet = pet)
        assertTrue(Garden.seeds(none).none { it.rare })
        assertEquals(none, Garden.plant(none, 0, "golden", t0))
        val (lucky, found) = Garden.maybeGoldenSeed(none, roll = 0)
        assertTrue(found)
        assertTrue(Garden.seeds(lucky).any { it.rare })
        val planted = Garden.plant(lucky, 0, "golden", t0)
        assertEquals(0, planted.goldenSeeds)
        assertEquals("golden", Garden.plots(planted)[0].crop)
        assertEquals(false, Garden.maybeGoldenSeed(none, roll = 50).second)
    }

    @Test fun `garden food fills and cheers without extra weight`() {
        val hungry = pet.copy(stage = Stage.KID, hatchedAt = 0, hunger = 1, happy = 1)
        val (fed, outcome) = PetEngine.feedHarvest(hungry)
        assertEquals(Outcome.OK, outcome)
        assertEquals(2, fed.hunger)
        assertEquals(2, fed.happy)
        assertEquals(hungry.weight, fed.weight)
    }
}
