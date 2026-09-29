package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownCalculatorTest {
    @Test
    fun leapDaysAndMonthEndsCountOnlyCompletedIntervals() {
        assertEquals(
            Countdown(0, 0, 4),
            CountdownCalculator.from(LocalDate.of(2031, 1, 31), LocalDate.of(2031, 2, 28)),
        )
        assertEquals(
            Countdown(0, 11, 52),
            CountdownCalculator.from(LocalDate.of(2032, 2, 29), LocalDate.of(2033, 2, 28)),
        )
        assertEquals(
            Countdown(1, 12, 52),
            CountdownCalculator.from(LocalDate.of(2032, 2, 29), LocalDate.of(2033, 3, 1)),
        )
    }

    @Test
    fun labelsUseThinSpacingBeforeSuffix() {
        assertEquals(
            listOf("10\u2009Y", "120\u2009M", "521\u2009W"),
            Countdown(years = 10, months = 120, weeks = 521).labels(),
        )
    }

    @Test
    fun targetDateHasNoCompleteIntervalsRemaining() {
        assertEquals(Countdown(0, 0, 0), CountdownCalculator.from(TARGET_DATE))
    }

    @Test
    fun countsOnlyCompleteIntervals() {
        assertEquals(
            Countdown(years = 0, months = 0, weeks = 1),
            CountdownCalculator.from(TARGET_DATE.minusWeeks(1)),
        )
        assertEquals(
            Countdown(years = 0, months = 1, weeks = 4),
            CountdownCalculator.from(TARGET_DATE.minusMonths(1)),
        )
        assertEquals(
            Countdown(years = 1, months = 12, weeks = 52),
            CountdownCalculator.from(TARGET_DATE.minusYears(1)),
        )
    }

    @Test
    fun usesTheConfiguredTargetDate() {
        assertEquals(
            Countdown(years = 2, months = 24, weeks = 104),
            CountdownCalculator.from(
                today = LocalDate.of(2030, 1, 1),
                targetDate = LocalDate.of(2032, 1, 1),
            ),
        )
    }
}
