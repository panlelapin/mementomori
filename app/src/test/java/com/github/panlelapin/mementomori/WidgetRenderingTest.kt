package com.github.panlelapin.mementomori

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.SizeF
import android.view.LayoutInflater
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WidgetRenderingTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun enlargedWidgetActuallyPaintsLargerGlyphs() {
        val labels = Countdown(10, 120, 521).labels()

        fun paintedRows(size: Float): Int {
            val bitmap =
                WidgetBitmapRenderer.render(
                    context,
                    SizeF(size, size),
                    labels,
                    Color.WHITE,
                )
            return (0 until bitmap.height).count { y ->
                (0 until bitmap.width).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
            }
        }
        assertTrue(paintedRows(220f) > paintedRows(110f) * 1.5f)
    }

    @Test
    fun staticPreviewMatchesBitmapGeometryAndThinSuffixSpacing() {
        val size = 110
        val density = context.resources.displayMetrics.density
        val real =
            WidgetBitmapRenderer.render(
                context,
                SizeF(size / density, size / density),
                Countdown(10, 120, 521).labels(),
                Color.WHITE,
            )
        val preview = LayoutInflater.from(context).inflate(R.layout.widget_preview, null)
        val spec =
            android.view.View.MeasureSpec.makeMeasureSpec(
                size,
                android.view.View.MeasureSpec.EXACTLY,
            )
        preview.measure(spec, spec)
        preview.layout(0, 0, size, size)
        val image =
            android.graphics.Bitmap.createBitmap(
                size,
                size,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        preview.draw(android.graphics.Canvas(image))
        val realBoxes = glyphBoxes(real, isPreview = false)
        val previewBoxes = glyphBoxes(image, isPreview = true)
        assertEquals(11, realBoxes.size)
        assertEquals(realBoxes.size, previewBoxes.size)
        // Font hinting snaps advances to pixels; vectors rasterize the unhinted outlines.
        // Compare each glyph position, not raster identity; a full-width suffix space fails this.
        realBoxes.zip(previewBoxes).forEach { (actual, expected) ->
            assertTrue(kotlin.math.abs(actual.left - expected.left) <= 3)
            assertTrue(kotlin.math.abs(actual.right - expected.right) <= 3)
            assertTrue(kotlin.math.abs(actual.top - expected.top) <= 1)
            assertTrue(kotlin.math.abs(actual.bottom - expected.bottom) <= 1)
        }
    }

    private fun glyphBoxes(
        bitmap: android.graphics.Bitmap,
        isPreview: Boolean,
    ): List<android.graphics.Rect> {
        fun hasInk(
            x: Int,
            y: Int,
        ): Boolean =
            (
                if (isPreview) {
                    Color.red(
                        bitmap.getPixel(x, y),
                    )
                } else {
                    Color.alpha(bitmap.getPixel(x, y))
                }
            ) >
                128
        val rows =
            bands(
                (0 until bitmap.height).filter { y ->
                    (0 until bitmap.width).any { x -> hasInk(x, y) }
                },
            )
        assertEquals(3, rows.size)
        return rows.flatMap { row ->
            bands((0 until bitmap.width).filter { x -> row.any { y -> hasInk(x, y) } })
                .map { column ->
                    android.graphics.Rect(column.first, row.first, column.last, row.last)
                }
        }
    }

    private fun bands(indices: List<Int>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        indices.forEach { index ->
            if (result.isEmpty() || index > result.last().last + 1) {
                result.add(index..index)
            } else {
                result[result.lastIndex] = result.last().first..index
            }
        }
        return result
    }

    @Test
    fun bundledFontHasEqualAdvancesAndPreviewCannotSubstituteIt() {
        val paint =
            Paint().apply {
                typeface = context.resources.getFont(R.font.noto_mono_regular)
                textSize = 100f
            }
        val advance = paint.measureText("0")
        "0123456789YMW".forEach { assertEquals(advance, paint.measureText(it.toString()), 0.001f) }
        val preview = LayoutInflater.from(context).inflate(R.layout.widget_preview, null)
        assertTrue(preview is ImageView)
        assertEquals(ImageView.ScaleType.FIT_CENTER, (preview as ImageView).scaleType)
    }

    @Test
    fun pixelsStayInsidePaddingAtAllSizesScalesAndCountdownLengths() {
        for (scale in listOf(1f, 1.5f, 2f)) {
            val configuration =
                Configuration(context.resources.configuration).apply {
                    fontScale =
                        scale
                }
            val scaled = context.createConfigurationContext(configuration)
            for (size in listOf(
                SizeF(110f, 110f),
                SizeF(220f, 220f),
                SizeF(110f, 220f),
                SizeF(220f, 110f),
            )) {
                for (labels in listOf(
                    Countdown(10, 120, 521).labels(),
                    Countdown(999, 11988, 52125).labels(),
                )) {
                    val bitmap = WidgetBitmapRenderer.render(scaled, size, labels, Color.WHITE)
                    val padding =
                        (WIDGET_PADDING_DP * scaled.resources.displayMetrics.density)
                            .toInt()
                    var painted = 0
                    val rows = mutableSetOf<Int>()
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) {
                            if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                                assertTrue(
                                    "pixel $x,$y outside padding at $size",
                                    x >= padding - 1 &&
                                        x < bitmap.width - padding + 1 &&
                                        y >= padding - 1 &&
                                        y < bitmap.height - padding + 1,
                                )
                                painted++
                                rows.add(y)
                            }
                        }
                    }
                    assertTrue(painted > 0)
                    assertEquals(3, rows.count { it - 1 !in rows })
                }
            }
        }
    }

    @Test
    fun hostSizesAreExactAndLegacyFallbackUsesOrientationPairs() {
        val options =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 240)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            }
        val portrait =
            context.createConfigurationContext(
                Configuration().apply {
                    orientation =
                        Configuration.ORIENTATION_PORTRAIT
                },
            )
        val landscape =
            context.createConfigurationContext(
                Configuration().apply {
                    orientation =
                        Configuration.ORIENTATION_LANDSCAPE
                },
            )
        assertEquals(listOf(SizeF(110f, 180f)), BasicWidgetRenderer.widgetSizes(options, portrait))
        assertEquals(listOf(SizeF(240f, 80f)), BasicWidgetRenderer.widgetSizes(options, landscape))
        options.putParcelableArrayList(
            AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(160f, 200f), SizeF(240f, 120f)),
        )
        assertEquals(
            listOf(SizeF(160f, 200f), SizeF(240f, 120f)),
            BasicWidgetRenderer.widgetSizes(options, portrait),
        )
    }
}
