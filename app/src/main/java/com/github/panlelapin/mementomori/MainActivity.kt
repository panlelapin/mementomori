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
        restoreDatePickerListener()
        supportFragmentManager.setFragmentResultListener(
            MaterialColorPicker.RESULT_KEY,
            this,
        ) { _, result ->
            val color = result.getInt(MaterialColorPicker.COLOR_KEY)
            if (result.getBoolean(MaterialColorPicker.DARK_MODE_KEY)) {
                WidgetSettingsStore.saveDarkFontColor(this, color)
            } else {
                WidgetSettingsStore.saveLightFontColor(this, color)
            }
            settingsChanged()
        }
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
            val systemBars =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
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
        if (supportFragmentManager.findFragmentByTag(DATE_PICKER_TAG) != null) return
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
        attachDatePickerListener(picker)
        picker.show(supportFragmentManager, DATE_PICKER_TAG)
    }

    private fun restoreDatePickerListener() {
        // FragmentManager restores the picker, but Material does not persist lambda listeners.
        val picker =
            supportFragmentManager.findFragmentByTag(
                DATE_PICKER_TAG,
            ) as? MaterialDatePicker<*>
        if (picker != null) attachDatePickerListener(picker)
    }

    private fun attachDatePickerListener(picker: MaterialDatePicker<*>) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener { selection ->
            if (selection is Long) saveTargetDate(selection.toLocalDateUtc())
        }
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
        if (supportFragmentManager.findFragmentByTag(MaterialColorPicker.RESULT_KEY) != null) return
        val settings = WidgetSettingsStore.load(this)
        val initialColor =
            WidgetColorSelector.select(
                darkMode = darkMode,
                lightColor = settings.lightFontColor,
                darkColor = settings.darkFontColor,
            )
        MaterialColorPicker
            .newInstance(darkMode, initialColor)
            .show(supportFragmentManager, MaterialColorPicker.RESULT_KEY)
    }

    private fun settingsChanged() {
        renderSettings()
        refreshAllWidgets(this)
    }

    private fun renderSettings() {
        val settings = WidgetSettingsStore.load(this)
        targetDateButton.text =
            settings.targetDate.formatForDisplay(resources.configuration.locales[0])
        targetDateButton.contentDescription =
            getString(R.string.target_date_accessibility, targetDateButton.text)
        renderColorButton(button = lightColorButton, color = settings.lightFontColor)
        renderColorButton(button = darkColorButton, color = settings.darkFontColor)
    }

    private fun renderColorButton(
        button: MaterialButton,
        color: Int,
    ) {
        button.text = String.format(Locale.ROOT, "#%06X", color and RGB_MASK)
        button.backgroundTintList = ColorStateList.valueOf(color)
        val blackContrast = ColorUtils.calculateContrast(Color.BLACK, color)
        val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, color)
        button.setTextColor(if (blackContrast >= whiteContrast) Color.BLACK else Color.WHITE)
        val mode =
            getString(
                if (button.id ==
                    R.id.light_color_button
                ) {
                    R.string.light_mode
                } else {
                    R.string.dark_mode
                },
            )
        button.contentDescription = getString(R.string.color_accessibility, mode, button.text)
    }
}

private fun LocalDate.formatForDisplay(locale: Locale): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

/** MaterialDatePicker encodes civil dates at UTC midnight; this is not a local instant. */
internal fun LocalDate.toUtcMilliseconds(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Decode the UTC date without shifting it through the user's time zone. */
internal fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
