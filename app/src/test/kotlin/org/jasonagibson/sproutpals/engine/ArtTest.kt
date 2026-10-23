package org.jasonagibson.sproutpals.engine

import org.jasonagibson.sproutpals.art.Icons
import org.jasonagibson.sproutpals.art.Sprites
import org.jasonagibson.sproutpals.ui.CAST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catches a mistyped row in the text-drawn pixel art before it looks lopsided on screen. */
class ArtTest {
    @Test fun `every body drawing is 32 wide and fits the screen`() {
        for (sp in Species.entries) for (st in Stage.entries) {
            val g = Sprites.body(sp, st)
            g.forEachIndexed { i, row -> assertEquals("$sp $st row $i", 32, row.length) }
            assertTrue("$sp $st is ${g.size} tall", g.size <= 56)
        }
    }

    @Test fun `every icon is 16 by 16`() {
        Icons.ALL.forEachIndexed { n, icon ->
            assertEquals("icon $n rows", 16, icon.size)
            icon.forEachIndexed { i, row -> assertEquals("icon $n row $i", 16, row.length) }
        }
    }

    @Test fun `every pet has colors for its body`() {
        for (sp in Species.entries) for (st in Stage.entries.drop(1)) {
            val pal = Sprites.palette(sp, st)
            assertTrue("$sp has no body color", pal.containsKey('B'))
        }
    }

    @Test fun `title scene has everyone exactly once, each in their own spot`() {
        assertEquals(Species.entries.toSet(), CAST.map { it.species }.toSet())
        assertEquals(CAST.size, CAST.map { it.species }.distinct().size)
        assertEquals(CAST.size, CAST.map { it.x to it.ground }.distinct().size)
    }

    @Test fun `no two characters share a look`() {
        val looks = Species.entries.map { Sprites.body(it, Stage.ADULT) to Sprites.palette(it, Stage.ADULT) }
        assertEquals(looks.size, looks.distinct().size)
    }
}
