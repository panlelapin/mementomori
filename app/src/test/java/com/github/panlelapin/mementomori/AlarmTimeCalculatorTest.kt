package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeCalculatorTest {
    @Test
    fun nextAlarmCrossesActualSpringAndAutumnOffsetChanges() {
        assertNextAlarm(
            ZonedDateTime.parse("2026-03-29T01:00:00+01:00[Europe/Paris]"),
            ZonedDateTime.parse("2026-03-30T01:00:00+02:00[Europe/Paris]"),
        )
        assertNextAlarm(
            ZonedDateTime.parse("2026-10-25T01:00:00+02:00[Europe/Paris]"),
            ZonedDateTime.parse("2026-10-26T01:00:00+01:00[Europe/Paris]"),
        )
    }

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    @Test
    fun schedulesOneAmOnTheSameDayWhenItIsStillUpcoming() {
        assertNextAlarm(
            now = ZonedDateTime.of(2036, 3, 17, 0, 30, 0, 0, paris),
            expected = ZonedDateTime.of(2036, 3, 17, 1, 0, 0, 0, paris),
        )
    }

    @Test
    fun schedulesOneAmOnTheNextDayAtOrAfterOneAm() {
        assertNextAlarm(
            now = ZonedDateTime.of(2036, 3, 17, 1, 0, 0, 0, paris),
            expected = ZonedDateTime.of(2036, 3, 18, 1, 0, 0, 0, paris),
        )
        assertNextAlarm(
            now = ZonedDateTime.of(2036, 3, 17, 18, 0, 0, 0, paris),
            expected = ZonedDateTime.of(2036, 3, 18, 1, 0, 0, 0, paris),
        )
    }

    @Test
    fun remainsAtLocalOneAmAcrossDaylightSavingTime() {
        assertNextAlarm(
            now = ZonedDateTime.of(2036, 3, 29, 12, 0, 0, 0, paris),
            expected = ZonedDateTime.of(2036, 3, 30, 1, 0, 0, 0, paris),
        )
    }

    private fun assertNextAlarm(
        now: ZonedDateTime,
        expected: ZonedDateTime,
    ) {
        assertEquals(expected.toInstant().toEpochMilli(), AlarmTimeCalculator.nextOneAmMillis(now))
    }
}
