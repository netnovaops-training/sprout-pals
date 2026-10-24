package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkAndBirthdayTest {
    private val m = Rules.MINUTE

    /** Steps every 5 seconds from [from] to [to]. */
    private fun walk(w0: Walk, from: Long, to: Long): Walk {
        var w = w0; var t = from
        while (t <= to) { w = Walking.onStep(w, t); t += 5_000 }
        return w
    }

    @Test fun `walk mode starts after two minutes of walking`() {
        val w = walk(Walk(), 0, 90_000)
        assertTrue(Walking.movingNow(w, 90_000))
        assertFalse("not yet at 1.5 min", Walking.showWalk(w, 90_000))
        val longer = walk(w, 95_000, 2 * m + 5_000)
        assertTrue(Walking.showWalk(longer, 2 * m + 5_000))
    }

    @Test fun `walk mode ends two minutes after the last step`() {
        val w = walk(Walk(), 0, 5 * m)
        assertTrue(Walking.showWalk(w, 5 * m + m))
        assertFalse(Walking.showWalk(w, 5 * m + 2 * m))
    }

    @Test fun `a short pause doesn't restart the walk`() {
        var w = walk(Walk(), 0, 2 * m)
        w = walk(w, 3 * m, 3 * m + 10_000)        // 1 minute stop at a crossing
        assertTrue(Walking.showWalk(w, 3 * m + 10_000))
    }

    @Test fun `party hat only on the day before each birthday`() {
        assertFalse(Rules.birthdaySoon(Rules.KID_AT - Rules.DAY - 1))
        assertTrue(Rules.birthdaySoon(Rules.KID_AT - Rules.DAY))
        assertTrue(Rules.birthdaySoon(Rules.KID_AT - 1))
        assertFalse("hat comes off once grown", Rules.birthdaySoon(Rules.KID_AT))
        assertTrue(Rules.birthdaySoon(Rules.ADULT_AT - Rules.HOUR))
        assertTrue(Rules.birthdaySoon(Rules.OLD_AT - Rules.HOUR))
        assertFalse(Rules.birthdaySoon(Rules.OLD_AT + Rules.DAY))
    }
}
