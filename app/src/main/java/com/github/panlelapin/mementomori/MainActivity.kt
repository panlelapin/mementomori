package com.github.panlelapin.mementomori

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val DATE_PICKER_TAG = "target_date_picker"
private const val RGB_MASK = 0x00FFFFFF
private const val DARK_TEXT_LUMINANCE_THRESHOLD = 0.5

/** Material 3 settings screen for the widget target date and day/night font colors. */
class MainActivity : AppCompatActivity() {
    private val targetDateButton: MaterialButton by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.target_date_button)
    }
    private val lightColorButton: MaterialButton by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.light_color_button)
    }
    private val darkColorButton: MaterialButton by lazy(LazyThreadSafetyMode.NONE) {
        findViewById(R.id.dark_color_button)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()
        targetDateButton.setOnClickListener { showTargetDatePicker() }
        lightColorButton.setOnClickListener { showColorPicker(darkMode = false) }
        darkColorButton.setOnClickListener { showColorPicker(darkMode = true) }
        renderSettings()
    }

    private fun applySystemBarInsets() {
        val content = findViewById<View>(R.id.settings_content)
        val initialLeft = content.paddingLeft
        val initialTop = content.paddingTop
        val initialRight = content.paddingRight
        val initialBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(content)
    }

    override fun onResume() {
        super.onResume()
        renderSettings()
    }

    private fun showTargetDatePicker() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val settings = WidgetSettingsStore.load(context = this, today = today)
        val constraints =
            CalendarConstraints
                .Builder()
                .setStart(tomorrow.toUtcMilliseconds())
                .setValidator(DateValidatorPointForward.from(tomorrow.toUtcMilliseconds()))
                .build()
        val picker =
            MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(R.string.target_date)
                .setSelection(settings.targetDate.toUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build()
        picker.addOnPositiveButtonClickListener { selection ->
            saveTargetDate(selection.toLocalDateUtc())
        }
        picker.show(supportFragmentManager, DATE_PICKER_TAG)
    }

    private fun saveTargetDate(targetDate: LocalDate) {
        if (!WidgetSettingsStore.saveTargetDate(context = this, targetDate = targetDate)) {
            val message =
                Snackbar.make(
                    findViewById(R.id.settings_root),
                    R.string.target_date_must_be_future,
                    Snackbar.LENGTH_LONG,
                )
            message.show()
            return
        }
        settingsChanged()
    }

    private fun showColorPicker(darkMode: Boolean) {
        val settings = WidgetSettingsStore.load(this)
        val initialColor =
            WidgetColorSelector.select(
                darkMode = darkMode,
                lightColor = settings.lightFontColor,
                darkColor = settings.darkFontColor,
            )
        val title = if (darkMode) R.string.dark_mode else R.string.light_mode
        MaterialColorPicker.show(
            context = this,
            title = title,
            initialColor = initialColor,
        ) { color ->
            if (darkMode) {
                WidgetSettingsStore.saveDarkFontColor(context = this, color = color)
            } else {
                WidgetSettingsStore.saveLightFontColor(context = this, color = color)
            }
            settingsChanged()
        }
    }

    private fun settingsChanged() {
        renderSettings()
        refreshAllWidgets(this)
    }

    private fun renderSettings() {
        val settings = WidgetSettingsStore.load(this)
        targetDateButton.text = settings.targetDate.formatForDisplay()
        renderColorButton(button = lightColorButton, color = settings.lightFontColor)
        renderColorButton(button = darkColorButton, color = settings.darkFontColor)
    }

    private fun renderColorButton(
        button: MaterialButton,
        color: Int,
    ) {
        button.text = String.format(Locale.ROOT, "#%06X", color and RGB_MASK)
        button.backgroundTintList = ColorStateList.valueOf(color)
        button.setTextColor(
            if (ColorUtils.calculateLuminance(color) > DARK_TEXT_LUMINANCE_THRESHOLD) {
                Color.BLACK
            } else {
                Color.WHITE
            },
        )
    }

    private fun LocalDate.formatForDisplay(): String =
        format(
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .withLocale(resources.configuration.locales[0]),
        )
}

private fun LocalDate.toUtcMilliseconds(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
