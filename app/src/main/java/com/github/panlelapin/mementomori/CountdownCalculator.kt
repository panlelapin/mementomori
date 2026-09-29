package com.github.panlelapin.mementomori

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal val TARGET_DATE: LocalDate = LocalDate.of(2040, 1, 1)

internal const val THIN_SPACE = "\u2009"

internal data class Countdown(
    val years: Long,
    val months: Long,
    val weeks: Long,
)

internal fun Countdown.labels(): List<String> =
    listOf(
        "${years}${THIN_SPACE}Y",
        "${months}${THIN_SPACE}M",
        "${weeks}${THIN_SPACE}W",
    )

/** Independent whole calendar intervals, never the successive components of a duration. */
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
