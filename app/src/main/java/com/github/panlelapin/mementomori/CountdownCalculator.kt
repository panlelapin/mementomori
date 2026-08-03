package com.github.panlelapin.mementomori

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal val TARGET_DATE: LocalDate = LocalDate.of(2036, 3, 17)

private const val THIN_SPACE = "\u2009"

internal data class Countdown(
    val years: Long,
    val months: Long,
    val weeks: Long,
)

internal fun Countdown.labels(): List<String> =
    listOf(
        "${years}${THIN_SPACE}a",
        "${months}${THIN_SPACE}m",
        "${weeks}${THIN_SPACE}s",
    )

internal object CountdownCalculator {
    fun from(
        today: LocalDate,
        targetDate: LocalDate = TARGET_DATE,
    ): Countdown =
        Countdown(
            years = ChronoUnit.YEARS.between(today, targetDate),
            months = ChronoUnit.MONTHS.between(today, targetDate),
            weeks = ChronoUnit.WEEKS.between(today, targetDate),
        )
}
