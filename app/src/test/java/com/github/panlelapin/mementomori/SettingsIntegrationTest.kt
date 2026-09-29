package com.github.panlelapin.mementomori

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SettingsIntegrationTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetPreferences() {
        context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).edit(commit = true) {
            clear()
        }
    }

    @Test
    fun defaultsPersistenceAndExpiredOrCorruptDatesFollowPolicy() {
        val today = LocalDate.of(2030, 1, 1)
        assertEquals(
            WidgetSettings(TARGET_DATE, Color.GRAY, Color.WHITE),
            WidgetSettingsStore.load(context, today),
        )
        assertFalse(WidgetSettingsStore.saveTargetDate(context, today, today))
        assertTrue(WidgetSettingsStore.saveTargetDate(context, today.plusDays(2), today))
        WidgetSettingsStore.saveLightFontColor(context, Color.RED)
        WidgetSettingsStore.saveDarkFontColor(context, Color.BLUE)
        val reopened =
            context.createConfigurationContext(
                Configuration(context.resources.configuration),
            )
        assertEquals(
            WidgetSettings(today.plusDays(2), Color.RED, Color.BLUE),
            WidgetSettingsStore.load(reopened, today),
        )
        assertEquals(TARGET_DATE, WidgetSettingsStore.load(reopened, today.plusDays(2)).targetDate)
        context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).edit(commit = true) {
            putString("target_date", "invalid")
        }
        assertEquals(TARGET_DATE, WidgetSettingsStore.load(context, today).targetDate)
    }

    @Test
    fun appearanceSelectsSavedColorsAndAllWritesAreOpaque() {
        WidgetSettingsStore.saveLightFontColor(context, 0x0000FF00)
        WidgetSettingsStore.saveDarkFontColor(context, 0x000000FF)
        val settings = WidgetSettingsStore.load(context)
        for ((mode, expected) in listOf(
            Configuration.UI_MODE_NIGHT_NO to Color.GREEN,
            Configuration.UI_MODE_NIGHT_YES to Color.BLUE,
        )) {
            val configured =
                context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply {
                        uiMode =
                            mode
                    },
                )
            assertEquals(expected, WidgetSettingsStore.currentFontColor(configured, settings))
        }
    }
}
