package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

/** 21, SLOTS, SCARE THE CROWS, and the one-day SCARECROW WATCH (added 10-25). */
class NewGamesTest {

    // ---------- 21 ----------

    @Test fun `21 counts aces as 11 or 1 and picture cards as 10`() {
        assertEquals(21, TwentyOne.total(listOf(1, 13)))          // ace + king
        assertEquals(12, TwentyOne.total(listOf(1, 1)))           // two aces: 11 + 1
        assertEquals(21, TwentyOne.total(listOf(1, 5, 5)))
        assertEquals(15, TwentyOne.total(listOf(1, 4, 10)))       // the ace drops to 1
        assertEquals(30, TwentyOne.total(listOf(12, 11, 10)))
    }

    @Test fun `21 plays five hands, the pal draws to 17, and staying always finishes`() {
        for (seed in 1..20) {
            val g = TwentyOne(Random(seed))
            var steps = 0
            while (!g.over && steps < 5_000) {
                if (g.yourTurn()) { if (TwentyOne.total(g.you) < 15) g.a() else g.b() }
                g.step(); steps++
                if (g.callout.isNotEmpty() && TwentyOne.total(g.you) <= 21) assertTrue(TwentyOne.total(g.pal) >= 17 || g.pal.size == 2)
            }
            assertTrue(g.over)
            assertEquals(5, g.played)
            assertTrue(g.score in 0..15)
            assertTrue(g.points() in 0..10)
        }
    }

    @Test fun `21 - going over is a bust, and C cashes out at once`() {
        val g = TwentyOne(Random(3))
        while (g.yourTurn()) g.a()                                 // keep taking cards
        assertTrue(TwentyOne.total(g.you) >= 21)
        if (TwentyOne.total(g.you) > 21) assertEquals("BUST!", g.callout)
        val quit = TwentyOne(Random(4))
        quit.c()
        assertTrue(quit.over)
        assertEquals(0, quit.played)
    }

    // ---------- SLOTS ----------

    @Test fun `slots pay 5 for three alike, 10 for three stars, 1 for a pair, never for bugs`() {
        assertEquals(5, Slots.payout(0, 0, 0))
        assertEquals(10, Slots.payout(Slots.STAR, Slots.STAR, Slots.STAR))
        assertEquals(1, Slots.payout(2, 3, 2))
        assertEquals(1, Slots.payout(3, 1, 1))
        assertEquals(0, Slots.payout(Slots.BUG, Slots.BUG, Slots.BUG))
        assertEquals(0, Slots.payout(Slots.BUG, 1, Slots.BUG))
        assertEquals(0, Slots.payout(0, 1, 2))
    }

    private fun spinOut(g: Slots) { var n = 0; while (g.spinning() && n++ < 100) g.step() }

    @Test fun `a held reel stays put for two pulls, then lets go and can't be held right away`() {
        val g = Slots(Random(5))
        g.b()
        assertEquals("no hold before the first pull", -1, g.held)
        g.a(); spinOut(g)
        g.b()
        assertEquals(0, g.held)
        g.b()
        assertEquals("B moves the hold to the next reel", 1, g.held)
        val kept = g.reels[1]
        g.a(); spinOut(g)
        assertEquals(kept, g.reels[1])
        g.b()
        assertEquals("a hold can't be moved once used", 1, g.held)
        g.a(); spinOut(g)
        assertEquals(kept, g.reels[1])
        assertEquals("released after 2 pulls", -1, g.held)
        g.b()
        assertEquals("needs one free pull first", -1, g.held)
        g.a(); spinOut(g)
        g.b()
        assertEquals(0, g.held)
    }

    @Test fun `slots end after ten pulls, or when C cashes out`() {
        val g = Slots(Random(6))
        var steps = 0
        while (!g.over && steps < 5_000) { if (!g.spinning()) g.a(); g.step(); steps++ }
        assertTrue(g.over)
        assertEquals(0, g.pullsLeft)
        assertTrue(g.points() in 0..10)
        val quit = Slots(Random(7))
        quit.c()
        assertTrue(quit.over)
    }

    // ---------- SCARE THE CROWS ----------

    @Test fun `doing nothing lets the crows peck until the game ends early`() {
        val g = ScareCrows(Random(8))
        var steps = 0
        while (!g.over && steps < 10_000) { g.step(); steps++ }
        assertEquals(g.maxPecks, g.pecks)
        assertTrue(steps < 30 * 8)
        assertEquals(0, g.score)
    }

    @Test fun `a quick scarecrow lasts the full 30 seconds and shoos plenty`() {
        val g = ScareCrows(Random(9))
        var steps = 0
        while (!g.over && steps < 10_000) {
            // walk to the crow closest to pecking and wave when there
            val target = (0 until g.beds).filter { g.crow[it] > 0 }.minByOrNull { g.crow[it] }
            when {
                target == null -> Unit
                target < g.cursor -> g.a()
                target > g.cursor -> g.c()
                else -> g.b()
            }
            g.step(); steps++
        }
        assertEquals(30 * 8, steps)
        assertTrue(g.pecks < g.maxPecks)
        assertTrue("shooed ${g.score}", g.score >= 15)
        assertTrue(g.points() in 1..10)
    }

    @Test fun `the play menu lists all eight games and starts the right one`() {
        assertEquals(8, MiniGame.NAMES.size)
        assertTrue(MiniGame.start(5, Random(1)) is TwentyOne)
        assertTrue(MiniGame.start(6, Random(1)) is Slots)
        assertTrue(MiniGame.start(7, Random(1)) is ScareCrows)
    }

    // ---------- SCARECROW WATCH ----------

    private val zone = ZoneId.of("America/New_York")
    private val pet = PetState(species = Species.KONA, bornAt = 0, lastTick = 0, hatchedAt = 0, stage = Stage.KID)
    private val watch = Shop.ITEMS.first { it.id == Shop.WATCH }

    @Test fun `the watch costs 500 stars and guards until midnight, once a day`() {
        val noon = java.time.ZonedDateTime.of(2027, 5, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(ShopResult.TOO_POOR, Shop.press(Save(pet = pet, points = 499), watch, noon, zone).second)
        val (s, r) = Shop.press(Save(pet = pet, points = 600), watch, noon, zone)
        assertEquals(ShopResult.BOUGHT, r)
        assertEquals(100, s.points)
        assertTrue(Shop.watching(s, noon + 11 * 3_600_000L))
        assertFalse("gone at midnight", Shop.watching(s, noon + 12 * 3_600_000L))
        assertEquals(ShopResult.ACTIVE, Shop.press(s.copy(points = 900), watch, noon + 3_600_000L, zone).second)
        assertEquals(ShopResult.BOUGHT, Shop.press(s.copy(points = 900), watch, noon + 13 * 3_600_000L, zone).second)
        assertFalse("never owned", Shop.owns(s, Shop.WATCH))
        assertFalse(Achievements.ALL.any { it.key == "own:${Shop.WATCH}" })
    }

    @Test fun `while on guard about 1 in 4 creature tricks are shooed off`() {
        // the squirrel's trick (digging up a seed) needs a seed just planted
        val t0 = 1_800_000_000_000L
        val base = Garden.plant(Save(pet = pet.copy(lastTick = t0, hatchedAt = t0), unlocked = listOf("squirrel"), visitorsAt = t0), 0, "radish", t0)
        var shooed = 0
        var dug = 0
        for (seed in 1..4000) {
            val s = base.copy(scarecrowUntil = t0 + 10 * 3_600_000L)
            val news = Visitors.hour(s, t0 + 3_600_000L, night = false, Random(seed)).second
            if (news.any { "SHOOED" in it }) shooed++
            if (news.any { "DUG UP" in it }) dug++
        }
        val share = shooed.toDouble() / (shooed + dug)
        assertTrue("shooed $shooed of ${shooed + dug}", share in 0.17..0.33)
        // and without the watch nothing is shooed
        val plain = (1..500).count { seed -> Visitors.hour(base, t0 + 3_600_000L, false, Random(seed)).second.any { "SHOOED" in it } }
        assertEquals(0, plain)
    }
}
