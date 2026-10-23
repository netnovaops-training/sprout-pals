package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShopTest {
    private val pet = PetState(species = Species.JUN, bornAt = 0, lastTick = 0)
    private val butterfly = Shop.ITEMS.first { it.id == "butterfly" }

    @Test fun `buying spends points and the item shows in the garden`() {
        val (after, r) = Shop.press(Save(pet = pet, points = 10), butterfly)
        assertEquals(ShopResult.BOUGHT, r)
        assertEquals(2, after.points)
        assertTrue(Shop.showing(after, "butterfly"))
    }

    @Test fun `prices climb from the top of the list down`() {
        Shop.ITEMS.zipWithNext().forEach { (a, b) -> assertTrue("${a.name} ${a.price} < ${b.name} ${b.price}", a.price < b.price) }
    }

    @Test fun `can't buy without enough points`() {
        val (after, r) = Shop.press(Save(pet = pet, points = 3), butterfly)
        assertEquals(ShopResult.TOO_POOR, r)
        assertEquals(3, after.points)
        assertFalse(Shop.owns(after, "butterfly"))
    }

    @Test fun `pressing an owned item hides it, then shows it again, for free`() {
        val owned = Save(pet = pet, points = 0, unlocked = listOf("butterfly"))
        val (hidden, r1) = Shop.press(owned, butterfly)
        assertEquals(ShopResult.HIDDEN, r1)
        assertFalse(Shop.showing(hidden, "butterfly"))
        val (shown, r2) = Shop.press(hidden, butterfly)
        assertEquals(ShopResult.SHOWN, r2)
        assertTrue(Shop.showing(shown, "butterfly"))
        assertEquals(0, shown.points)
    }

    @Test fun `every regular character is equally likely, Jason is rare`() {
        val rng = Random(12)
        val n = 120_000
        val counts = (1..n).groupingBy { PetEngine.pickSpecies(rng) }.eachCount()
        for (sp in Species.entries.filter { !it.rare }) {
            val share = counts.getValue(sp).toDouble() / n
            assertTrue("$sp got $share", share in 0.155..0.175)
        }
        assertTrue((counts[Species.JASON] ?: 0).toDouble() / n in 0.007..0.013)
    }
}
