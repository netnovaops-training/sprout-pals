package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlantHarvestTest {
    @Test fun `picking at gold scores 2, ripe 1, early nothing, and the plant is re-sown`() {
        val g = PlantHarvest(Random(1))
        g.growth[2] = 80; g.b()
        assertEquals(2, g.score); assertEquals("PERFECT!", g.callout); assertEquals(0, g.growth[2])
        g.growth[2] = 72; g.b()
        assertEquals(3, g.score)
        g.growth[2] = 20; g.b()
        assertEquals(3, g.score); assertEquals("TOO EARLY", g.callout)
    }

    @Test fun `a smart picker earns stars and the round ends after 30 seconds`() {
        val g = PlantHarvest(Random(2))
        while (!g.over) {
            // walk to the ripest plant and pick it in the gold window
            val best = (0 until g.plots).maxBy { if (g.growth[it] <= 86) g.growth[it] else -1 }
            while (g.cursor < best) g.c()
            while (g.cursor > best) g.a()
            if (g.growth[best] in 78..86) g.b()
            g.step()
        }
        assertTrue("scored ${g.score}", g.score >= 10)
        assertTrue(g.points() in 1..10)
    }
}
