package com.github.panlelapin.mementomori

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetSettingsTest {
    @Test
    fun targetDateMustBeStrictlyInTheFuture() {
        val today = LocalDate.of(2030, 1, 1)

        assertFalse(TargetDatePolicy.isValid(today = today, targetDate = today.minusDays(1)))
        assertFalse(TargetDatePolicy.isValid(today = today, targetDate = today))
        assertTrue(TargetDatePolicy.isValid(today = today, targetDate = today.plusDays(1)))
    }

    @Test
    fun defaultTargetUsesTheProjectDateWhileItIsFuture() {
        assertEquals(
            TARGET_DATE,
            TargetDatePolicy.defaultFor(LocalDate.of(2030, 1, 1)),
        )
    }

    @Test
    fun defaultTargetFallsBackToTomorrowAfterTheProjectDate() {
        val today = TARGET_DATE.plusDays(1)

        assertEquals(today.plusDays(1), TargetDatePolicy.defaultFor(today))
    }

    @Test
    fun fontColorFollowsTheCurrentSystemMode() {
        assertEquals(
            Color.BLACK,
            WidgetColorSelector.select(
                darkMode = false,
                lightColor = Color.BLACK,
                darkColor = Color.WHITE,
            ),
        )
        assertEquals(
            Color.WHITE,
            WidgetColorSelector.select(
                darkMode = true,
                lightColor = Color.BLACK,
                darkColor = Color.WHITE,
            ),
        )
    }
}
