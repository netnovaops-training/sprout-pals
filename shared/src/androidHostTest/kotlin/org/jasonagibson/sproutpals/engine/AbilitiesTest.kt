package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AbilitiesTest {
    @Test fun `Jason stops every bad effect but can't visit`() {
        repeat(50) { assertNotNull(Abilities.blocks(Species.JASON, fromCreature = it % 2 == 0, Random(it))) }
        assertFalse(Abilities.canVisit(Species.JASON))
        assertTrue(Abilities.canVisit(Species.KONA))
    }

    @Test fun `Jun shrugs off about half, the dogs chase off half the creatures, Nathan none`() {
        val rng = Random(3)
        val jun = (1..2000).count { Abilities.blocks(Species.JUN, false, rng) != null }
        assertTrue("$jun", jun in 900..1100)
        val dog = (1..2000).count { Abilities.blocks(Species.RUSTY, true, rng) != null }
        assertTrue("$dog", dog in 900..1100)
        assertNull(Abilities.blocks(Species.RUSTY, fromCreature = false, rng))   // dogs can't chase the weather
        assertNull(Abilities.blocks(Species.NATHAN, true, rng))
    }

    @Test fun `Kona's hearts last longer and Jun's water lasts longer`() {
        assertEquals(225, Abilities.happyEvery(Species.KONA, 150))
        assertEquals(150, Abilities.happyEvery(Species.JUN, 150))
        val pet = PetState(species = Species.JUN, bornAt = 0, lastTick = 0)
        assertEquals(Garden.WET_MINUTES + 60, Garden.wetMinutes(Save(pet = pet)))
    }

    @Test fun `cats bring a guest creature for 4 hours that isn't owned and never misbehaves`() {
        val t0 = 1_800_000_000_000L
        val hour = 3_600_000L
        val dj = PetState(species = Species.DJ, bornAt = 0, lastTick = t0, hatchedAt = t0, stage = Stage.ADULT)
        var s = Save(pet = dj, visitorsAt = t0, weatherDay = Long.MAX_VALUE)
        var guestSeen = false
        for (h in 1..200) {
            s = Visitors.catchUp(s, t0 + h * hour, java.time.ZoneId.of("UTC"), Random(h)).first
            s.guestCreature?.let { g ->
                guestSeen = true
                assertTrue(Visitors.present(s, g, t0 + h * hour))
                assertFalse(Shop.owns(s, g))
                assertTrue(s.guestUntil - (t0 + h * hour) <= Abilities.GUEST_HOURS * hour)
            }
        }
        assertTrue(guestSeen)
        assertTrue(s.unlocked.isEmpty())
    }

    @Test fun `Nathan borrows Jun's good side while Jun is over`() {
        val junHour = (0L until 100).first { Species.JUN in Abilities.company(Species.NATHAN, it) }
        val quietHour = (0L until 100).first { Abilities.company(Species.NATHAN, it).isEmpty() }
        assertEquals(60, Abilities.extraWater(Species.NATHAN, junHour))
        assertEquals(0, Abilities.extraWater(Species.NATHAN, quietHour))
        assertTrue(Abilities.canVisit(Species.NATHAN))
    }

    @Test fun `Karta always has two friends over, Nathan sometimes one, never themselves`() {
        for (h in 0L until 48) {
            val k = Abilities.company(Species.KARTA, h)
            assertEquals(2, k.size); assertFalse(Species.KARTA in k); assertEquals(2, k.toSet().size)
            val n = Abilities.company(Species.NATHAN, h)
            assertTrue(n.all { it == Species.KARTA || it == Species.JUN })
        }
        assertTrue((0L until 48).any { Abilities.company(Species.NATHAN, it).isNotEmpty() })
        assertTrue(Abilities.company(Species.KONA, 5).isEmpty())
    }
}
