package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShopTest {
    private val pet = PetState(species = Species.JUN, bornAt = 0, lastTick = 0)
    private fun item(id: String) = Shop.ITEMS.first { it.id == id }

    @Test fun `buying spends points and brings the item straight out`() {
        val (after, r) = Shop.press(Save(pet = pet, points = 25), item("butterfly"))
        assertEquals(ShopResult.BOUGHT, r)
        assertEquals(5, after.points)
        assertTrue(Shop.showing(after, "butterfly"))
    }

    @Test fun `a creature goes home after its day out, and comes back when switched on`() {
        val t0 = 1_800_000_000_000L
        val hour = 3_600_000L
        var s = Shop.press(Save(pet = pet, points = 50, visitorsAt = t0), item("ladybug"), t0).first
        s = Visitors.catchUp(s, t0 + 23 * hour, java.time.ZoneId.of("UTC"), Random(1)).first
        assertTrue(Shop.showing(s, "ladybug"))
        val (gone, news) = Visitors.catchUp(s, t0 + 25 * hour, java.time.ZoneId.of("UTC"), Random(1))
        assertFalse(Shop.showing(gone, "ladybug"))
        assertTrue(news.any { "WENT HOME" in it })
        val (back, r) = Shop.press(gone, item("ladybug"), t0 + 25 * hour)
        assertEquals(ShopResult.SHOWN, r)
        assertEquals(gone.points, back.points)
        assertTrue(Shop.showing(back, "ladybug"))
    }

    @Test fun `all creatures can be out at once, each switched off on its own`() {
        var s = Save(pet = pet, points = 500)
        s = Shop.press(s, item("butterfly")).first
        s = Shop.press(s, item("bunny")).first
        assertTrue(Shop.showing(s, "butterfly") && Shop.showing(s, "bunny"))
        val before = s.points
        val (off, r) = Shop.press(s, item("butterfly"))
        assertEquals(ShopResult.HIDDEN, r)
        assertFalse(Shop.showing(off, "butterfly"))
        assertTrue(Shop.showing(off, "bunny"))
        assertEquals(before, off.points)
        assertTrue(Shop.showing(Shop.press(off, item("butterfly")).first, "butterfly"))
    }

    @Test fun `one hat, one shirt, one pair of pants at a time`() {
        var s = Save(pet = pet, points = 500)
        for (id in listOf("beanie", "green_tee", "jeans", "sun_hat")) s = Shop.press(s, item(id)).first
        assertEquals("sun_hat", Shop.worn(s, Slot.HAT))
        assertEquals("green_tee", Shop.worn(s, Slot.SHIRT))
        assertEquals("jeans", Shop.worn(s, Slot.PANTS))
        assertFalse(Shop.showing(s, "beanie"))
        // taking the shirt off leaves the rest on
        s = Shop.press(s, item("green_tee")).first
        assertNull(Shop.worn(s, Slot.SHIRT))
        assertEquals("sun_hat", Shop.worn(s, Slot.HAT))
    }

    @Test fun `can't buy without enough points`() {
        val (after, r) = Shop.press(Save(pet = pet, points = 19), item("butterfly"))
        assertEquals(ShopResult.TOO_POOR, r)
        assertFalse(Shop.owns(after, "butterfly"))
    }

    @Test fun `prices climb from the top of each page down`() {
        val s = Save(pet = pet)
        for (tab in ShopTab.entries) {
            Shop.items(tab, s).zipWithNext().forEach { (a, b) -> assertTrue("${a.name} ${a.price} < ${b.name} ${b.price}", a.price < b.price) }
        }
    }

    @Test fun `the last creature-page item adds 5 each time up to 15, then leaves the shop`() {
        var s = Save(pet = pet, points = 10_000)
        assertEquals(1, Shop.rarePercent(s))
        s = Shop.press(s, item(Rules.MYSTERY_ITEM)).first
        assertEquals(6, Shop.rarePercent(s))
        s = Shop.press(s, item(Rules.MYSTERY_ITEM)).first
        assertEquals(11, Shop.rarePercent(s))
        assertTrue(Shop.items(ShopTab.CREATURES, s).any { it.repeatable })
        s = Shop.press(s, item(Rules.MYSTERY_ITEM)).first
        assertEquals(15, Shop.rarePercent(s))
        assertFalse("gone from the shop", Shop.items(ShopTab.CREATURES, s).any { it.repeatable })
        assertEquals(ShopResult.MAXED, Shop.press(s, item(Rules.MYSTERY_ITEM)).second)
    }

    @Test fun `every regular character is equally likely, the rare one is rare`() {
        val rng = Random(12)
        val n = 120_000
        val counts = (1..n).groupingBy { PetEngine.pickSpecies(rng) }.eachCount()
        for (sp in Species.entries.filter { !it.rare }) {
            val share = counts.getValue(sp).toDouble() / n
            assertTrue("$sp got $share", share in 0.114..0.134)   // 99% / 8 regulars
        }
        assertTrue((counts[Species.JASON] ?: 0).toDouble() / n in 0.007..0.013)
    }
}
