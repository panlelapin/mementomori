package com.github.panlelapin.mementomori

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.LocalDate
import kotlin.math.roundToInt

private const val REFRESH_ACTION = "com.github.panlelapin.mementomori.action.REFRESH_WIDGET"
private const val THIN_SPACE = "\u2009"
private const val DEFAULT_WIDGET_WIDTH_DP = 110f
private const val DEFAULT_WIDGET_HEIGHT_DP = 110f
private const val WIDGET_LINE_SPACING_FACTOR = 1.15f
private const val WIDGET_SUFFIX_GAP_EM = 0.10f

/** Renders Memento Mori as pixels drawn with the bundled monospace font. */
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

/** Produces native RemoteViews whose bitmap cannot have its typeface replaced by the launcher. */
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
        val widthDp = currentWidthDp(options)
        val heightDp = currentHeightDp(options)
        val fontSize =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = widthDp,
                heightDp = heightDp,
                fontScale = context.resources.configuration.fontScale,
                labels = labels,
            )
        val bitmap =
            WidgetBitmapRenderer.render(
                context = context,
                widthDp = widthDp,
                heightDp = heightDp,
                fontSizeSp = fontSize,
                labels = labels,
            )

        return RemoteViews(context.packageName, R.layout.widget_content).apply {
            setImageViewBitmap(
                R.id.widget_countdown,
                bitmap,
            )
            setContentDescription(
                R.id.widget_countdown,
                labels.joinToString(separator = ", "),
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

/** Draws the complete widget in the application process with the exact bundled typeface. */
internal object WidgetBitmapRenderer {
    fun render(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        fontSizeSp: Float,
        labels: List<String>,
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val widthPx = (widthDp * metrics.density).roundToInt().coerceAtLeast(1)
        val heightPx = (heightDp * metrics.density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.density = metrics.densityDpi

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.RIGHT
                textSize =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, metrics)
                typeface = context.resources.getFont(R.font.noto_mono_regular)
            }
        val fontMetrics = paint.fontMetrics
        val lineAdvance = paint.fontSpacing * WIDGET_LINE_SPACING_FACTOR
        val blockHeight =
            fontMetrics.descent - fontMetrics.ascent + lineAdvance * (labels.size - 1)
        val firstBaseline = (heightPx - blockHeight) / 2f - fontMetrics.ascent
        val rightEdge = widthPx - WIDGET_PADDING_DP * metrics.density
        val canvas = Canvas(bitmap)

        labels.forEachIndexed { index, label ->
            drawLabel(
                canvas = canvas,
                paint = paint,
                label = label,
                rightEdge = rightEdge,
                baseline = firstBaseline + index * lineAdvance,
            )
        }
        return bitmap
    }

    private fun drawLabel(
        canvas: Canvas,
        paint: Paint,
        label: String,
        rightEdge: Float,
        baseline: Float,
    ) {
        val separatorIndex = label.indexOf(THIN_SPACE)
        if (separatorIndex < 0) {
            canvas.drawText(label, rightEdge, baseline, paint)
            return
        }

        val number = label.substring(startIndex = 0, endIndex = separatorIndex)
        val suffix = label.substring(startIndex = separatorIndex + THIN_SPACE.length)
        canvas.drawText(suffix, rightEdge, baseline, paint)
        val suffixWidth = paint.measureText(suffix)
        val customGap = paint.textSize * WIDGET_SUFFIX_GAP_EM
        canvas.drawText(number, rightEdge - suffixWidth - customGap, baseline, paint)
    }
}
