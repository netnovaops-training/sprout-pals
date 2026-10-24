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
