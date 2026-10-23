package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    private val egg = PetState(species = Species.JUN, bornAt = 0, lastTick = 0)

    @Test fun `every stop has its own prize`() {
        val ids = Route.STOPS.map { it.prizeId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Route.STOPS.size >= 20)
    }

    @Test fun `first shop is 50 miles of walking`() {
        assertEquals(0, Route.reached(99_999))
        assertEquals(1, Route.reached(100_000))
        assertEquals(50.0, Route.milesToNext(0)!!, 0.001)
        assertEquals("Sokolowski Greenhouses", Route.next(0)!!.name)
    }

    @Test fun `a prize is collected once`() {
        val save = Save(pet = egg, stepsTotal = 100_500)
        val (after, fresh) = Route.collect(save)
        assertEquals(listOf("seedling_hat"), fresh.map { it.prizeId })
        assertEquals(1, after.stopsReached)
        assertEquals(emptyList<Stop>(), Route.collect(after).second)
    }

    @Test fun `catching up after a long time away hands out every missed prize`() {
        val (after, fresh) = Route.collect(Save(pet = egg, stepsTotal = 3 * Route.STEPS_PER_STOP + 5))
        assertEquals(3, fresh.size)
        assertEquals(listOf("seedling_hat", "sunflower_pin", "orange_apron"), after.unlocked)
    }

    @Test fun `the route ends after the last shop`() {
        val all = Route.STOPS.size * Route.STEPS_PER_STOP
        assertEquals(Route.STOPS.size, Route.reached(all * 2))
        assertEquals(null, Route.next(all))
    }
}
