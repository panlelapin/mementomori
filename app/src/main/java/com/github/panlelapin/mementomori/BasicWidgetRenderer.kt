package com.github.panlelapin.mementomori

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.net.toUri
import java.time.LocalDate

private const val DEFAULT_WIDGET_SIZE_DP = 110f

/** Reads settings once per refresh and supplies a separately rendered bitmap for each host size. */
internal object BasicWidgetRenderer {
    fun update(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val today = LocalDate.now()
        val settings = WidgetSettingsStore.load(context, today)
        val countdown = CountdownCalculator.from(today, settings.targetDate)
        appWidgetIds.forEach { id ->
            val sizes = widgetSizes(manager.getAppWidgetOptions(id), context)
            val views =
                sizes.associateWith { size ->
                    remoteViews(
                        context = context,
                        id = id,
                        size = size,
                        countdown = countdown,
                        settings = settings,
                    )
                }
            manager.updateAppWidget(id, RemoteViews(views))
        }
    }

    /** Legacy hosts report portrait width/landscape height minima, not one usable rectangle. */
    fun widgetSizes(
        options: Bundle,
        context: Context,
    ): List<SizeF> {
        val reported =
            options.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                SizeF::class.java,
            )
        val exact =
            reported
                .orEmpty()
                .asSequence()
                .filter(::isValidSize)
                .distinct()
                .toList()
        if (exact.isNotEmpty()) return exact
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthKey =
            if (isLandscape) {
                AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            }
        val heightKey =
            if (isLandscape) {
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            }
        return listOf(SizeF(dimension(options, widthKey), dimension(options, heightKey)))
    }

    private fun isValidSize(size: SizeF): Boolean =
        size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0

    private fun dimension(
        options: Bundle,
        key: String,
    ): Float = options.getInt(key).takeIf { it > 0 }?.toFloat() ?: DEFAULT_WIDGET_SIZE_DP

    private fun remoteViews(
        context: Context,
        id: Int,
        size: SizeF,
        countdown: Countdown,
        settings: WidgetSettings,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_content).apply {
            setImageViewBitmap(
                R.id.widget_countdown,
                WidgetBitmapRenderer.render(
                    context = context,
                    size = size,
                    labels = countdown.labels(),
                    color = WidgetSettingsStore.currentFontColor(context, settings),
                ),
            )
            setContentDescription(
                R.id.widget_countdown,
                context.getString(
                    R.string.countdown_accessibility,
                    countdown.years,
                    countdown.months,
                    countdown.weeks,
                ),
            )
            setOnClickPendingIntent(R.id.widget_countdown, refreshPendingIntent(context, id))
        }

    private fun refreshPendingIntent(
        context: Context,
        id: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, BasicWidgetReceiver::class.java)
                .setAction(REFRESH_ACTION)
                .setData("mementomori://widget/$id".toUri())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
