package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFontSizeCalculatorTest {
    @Test
    fun measuredContentFitsBothDimensionsWithoutStretching() {
        for (width in listOf(0f, 94f, 204f, 400f)) {
            for (height in listOf(0f, 94f, 204f, 400f)) {
                val scale = WidgetFontSizeCalculator.fitScale(width, height, 180f, 260f)
                assertTrue(180f * scale <= width + 0.001f)
                assertTrue(260f * scale <= height + 0.001f)
                assertTrue(scale in 0f..1f)
            }
        }
    }

    @Test
    fun neverEnlargesAlreadyFittingText() {
        assertEquals(1f, WidgetFontSizeCalculator.fitScale(200f, 200f, 20f, 30f), 0f)
    }

    @Test
    fun rejectsInvalidMeasurements() {
        for (value in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                WidgetFontSizeCalculator.fitScale(value, 10f, 10f, 10f)
            }
            assertThrows(IllegalArgumentException::class.java) {
                WidgetFontSizeCalculator.fitScale(10f, value, 10f, 10f)
            }
            assertThrows(IllegalArgumentException::class.java) {
                WidgetFontSizeCalculator.fitScale(10f, 10f, value, 10f)
            }
            assertThrows(IllegalArgumentException::class.java) {
                WidgetFontSizeCalculator.fitScale(10f, 10f, 10f, value)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetFontSizeCalculator.fitScale(10f, 10f, 0f, 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetFontSizeCalculator.fitScale(10f, 10f, 1f, 0f)
        }
    }
}
