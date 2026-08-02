package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownCalculatorTest {
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
}
