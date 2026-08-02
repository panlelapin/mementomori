package com.github.panlelapin.mementomori

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val WIDGET_FOREGROUND_ARGB = 0xFFFFFFFF
private const val ALARM_REQUEST_CODE = 2036
private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L
private val TARGET_DATE: LocalDate = LocalDate.of(2036, 3, 17)

/** Renders the basic home-screen widget. */
class BasicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val countdown = Countdown.from(LocalDate.now())
        provideContent { WidgetContent(countdown) }
    }

    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    @Composable
    private fun WidgetContent(countdown: Countdown) {
        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                CountdownText("${countdown.years}a")
                CountdownText("${countdown.months}m")
                CountdownText("${countdown.weeks}s")
            }
        }
    }

    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    @Composable
    private fun CountdownText(text: String) {
        Text(
            text = text,
            style =
                TextStyle(
                    color = ColorProvider(Color(WIDGET_FOREGROUND_ARGB)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

/** Receives widget lifecycle events and supplies [BasicWidget]. */
class BasicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BasicWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DailyUpdateReceiver.schedule(context)
    }

    override fun onDisabled(context: Context) {
        DailyUpdateReceiver.cancel(context)
        super.onDisabled(context)
    }
}

/** Refreshes all widget instances after boot and at the daily 1 a.m. alarm. */
class DailyUpdateReceiver : BroadcastReceiver() {
    @Suppress("InjectDispatcher")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            schedule(context)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                BasicWidget().updateAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Scheduling helpers for the widget's daily refresh. */
    companion object {
        /** Schedules the next 1 a.m. refresh and repeats it every 24 hours. */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextOneAmMillis(),
                DAY_IN_MILLIS,
                pendingIntent(context),
            )
        }

        /** Cancels the daily refresh when the last widget is removed. */
        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, DailyUpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun nextOneAmMillis(): Long {
            val now = LocalDateTime.now()
            var next = LocalDateTime.of(now.toLocalDate(), LocalTime.of(1, 0))
            if (!now.isBefore(next)) {
                next = next.plusDays(1)
            }
            return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}

private data class Countdown(
    val years: Long,
    val months: Long,
    val weeks: Long,
) {
    companion object {
        fun from(today: LocalDate): Countdown =
            Countdown(
                years = ChronoUnit.YEARS.between(today, TARGET_DATE),
                months = ChronoUnit.MONTHS.between(today, TARGET_DATE),
                weeks = ChronoUnit.WEEKS.between(today, TARGET_DATE),
            )
    }
}
