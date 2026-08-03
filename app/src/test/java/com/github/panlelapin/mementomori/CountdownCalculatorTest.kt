package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownCalculatorTest {
    @Test
    fun labelsUseThinSpacingBeforeSuffix() {
        assertEquals(
            listOf("10\u2009a", "120\u2009m", "521\u2009s"),
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
            CountdownCalculator.from(LocalDate.of(2036, 3, 10)),
        )
        assertEquals(
            Countdown(years = 0, months = 1, weeks = 4),
            CountdownCalculator.from(LocalDate.of(2036, 2, 17)),
        )
        assertEquals(
            Countdown(years = 1, months = 12, weeks = 52),
            CountdownCalculator.from(LocalDate.of(2035, 3, 17)),
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
