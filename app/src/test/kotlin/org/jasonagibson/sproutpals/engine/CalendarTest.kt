package org.jasonagibson.sproutpals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarTest {
    @Test fun `seasons follow the months`() {
        assertEquals(Season.SPRING, Calendar.season(LocalDate.of(2027, 4, 2)))
        assertEquals(Season.SUMMER, Calendar.season(LocalDate.of(2027, 7, 15)))
        assertEquals(Season.AUTUMN, Calendar.season(LocalDate.of(2026, 10, 24)))
        assertEquals(Season.WINTER, Calendar.season(LocalDate.of(2027, 1, 9)))
    }

    @Test fun `holidays fall on their dates and most days have none`() {
        assertEquals(Holiday.HALLOWEEN, Calendar.holiday(LocalDate.of(2026, 10, 24)))
        assertEquals(Holiday.WINTER_LIGHTS, Calendar.holiday(LocalDate.of(2026, 12, 20)))
        assertEquals(Holiday.VALENTINES, Calendar.holiday(LocalDate.of(2027, 2, 14)))
        assertEquals(Holiday.EGG_HUNT, Calendar.holiday(LocalDate.of(2027, 4, 1)))
        assertEquals(Holiday.FIREWORKS, Calendar.holiday(LocalDate.of(2027, 7, 4)))
        assertNull(Calendar.holiday(LocalDate.of(2027, 6, 10)))
    }
}
