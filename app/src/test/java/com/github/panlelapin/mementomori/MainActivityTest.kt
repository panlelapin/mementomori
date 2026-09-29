package com.github.panlelapin.mementomori

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test
    fun activityFollowsNightModeThroughConfigurationRecreation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val configuration =
            android.content.res.Configuration(
                controller.get().resources.configuration,
            )
        configuration.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
        controller.configurationChange(configuration)
        assertEquals(
            android.content.res.Configuration.UI_MODE_NIGHT_YES,
            controller
                .get()
                .resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK,
        )
        configuration.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
        controller.configurationChange(configuration)
        assertEquals(
            android.content.res.Configuration.UI_MODE_NIGHT_NO,
            controller
                .get()
                .resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK,
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun insetsIncludeLandscapeCutoutsWithoutAccumulatingPadding() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val content = controller.get().findViewById<View>(R.id.settings_content)
        val left = content.paddingLeft
        val insets =
            androidx.core.view.WindowInsetsCompat
                .Builder()
                .setInsets(
                    androidx.core.view.WindowInsetsCompat.Type
                        .displayCutout(),
                    androidx.core.graphics.Insets
                        .of(60, 0, 0, 0),
                ).build()
        repeat(2) {
            androidx.core.view.ViewCompat
                .dispatchApplyWindowInsets(content, insets)
        }
        assertEquals(left + 60, content.paddingLeft)
        controller.pause().stop().destroy()
    }

    @Before
    fun resetPreferences() {
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
    }

    @Test
    fun restoredDatePickerStillSavesItsSelection() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.get().findViewById<View>(R.id.target_date_button).performClick()
        controller.get().supportFragmentManager.executePendingTransactions()
        controller.recreate()
        val activity = controller.get()
        activity.supportFragmentManager.executePendingTransactions()
        val picker =
            activity.supportFragmentManager.findFragmentByTag(
                "target_date_picker",
            ) as androidx.fragment.app.DialogFragment
        picker
            .requireDialog()
            .findViewById<Button>(
                com.google.android.material.R.id.confirm_button,
            ).performClick()
        assertEquals(
            TARGET_DATE.toString(),
            activity
                .getSharedPreferences(
                    "widget_settings",
                    Context.MODE_PRIVATE,
                ).getString("target_date", null),
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun restoredColorDialogKeepsUnsavedChannelsAndDeliversResult() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.get().findViewById<View>(R.id.light_color_button).performClick()
        controller.get().supportFragmentManager.executePendingTransactions()
        val initial =
            controller.get().supportFragmentManager.findFragmentByTag(
                MaterialColorPicker.RESULT_KEY,
            ) as MaterialColorPicker
        sliders(initial.requireDialog()).zip(listOf(20f, 40f, 60f)).forEach { (slider, value) ->
            slider.value =
                value
        }
        controller.recreate()
        val activity = controller.get()
        activity.supportFragmentManager.executePendingTransactions()
        val restored =
            activity.supportFragmentManager.findFragmentByTag(
                MaterialColorPicker.RESULT_KEY,
            ) as MaterialColorPicker
        assertEquals(listOf(20f, 40f, 60f), sliders(restored.requireDialog()).map { it.value })
        (restored.requireDialog() as AlertDialog)
            .getButton(
                AlertDialog.BUTTON_POSITIVE,
            ).performClick()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idle()
        assertEquals(Color.rgb(20, 40, 60), WidgetSettingsStore.load(activity).lightFontColor)
        controller.pause().stop().destroy()
    }

    @Test
    fun colorButtonsAreReadableAndNamedForAccessibility() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val button = activity.findViewById<MaterialButton>(R.id.light_color_button)
        assertTrue(ColorUtils.calculateContrast(button.currentTextColor, Color.GRAY) >= 4.5)
        assertTrue(button.contentDescription.toString().contains("Light mode"))
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.getDefaultNightMode(),
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun utcDateEncodingPreservesLeapDaysAndMonthEnds() {
        for (date in listOf(LocalDate.of(2032, 2, 29), LocalDate.of(2031, 1, 31))) {
            assertEquals(date, date.toUtcMilliseconds().toLocalDateUtc())
        }
    }

    private fun sliders(dialog: Dialog): List<Slider> =
        descendants(dialog.window!!.decorView).filterIsInstance<Slider>()

    private fun descendants(view: View): List<View> =
        listOf(view) +
            if (view is ViewGroup) {
                (0 until view.childCount).flatMap {
                    descendants(view.getChildAt(it))
                }
            } else {
                emptyList()
            }
}
