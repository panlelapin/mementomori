package com.github.panlelapin.mementomori

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.LocalDate

private const val REFRESH_ACTION = "com.github.panlelapin.mementomori.action.REFRESH_WIDGET"
private const val DEFAULT_WIDGET_WIDTH_DP = 55f
private const val DEFAULT_WIDGET_HEIGHT_DP = 110f

/** Renders Memento Mori with the bundled Input Mono font in a native RemoteViews TextView. */
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
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DailyUpdateReceiver.schedule(context)
        BasicWidgetRenderer.update(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        BasicWidgetRenderer.update(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == REFRESH_ACTION) {
            val appWidgetId =
                intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                BasicWidgetRenderer.update(context, intArrayOf(appWidgetId))
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        DailyUpdateReceiver.cancel(context)
        super.onDisabled(context)
    }
}

/** Produces native RemoteViews so the app's bundled monospace font is used by every launcher. */
internal object BasicWidgetRenderer {
    fun update(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews(context, appWidgetId))
        }
    }

    private fun remoteViews(
        context: Context,
        appWidgetId: Int,
    ): RemoteViews {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val labels = CountdownCalculator.from(LocalDate.now()).labels()
        val fontSize =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = currentWidthDp(options),
                heightDp = currentHeightDp(options),
                fontScale = context.resources.configuration.fontScale,
                labels = labels,
            )

        return RemoteViews(context.packageName, R.layout.widget_content).apply {
            setTextViewText(R.id.widget_countdown, labels.joinToString(separator = "\n"))
            setTextViewTextSize(
                R.id.widget_countdown,
                TypedValue.COMPLEX_UNIT_SP,
                fontSize,
            )
            setOnClickPendingIntent(
                R.id.widget_countdown,
                refreshPendingIntent(context, appWidgetId),
            )
        }
    }

    private fun currentWidthDp(options: Bundle): Float =
        options
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .takeIf { it > 0 }
            ?.toFloat()
            ?: DEFAULT_WIDGET_WIDTH_DP

    private fun currentHeightDp(options: Bundle): Float =
        options
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            .takeIf { it > 0 }
            ?.toFloat()
            ?: DEFAULT_WIDGET_HEIGHT_DP

    private fun refreshPendingIntent(
        context: Context,
        appWidgetId: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(context, BasicWidgetReceiver::class.java)
                .setAction(REFRESH_ACTION)
                .setData(Uri.parse("mementomori://widget/$appWidgetId"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
