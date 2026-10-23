package org.jasonagibson.sproutpals.engine

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Weather: on most days (not all) one 6-hour event rolls, good or bad, starting between
 * 8am and 3pm. Its effects are checked by the garden and the creatures while it's on.
 */
@Serializable
enum class WeatherKind(val good: Boolean, val icon: String, val title: String, val line: String) {
    RAIN(true, "☔", "RAIN SHOWER", "Every plot is watered for free"),
    RAINBOW(true, "🌈", "RAINBOW", "Harvests pay double ★"),
    SUN(true, "☀", "WARM SUN", "+1 ♥ and +2 ★ on every harvest"),
    HEAT(false, "🔥", "HEAT WAVE", "Watering only lasts half as long"),
    STORM(false, "⛈", "STORM", "Creatures hide; crops may get flattened"),
    /** Winter only (Nov-Mar), in place of rain: fun for the pal, but the soil freezes. */
    SNOW(true, "❄", "SNOW DAY", "+1 ♥ playing; the soil freezes (no watering)"),
}

@Serializable
data class WeatherEvent(val kind: WeatherKind, val start: Long, val end: Long, val started: Boolean = false)

object Weather {
    const val HOURS = 6
    /** Chance (in 100) that a day has an event at all. */
    const val DAILY_CHANCE = 60
    private const val HOUR = 3_600_000L

    /** The event on right now, if any. */
    fun now(save: Save, at: Long): WeatherKind? = save.weather?.takeIf { at >= it.start && at < it.end }?.kind

    /** Once per day: maybe plan today's event. */
    fun plan(save: Save, at: Long, zone: ZoneId, rng: Random): Save {
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        if (save.weatherDay == day.toEpochDay()) return save
        val s = save.copy(weatherDay = day.toEpochDay())
        if (rng.nextInt(100) >= DAILY_CHANCE) return s
        val winter = day.monthValue >= 11 || day.monthValue <= 3
        val pool = WeatherKind.entries.filter { it != WeatherKind.SNOW }
        val rolled = pool[rng.nextInt(pool.size)]
        val kind = if (winter && rolled == WeatherKind.RAIN) WeatherKind.SNOW else rolled
        val start = day.atTime(8 + rng.nextInt(8), 0).atZone(zone).toInstant().toEpochMilli()
        return s.copy(weather = WeatherEvent(kind, start, start + HOURS * HOUR))
    }

    /** When the event begins: its one-off effects, and the news. */
    fun begin(save: Save, at: Long, rng: Random): Pair<Save, String?> {
        val w = save.weather ?: return save to null
        if (w.started || at < w.start || at >= w.end) return save to null
        var s = save.copy(weather = w.copy(started = true))
        val alive = s.pet.ended == null && s.pet.hatchedAt != null
        when (w.kind) {
            WeatherKind.RAIN -> s = s.copy(plots = Garden.plots(s).map { p ->
                if (p.crop == null) p else p.copy(grown = Garden.grownAt(p, at), updatedAt = at, wetUntil = maxOf(p.wetUntil, w.end))
            })
            WeatherKind.SUN, WeatherKind.SNOW -> if (alive) s = s.copy(pet = s.pet.copy(happy = (s.pet.happy + 1).coerceAtMost(Rules.MAX_HEARTS)))
            WeatherKind.STORM -> {
                if (alive) s = s.copy(pet = s.pet.copy(happy = (s.pet.happy - 1).coerceAtLeast(0)))
                if (rng.nextInt(100) < 30) Garden.setBack(s, at, 60)?.let { s = it.first }
            }
            WeatherKind.RAINBOW, WeatherKind.HEAT -> Unit
        }
        return s to "${w.kind.icon} ${w.kind.title}!\n${w.kind.line.uppercase()}\n(${HOURS} HOURS)"
    }
}
