package com.github.panlelapin.mementomori

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WidgetLifecycleTest {
    @Test
    fun touchRecalculatesSavedDateAndLastRemovalCancelsAlarm() {
        val context = RuntimeEnvironment.getApplication()
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val id = widgets.createWidget(BasicWidgetReceiver::class.java, R.layout.widget_content)
        assertNotNull(alarms.peekNextScheduledAlarm())
        WidgetSettingsStore.saveTargetDate(context, LocalDate.now().plusYears(2))
        val image = widgets.getViewFor(id) as ImageView
        assertTrue(image.performClick())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(
            (
                widgets.getViewFor(
                    id,
                ) as ImageView
            ).contentDescription.toString().startsWith("Full years: 2"),
        )
        BasicWidgetReceiver().onDisabled(context)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun bootTimeChangesAndReplacementRefreshAndRearmOnlyWithWidgets() {
        val context = RuntimeEnvironment.getApplication()
        val widgets = shadowOf(AppWidgetManager.getInstance(context))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val receiver = DailyUpdateReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(alarms.peekNextScheduledAlarm())
        val id = widgets.createWidget(BasicWidgetReceiver::class.java, R.layout.widget_content)
        for (action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )) {
            context.getSystemService(AlarmManager::class.java).cancelAll()
            receiver.onReceive(context, Intent(action))
            assertNotNull(alarms.peekNextScheduledAlarm())
            assertNotNull((widgets.getViewFor(id) as ImageView).drawable)
        }
    }

    @Test
    fun previewAndIconResourcesFollowSystemAppearance() {
        val context = RuntimeEnvironment.getApplication()
        for ((mode, background) in listOf(
            Configuration.UI_MODE_NIGHT_NO to Color.BLACK,
            Configuration.UI_MODE_NIGHT_YES to Color.WHITE,
        )) {
            val themed =
                context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply {
                        uiMode =
                            mode
                    },
                )
            assertEquals(background, themed.getColor(R.color.widget_preview_background))
            assertEquals(background, themed.getColor(R.color.app_icon_background))
            assertEquals(
                if (background ==
                    Color.BLACK
                ) {
                    Color.WHITE
                } else {
                    Color.BLACK
                },
                themed.getColor(R.color.widget_preview_foreground),
            )
        }
    }
}
