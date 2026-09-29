package com.github.panlelapin.mementomori

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.SizeF
import android.util.TypedValue
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

internal const val WIDGET_PADDING_DP = 8f
internal const val WIDGET_LINE_SPACING = 1.15f
internal const val WIDGET_SUFFIX_GAP_EM = 0.10f
private const val VISUAL_SIZE_MULTIPLIER = 1.35f
private const val LINE_COUNT = 3

/** Draws Noto Mono in-process, fitting actual metrics instead of estimated character sizes. */
internal object WidgetBitmapRenderer {
    fun render(
        context: Context,
        size: SizeF,
        labels: List<String>,
        color: Int,
    ): Bitmap {
        require(labels.size == LINE_COUNT)
        val metrics = context.resources.displayMetrics
        val width = (size.width * metrics.density).roundToInt().coerceAtLeast(1)
        val height = (size.height * metrics.density).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        bitmap.density = metrics.densityDpi
        val padding = WIDGET_PADDING_DP * metrics.density
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color
                textAlign = Paint.Align.RIGHT
                typeface = context.resources.getFont(R.font.noto_mono_regular)
                // Android owns the nonlinear sp conversion; never divide by Configuration.fontScale.
                val candidateSp =
                    TypedValue.deriveDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        height.toFloat() / LINE_COUNT,
                        metrics,
                    )
                textSize =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        candidateSp * VISUAL_SIZE_MULTIPLIER,
                        metrics,
                    )
            }
        paint.textSize *=
            WidgetFontSizeCalculator.fitScale(
                width = (width - 2 * padding).coerceAtLeast(0f),
                height = (height - 2 * padding).coerceAtLeast(0f),
                textWidth = labels.maxOf { labelWidth(paint, it) },
                textHeight = blockHeight(paint, labels.size),
            )
        val baseline = (height - blockHeight(paint, labels.size)) / 2f - paint.fontMetrics.ascent
        val canvas = Canvas(bitmap)
        labels.forEachIndexed { index, label ->
            drawLabel(
                canvas = canvas,
                paint = paint,
                label = label,
                right = width - padding,
                baseline = baseline + index * paint.fontSpacing * WIDGET_LINE_SPACING,
            )
        }
        return bitmap
    }

    private fun blockHeight(
        paint: Paint,
        lines: Int,
    ): Float =
        paint.fontMetrics.descent -
            paint.fontMetrics.ascent +
            paint.fontSpacing *
            WIDGET_LINE_SPACING *
            (lines - 1)

    private fun labelWidth(
        paint: Paint,
        label: String,
    ): Float =
        paint.measureText(label.replace(oldValue = THIN_SPACE, newValue = "")) +
            paint.textSize *
            WIDGET_SUFFIX_GAP_EM

    private fun drawLabel(
        canvas: Canvas,
        paint: Paint,
        label: String,
        right: Float,
        baseline: Float,
    ) {
        val parts = label.split(THIN_SPACE)
        require(parts.size == 2)
        canvas.drawText(parts[1], right, baseline, paint)
        canvas.drawText(
            parts[0],
            right - paint.measureText(parts[1]) - paint.textSize * WIDGET_SUFFIX_GAP_EM,
            baseline,
            paint,
        )
    }
}
