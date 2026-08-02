package com.github.panlelapin.mementomori

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal val TARGET_DATE: LocalDate = LocalDate.of(2036, 3, 17)

internal data class Countdown(
    val years: Long,
    val months: Long,
    val weeks: Long,
)

internal fun Countdown.labels(): List<String> = listOf("${years}a", "${months}m", "${weeks}s")

internal object CountdownCalculator {
    fun from(today: LocalDate): Countdown =
        Countdown(
            years = ChronoUnit.YEARS.between(today, TARGET_DATE),
            months = ChronoUnit.MONTHS.between(today, TARGET_DATE),
            weeks = ChronoUnit.WEEKS.between(today, TARGET_DATE),
        )
}
