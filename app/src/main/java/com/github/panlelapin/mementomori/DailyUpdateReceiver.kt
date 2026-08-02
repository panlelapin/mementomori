package com.github.panlelapin.mementomori

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime

private const val ALARM_REQUEST_CODE = 2036
private const val DAILY_UPDATE_ACTION =
    "com.github.panlelapin.mementomori.action.DAILY_UPDATE"

/** Refreshes every widget after boot, time changes, package updates, and the daily alarm. */
class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            DAILY_UPDATE_ACTION,

            Intent.ACTION_BOOT_COMPLETED,

            Intent.ACTION_MY_PACKAGE_REPLACED,

            Intent.ACTION_TIME_CHANGED,

            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit

            else -> return
        }

        if (hasActiveWidgets(context)) {
            schedule(context)
            refreshAllWidgets(context)
        } else {
            cancel(context)
        }
    }

    /** Scheduling helpers for the widget's daily refresh. */
    companion object {
        /** Schedules a one-shot refresh for the next local 1 a.m. */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                AlarmTimeCalculator.nextOneAmMillis(ZonedDateTime.now()),
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
                Intent(context, DailyUpdateReceiver::class.java).setAction(DAILY_UPDATE_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

internal object AlarmTimeCalculator {
    private val refreshTime: LocalTime = LocalTime.of(1, 0)

    fun nextOneAmMillis(now: ZonedDateTime): Long {
        val nextDate =
            if (now.toLocalTime().isBefore(refreshTime)) {
                now.toLocalDate()
            } else {
                now.toLocalDate().plusDays(1)
            }
        return nextDate
            .atTime(refreshTime)
            .atZone(now.zone)
            .toInstant()
            .toEpochMilli()
    }
}

private fun hasActiveWidgets(context: Context): Boolean {
    val provider = ComponentName(context, BasicWidgetReceiver::class.java)
    return AppWidgetManager.getInstance(context).getAppWidgetIds(provider).isNotEmpty()
}

@Suppress("InjectDispatcher")
internal fun BroadcastReceiver.refreshAllWidgets(context: Context) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            BasicWidget().updateAll(context.applicationContext)
        } finally {
            pendingResult.finish()
        }
    }
}
