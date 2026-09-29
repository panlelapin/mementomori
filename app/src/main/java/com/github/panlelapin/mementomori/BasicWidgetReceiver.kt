package com.github.panlelapin.mementomori

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

internal const val REFRESH_ACTION = "com.github.panlelapin.mementomori.action.REFRESH_WIDGET"

/** Handles widget lifecycle and explicit taps; drawing belongs to [BasicWidgetRenderer]. */
class BasicWidgetReceiver : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DailyUpdateReceiver.schedule(context)
        refreshAllWidgets(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        DailyUpdateReceiver.schedule(context)
        BasicWidgetRenderer.update(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        BasicWidgetRenderer.update(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == REFRESH_ACTION) {
            val id =
                intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                DailyUpdateReceiver.schedule(context)
                BasicWidgetRenderer.update(context, intArrayOf(id))
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        DailyUpdateReceiver.cancel(context)
        super.onDisabled(context)
    }
}
