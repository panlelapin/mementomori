package com.github.panlelapin.mementomori

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.content.edit
import java.time.LocalDate

private const val PREFERENCES_NAME = "widget_settings"
private const val TARGET_DATE_KEY = "target_date"
private const val LIGHT_FONT_COLOR_KEY = "light_font_color"
private const val DARK_FONT_COLOR_KEY = "dark_font_color"

internal data class WidgetSettings(
    val targetDate: LocalDate,
    val lightFontColor: Int,
    val darkFontColor: Int,
)

/** Civil dates must be strictly future; an expired default becomes tomorrow after 2040. */
internal object TargetDatePolicy {
    fun isValid(
        today: LocalDate,
        targetDate: LocalDate,
    ): Boolean = targetDate.isAfter(today)

    fun defaultFor(today: LocalDate): LocalDate =
        TARGET_DATE.takeIf { isValid(today = today, targetDate = it) } ?: today.plusDays(1)
}

/** Pure color selection for the current system appearance. */
internal object WidgetColorSelector {
    fun select(
        darkMode: Boolean,
        lightColor: Int,
        darkColor: Int,
    ): Int = if (darkMode) darkColor else lightColor
}

/**
 * Persists only the date and opaque colors. Invalid/expired dates fall back at read time without
 * rewriting the user's saved value; countdowns always derive from the caller's local [LocalDate].
 */
internal object WidgetSettingsStore {
    fun load(
        context: Context,
        today: LocalDate = LocalDate.now(),
    ): WidgetSettings {
        val preferences = preferences(context)
        val savedTargetDate = preferences.getString(TARGET_DATE_KEY, null)?.toLocalDateOrNull()
        val targetDate =
            savedTargetDate?.takeIf { TargetDatePolicy.isValid(today = today, targetDate = it) }
                ?: TargetDatePolicy.defaultFor(today)
        return WidgetSettings(
            targetDate = targetDate,
            lightFontColor = preferences.getInt(LIGHT_FONT_COLOR_KEY, Color.GRAY),
            darkFontColor = preferences.getInt(DARK_FONT_COLOR_KEY, Color.WHITE),
        )
    }

    fun saveTargetDate(
        context: Context,
        targetDate: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        if (!TargetDatePolicy.isValid(today = today, targetDate = targetDate)) {
            return false
        }
        preferences(context).edit {
            putString(TARGET_DATE_KEY, targetDate.toString())
        }
        return true
    }

    fun saveLightFontColor(
        context: Context,
        color: Int,
    ) {
        preferences(context).edit {
            putInt(LIGHT_FONT_COLOR_KEY, color or Color.BLACK)
        }
    }

    fun saveDarkFontColor(
        context: Context,
        color: Int,
    ) {
        preferences(context).edit {
            putInt(DARK_FONT_COLOR_KEY, color or Color.BLACK)
        }
    }

    fun currentFontColor(
        context: Context,
        settings: WidgetSettings,
    ): Int =
        WidgetColorSelector.select(
            darkMode = context.resources.configuration.isDarkMode(),
            lightColor = settings.lightFontColor,
            darkColor = settings.darkFontColor,
        )

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(this) }.getOrNull()
}

private fun Configuration.isDarkMode(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
