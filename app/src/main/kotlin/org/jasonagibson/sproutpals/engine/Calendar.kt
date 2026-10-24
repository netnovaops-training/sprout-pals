package org.jasonagibson.sproutpals.engine

import java.time.LocalDate
import java.time.MonthDay

/** The garden follows the real calendar: the season colours the scenery, holidays add a little extra. */
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

enum class Holiday(val title: String) {
    VALENTINES("VALENTINE'S WEEK"),
    EGG_HUNT("SPRING EGG HUNT"),
    FIREWORKS("FIREWORKS NIGHTS"),
    HALLOWEEN("HALLOWEEN"),
    WINTER_LIGHTS("WINTER LIGHTS"),
}

object Calendar {
    /** Northern-hemisphere seasons by month (Mar-May spring, Jun-Aug summer, Sep-Nov autumn). */
    fun season(d: LocalDate): Season = when (d.monthValue) {
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    private fun between(d: LocalDate, from: MonthDay, to: MonthDay) = MonthDay.from(d).let { it >= from && it <= to }

    fun holiday(d: LocalDate): Holiday? = when {
        between(d, MonthDay.of(2, 8), MonthDay.of(2, 14)) -> Holiday.VALENTINES
        between(d, MonthDay.of(3, 25), MonthDay.of(4, 20)) -> Holiday.EGG_HUNT
        between(d, MonthDay.of(7, 1), MonthDay.of(7, 4)) -> Holiday.FIREWORKS
        between(d, MonthDay.of(10, 20), MonthDay.of(10, 31)) -> Holiday.HALLOWEEN
        d.monthValue == 12 -> Holiday.WINTER_LIGHTS
        else -> null
    }

    /** Stars for finding the hidden egg, once a year. */
    const val EGG_STARS = 10
}
