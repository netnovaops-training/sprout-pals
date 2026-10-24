package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class WeatherAndAlertsTest {
    private val zone = ZoneId.of("America/New_York")
    private val hour = 3_600_000L
    private val day0 = LocalDate.of(2026, 10, 24)
    private fun at(h: Int, day: Long = 0) = day0.plusDays(day).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val pet = PetState(species = Species.KONA, bornAt = 0, lastTick = 0, hatchedAt = at(0), stage = Stage.KID)

    @Test fun `most days but not every day get one 6-hour event in the daytime`() {
        val rng = Random(5)
        var events = 0
        for (d in 0L until 200) {
            val s = Weather.plan(Save(pet = pet), at(1, d), zone, rng)
            val w = s.weather ?: continue
            events++
            val startHour = java.time.Instant.ofEpochMilli(w.start).atZone(zone).hour
            assertTrue(startHour in 8..15)
            assertEquals(6 * hour, w.end - w.start)
        }
        assertTrue("got $events", events in 95..145)
    }

    @Test fun `a day is only planned once`() {
        val s = Weather.plan(Save(pet = pet), at(1), zone, Random(1))
        assertEquals(s, Weather.plan(s, at(9), zone, Random(2)))
    }

    @Test fun `rain waters every planted plot until it ends`() {
        var s = Garden.plant(Save(pet = pet), 0, "pumpkin", at(9))
        s = s.copy(weather = WeatherEvent(WeatherKind.RAIN, at(10), at(13)))   // a short shower
        val (rained, news) = Weather.begin(s, at(10), Random(1))
        assertTrue(news!!.contains("RAIN"))
        assertEquals(PlotState.GROWING, Garden.state(Garden.plots(rained)[0], at(12)))
        assertEquals(PlotState.DRY, Garden.state(Garden.plots(rained)[0], at(14)))
        assertNull("begins once", Weather.begin(rained, at(11), Random(1)).second)
    }

    @Test fun `a heat wave halves watering and a rainbow doubles harvests`() {
        val s = Save(pet = pet, weather = WeatherEvent(WeatherKind.HEAT, at(10), at(16)))
        assertEquals(Garden.WET_MINUTES / 2, Garden.wetMinutes(s, at(11)))
        assertEquals(Garden.WET_MINUTES, Garden.wetMinutes(s, at(17)))
        val r = Save(pet = pet, weather = WeatherEvent(WeatherKind.RAINBOW, at(10), at(16)))
        val radish = Garden.crop("radish")!!
        assertEquals(radish.points * 2, Garden.harvestPoints(r, radish, at(12)))
    }

    @Test fun `snow only falls in winter, and frozen soil can't be watered`() {
        val rng = Random(9)
        val summer = LocalDate.of(2027, 7, 1)
        val july = (0L until 60).mapNotNull {
            Weather.plan(Save(pet = pet), summer.plusDays(it).atTime(1, 0).atZone(zone).toInstant().toEpochMilli(), zone, rng).weather?.kind
        }
        assertFalse(WeatherKind.SNOW in july)
        val jan = LocalDate.of(2027, 1, 1)
        val winter = (0L until 60).mapNotNull {
            Weather.plan(Save(pet = pet), jan.plusDays(it).atTime(1, 0).atZone(zone).toInstant().toEpochMilli(), zone, rng).weather?.kind
        }
        assertTrue(WeatherKind.SNOW in winter)
        assertFalse(WeatherKind.RAIN in winter)

        var s = Garden.plant(Save(pet = pet), 0, "radish", at(9))
        s = s.copy(weather = WeatherEvent(WeatherKind.SNOW, at(10), at(16)))
        assertEquals(s, Garden.water(s, 0, at(11)))
    }

    @Test fun `alerts fire once and again only after clearing`() {
        val sick = Save(pet = pet.copy(sick = true))
        val (first, keys) = Alerts.fresh(sick, at(12), false)
        assertTrue(first.any { it.key == "sick" })
        val (second, _) = Alerts.fresh(sick.copy(notified = keys), at(12), false)
        assertFalse(second.any { it.key == "sick" })
        val (_, clearedKeys) = Alerts.fresh(sick.copy(pet = pet, notified = keys), at(12), false)
        val (again, _) = Alerts.fresh(sick.copy(notified = clearedKeys), at(12), false)
        assertTrue(again.any { it.key == "sick" })
    }

    @Test fun `birthday eve and ripe crops are announced`() {
        var s = Garden.plant(Save(pet = pet), 0, "radish", at(9))
        s = Garden.water(s, 0, at(9))
        val alerts = Alerts.current(s, at(11), birthdayEve = true)
        assertTrue(alerts.any { it.key.startsWith("birthday") })
        assertTrue(alerts.any { it.key == "ripe" })
    }
}
