package org.jasonagibson.sproutpals.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

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
    fun season(d: LocalDate): Season = when (d.month.number) {
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    /** Month and day as one number (Feb 14 = 214), so date ranges compare simply. */
    private fun md(d: LocalDate) = d.month.number * 100 + d.day

    private fun between(d: LocalDate, from: Int, to: Int) = md(d) in from..to

    fun holiday(d: LocalDate): Holiday? = when {
        between(d, 208, 214) -> Holiday.VALENTINES
        between(d, 325, 420) -> Holiday.EGG_HUNT
        between(d, 701, 704) -> Holiday.FIREWORKS
        between(d, 1020, 1031) -> Holiday.HALLOWEEN
        d.month.number == 12 -> Holiday.WINTER_LIGHTS
        else -> null
    }

    /** Stars for finding the hidden egg, once a year. */
    const val EGG_STARS = 10
}
