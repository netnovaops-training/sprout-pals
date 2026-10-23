package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private val pet = PetState(species = Species.JUN, bornAt = 0, lastTick = 0)

    @Test fun `bronze at 1, silver at 5, gold at 10, each once, each paying stars`() {
        var s = Save(pet = pet)
        val all = mutableListOf<Unlock>()
        repeat(12) { val (n, u) = Achievements.record(s, "harvest"); s = n; all += u }
        assertEquals(listOf(0, 1, 2), all.map { it.tier })
        assertEquals(12, s.counts["harvest"])
        assertEquals(2 + 5 + 10, s.points)
    }

    @Test fun `every ownable shop item has an achievement, the secret one does not`() {
        for (item in Shop.ITEMS) {
            val has = Achievements.feat("own:${item.id}") != null
            assertEquals(item.name, !item.repeatable, has)
        }
        assertFalse(Achievements.ALL.any { Rules.MYSTERY_ITEM in it.key })
    }

    @Test fun `unknown keys are ignored and progress adds up`() {
        val s = Save(pet = pet)
        assertEquals(s, Achievements.record(s, "nonsense").first)
        val (after, _) = Achievements.record(s, "own:ladybug")
        val (got, of) = Achievements.progress(after)
        assertEquals(1, got)
        assertTrue(of > 60)
    }
}
