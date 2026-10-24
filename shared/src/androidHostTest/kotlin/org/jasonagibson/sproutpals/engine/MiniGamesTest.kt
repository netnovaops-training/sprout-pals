package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MiniGamesTest {

    @Test fun `fruit catch ends after 30 seconds and a perfect player catches plenty`() {
        val g = FruitCatch(Random(1))
        var steps = 0
        while (!g.over && steps < 10_000) {
            // perfect play: stand under the lowest fruit, dodge rocks
            val target = g.drops.filter { !it.rock }.maxByOrNull { it.y }
            if (target != null && target.lane < g.lane) g.a() else if (target != null && target.lane > g.lane) g.c()
            g.step(); steps++
        }
        assertTrue(g.over)
        assertEquals(30 * 8, steps)
        assertTrue("caught ${g.score}", g.score >= 10)
        assertTrue(g.points() in 1..10)
    }

    @Test fun `standing still under rocks ends fruit catch early`() {
        val g = FruitCatch(Random(2))
        var steps = 0
        while (!g.over && steps < 10_000) {
            val rock = g.drops.filter { it.rock }.maxByOrNull { it.y }
            if (rock != null && rock.lane < g.lane) g.a() else if (rock != null && rock.lane > g.lane) g.c()
            g.step(); steps++
        }
        assertEquals(0, g.lives)
        assertTrue(steps < 30 * 8)
    }

    @Test fun `jump rope - jumping in time keeps going, missing a jump ends it`() {
        val good = JumpRope()
        repeat(400) { if (!good.over) { if (good.phase == good.period - 4) good.b(); good.step() } }
        assertFalse("well-timed jumps should last", good.over)
        assertTrue(good.score > 15)

        val lazy = JumpRope()
        repeat(100) { if (!lazy.over) lazy.step() }
        assertTrue(lazy.over)
        assertEquals(0, lazy.score)
    }

    @Test fun `tic tac toe - three in a row wins, and the pal takes its turn`() {
        val g = TicTacToe(Random(3))
        g.b()                                   // X in the centre (cursor starts there)
        repeat(6) { g.step() }                  // the pal thinks, then moves
        assertEquals(2, g.board.count { it != ' ' })
        assertEquals('X', TicTacToe.winnerOf("XXX      ".toCharArray()))
        assertEquals('O', TicTacToe.winnerOf("O   O   O".toCharArray()))
        assertEquals(' ', TicTacToe.winnerOf("XOXOXOOXO".toCharArray()))
    }

    @Test fun `tic tac toe always finishes`() {
        repeat(50) { seed ->
            val g = TicTacToe(Random(seed))
            var guard = 0
            while (!g.over && guard < 500) { g.a(); g.b(); repeat(6) { g.step() }; guard++ }
            assertTrue(g.over)
            assertTrue(g.points() in 1..3)
        }
    }

    @Test fun `higher or lower plays five rounds`() {
        val g = HigherLower(Random(4))
        var guard = 0
        while (!g.over && guard < 1000) { if (g.current <= 5) g.a() else g.c(); repeat(10) { g.step() }; guard++ }
        assertTrue(g.over)
        assertEquals(5, g.round)
        assertTrue(g.score in 0..5)
    }
}
