package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

class VisitorsTest {
    private val zone = ZoneId.of("America/New_York")
    private val hour = 60 * 60_000L
    private val t0 = 1_800_000_000_000L
    private val pet = PetState(species = Species.JUN, bornAt = 0, lastTick = t0, hatchedAt = t0, stage = Stage.KID)
    private fun with(vararg ids: String) = Save(pet = pet, unlocked = ids.toList(), visitorsAt = t0)

    @Test fun `no creatures, nothing happens`() {
        val s = Save(pet = pet, visitorsAt = t0)
        val (after, news) = Visitors.catchUp(s, t0 + 10 * hour, zone, Random(1))
        assertEquals(s.copy(visitorsAt = t0 + 10 * hour), after)
        assertTrue(news.isEmpty())
    }

    @Test fun `each creature out adds life, the deer the most`() {
        val (a, _) = Visitors.catchUp(with("ladybug", "deer"), t0 + 10 * hour, zone, Random(1))
        assertEquals(10 * (Visitors.LIFE_MINUTES + Visitors.DEER_LIFE), a.pet.bonusLife)
        // switched off: no bonus
        val off = with("ladybug").copy(hidden = listOf("ladybug"))
        assertEquals(0, Visitors.catchUp(off, t0 + 10 * hour, zone, Random(1)).first.pet.bonusLife)
    }

    @Test fun `over a long stretch bees grow flowers and squirrels bring points`() {
        val (a, _) = Visitors.catchUp(with("bee", "squirrel"), t0 + 48 * hour, zone, Random(3))
        assertTrue(a.flowers > 0)
        assertTrue(a.points > 0)
    }

    @Test fun `bonus life keeps a pal going past two weeks`() {
        val old = pet.copy(hatchedAt = 0, lastTick = 14 * Rules.DAY - Rules.MINUTE, stage = Stage.OLD, bonusLife = 120)
        val after = PetEngine.advance(old, 14 * Rules.DAY + 60 * Rules.MINUTE, zone, Random(1))
        assertEquals(null, after.ended)
    }

    @Test fun `the ladybug keeps soil wet longer and bees add harvest points`() {
        assertEquals(Garden.WET_MINUTES + 60, Garden.wetMinutes(with("ladybug")))
        val radish = Garden.crop("radish")!!
        assertEquals(radish.points + 2, Garden.harvestPoints(with("bee").copy(flowers = 4), radish))
    }
}
