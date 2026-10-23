package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import kotlin.random.Random

/** Fast-forwards whole lives: two weeks of game time run in well under a second. */
class PetEngineTest {

    private val zone = ZoneOffset.UTC
    // 2026-01-01 08:00 UTC: awake time, so the first hours are predictable.
    private val start = 1_767_225_600_000L + 8 * Rules.HOUR

    private fun egg(species: Species = Species.JUN) = PetState(species = species, bornAt = start, lastTick = start)

    private fun shakeOpen(e: PetState, at: Long): PetState {
        var s = e
        repeat(Rules.SHAKES_TO_HATCH) { s = PetEngine.shake(s, at) }
        return s
    }

    /** Egg appears at 08:00 and is shaken open at 09:00. */
    private fun pet(species: Species = Species.JUN) = shakeOpen(egg(species), start + Rules.HOUR)

    /** A good owner checks in every 10 minutes and does whatever is needed. */
    private fun liveWithCare(rng: Random): PetState {
        var s = pet()
        var t = start + Rules.HOUR
        while (s.ended == null && t < start + 20 * Rules.DAY) {
            t += 10 * Rules.MINUTE
            s = PetEngine.advance(s, t, zone, rng)
            if (s.sleeping) {
                if (s.lightsOn) s = PetEngine.toggleLights(s)
                continue
            }
            while (PetEngine.feedMeal(s).second == Outcome.OK) s = PetEngine.feedMeal(s).first
            while (PetEngine.play(s).second == Outcome.OK) s = PetEngine.play(s).first
            s = PetEngine.clean(s).first
            while (s.sick) s = PetEngine.medicine(s).first
        }
        return s
    }

    @Test fun `a well cared for pet lives the full two weeks`() {
        val s = liveWithCare(Random(1))
        assertEquals(EndReason.NATURAL, s.ended)
        assertEquals(start + Rules.HOUR + Rules.LIFE_END, s.endedAt)
        assertEquals(Stage.OLD, s.stage)
        assertEquals(0, s.totalMistakes)
    }

    @Test fun `stages change at the planned times`() {
        // Later stages: a neglected pet wouldn't get that far, so check the schedule itself.
        assertEquals(Stage.BABY, Rules.stageAt(Rules.KID_AT - 1))
        assertEquals(Stage.KID, Rules.stageAt(Rules.KID_AT))
        assertEquals(Stage.ADULT, Rules.stageAt(Rules.ADULT_AT))
        assertEquals(Stage.OLD, Rules.stageAt(Rules.OLD_AT))
        assertEquals(14 * Rules.DAY, Rules.LIFE_END)
    }

    @Test fun `a neglected pet returns to the garden early`() {
        val s = PetEngine.advance(pet(), start + 20 * Rules.DAY, zone, Random(3))
        assertNotEquals(null, s.ended)
        assertNotEquals(EndReason.NATURAL, s.ended)
        assertTrue("ended at ${(s.endedAt!! - start) / Rules.HOUR}h", s.endedAt!! < start + 3 * Rules.DAY)
    }

    @Test fun `nothing counts down while asleep`() {
        val rng = Random(4)
        // Born 08:00, hatches 09:00; at 22:00 it falls asleep.
        val atBedtime = PetEngine.feedMeal(PetEngine.advance(pet(), start + 14 * Rules.HOUR, zone, rng)).first
        val lightsOff = PetEngine.toggleLights(atBedtime)
        val beforeWake = PetEngine.advance(lightsOff, start + 22 * Rules.HOUR + 59 * Rules.MINUTE, zone, rng)
        assertTrue(atBedtime.sleeping)
        assertEquals(atBedtime.hunger, beforeWake.hunger)
        assertEquals(atBedtime.happy, beforeWake.happy)
        assertEquals(atBedtime.poops, beforeWake.poops)
        assertEquals(atBedtime.totalMistakes, beforeWake.totalMistakes) // no new mistakes overnight
    }

    @Test fun `lights left on at night is one mistake`() {
        val bedtime = PetEngine.advance(pet(), start + 14 * Rules.HOUR, zone, Random(5))
        val off = PetEngine.advance(PetEngine.toggleLights(bedtime), start + 16 * Rules.HOUR, zone, Random(5))
        val on = PetEngine.advance(bedtime, start + 16 * Rules.HOUR, zone, Random(5))
        assertTrue(on.sleeping)
        assertEquals(off.totalMistakes + 1, on.totalMistakes)
    }

    @Test fun `two doses of medicine cure`() {
        val sick = PetEngine.advance(pet(), start + 2 * Rules.HOUR, zone, Random(6)).copy(sick = true)
        val (one, o1) = PetEngine.medicine(sick)
        assertEquals(Outcome.OK, o1)
        assertTrue(one.sick)
        val (two, _) = PetEngine.medicine(one)
        assertFalse(two.sick)
    }

    @Test fun `a full pet refuses another meal`() {
        val s = PetEngine.advance(pet(), start + 2 * Rules.HOUR, zone, Random(7)).copy(hunger = 4)
        assertEquals(Outcome.NOT_NEEDED, PetEngine.feedMeal(s).second)
    }

    @Test fun `secret Jason hatches about 1 in 100`() {
        val rng = Random(8)
        val n = 100_000
        val jasons = (1..n).count { PetEngine.pickSpecies(rng) == Species.JASON }
        assertTrue("got $jasons", jasons in 850..1150)
        val kona = (1..n).count { PetEngine.pickSpecies(rng) == Species.KONA }
        assertTrue("got $kona", kona in 15_500..17_500)
    }

    @Test fun `steps turn into points and survive a reboot`() {
        var save = Save(pet = pet())
        save = Steps.apply(save, 5_000)          // first reading only sets the baseline
        assertEquals(0, save.points)
        save = Steps.apply(save, 5_250)          // +250 steps -> 2 points, 50 left over
        assertEquals(2, save.points)
        assertEquals(50, save.stepRemainder)
        save = Steps.apply(save, 60)             // phone rebooted: 60 new steps, 110 pooled
        assertEquals(3, save.points)
        assertEquals(310L, save.stepsTotal)
    }

    @Test fun `poop comes every 4 awake hours plus a surprise or two a day`() {
        // A well-fed pet, cleaned every 10 minutes for 10 days; count the poops that appear.
        // Awake 07:00-22:00 = 15 h: 3 scheduled + about 1.5 surprise = about 4.5 a day.
        val rng = Random(10)
        var s = pet()
        var t = start + Rules.HOUR
        var total = 0
        val days = 10
        repeat(days * 24 * 6) {
            t += 10 * Rules.MINUTE
            s = PetEngine.advance(s, t, zone, rng)
            total += s.poops
            s = s.copy(poops = 0, poopAge = 0, hunger = 4, happy = 4, sick = false, sickMinutes = 0)
        }
        val perDay = total.toDouble() / days
        assertTrue("got $perDay a day", perDay in 3.4..6.0)
    }

    @Test fun `uncleaned poop gets smelly after half an hour`() {
        val s = pet().copy(poops = 1, poopAge = Rules.SMELLY_AFTER - 1)
        assertFalse(PetEngine.smelly(s))
        assertTrue(PetEngine.smelly(s.copy(poopAge = Rules.SMELLY_AFTER)))
    }

    @Test fun `rare odds follow the given percent`() {
        val rng = Random(11)
        val n = 100_000
        val jasons = (1..n).count { PetEngine.pickSpecies(rng, rarePercent = 16) == Species.JASON }
        assertTrue("got $jasons", jasons in 15_000..17_000)
    }

    @Test fun `an egg waits for a shake and never ages on its own`() {
        val waited = PetEngine.advance(egg(), start + 3 * Rules.DAY, zone, Random(9))
        assertEquals(Stage.EGG, waited.stage)
        assertEquals(null, waited.ended)
    }

    @Test fun `three shakes open the egg and start the two week clock`() {
        val at = start + 5 * Rules.HOUR
        var s = egg()
        s = PetEngine.shake(s, at); s = PetEngine.shake(s, at)
        assertEquals(Stage.EGG, s.stage)
        assertEquals(2, s.cracks)
        s = PetEngine.shake(s, at)
        assertEquals(Stage.BABY, s.stage)
        assertEquals(at, s.hatchedAt)
        assertEquals(Stage.BABY, PetEngine.shake(s, at + 1).stage) // shaking a baby does nothing
    }
}
