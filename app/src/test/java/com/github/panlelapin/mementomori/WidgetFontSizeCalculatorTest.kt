package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFontSizeCalculatorTest {
    private val labels = listOf("10\u2009Y", "120\u2009M", "521\u2009W")

    @Test
    fun fontSizeUsesTheMostRestrictiveDimension() {
        val square =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 110f,
                heightDp = 110f,
                fontScale = 1f,
                labels = labels,
            )
        val narrow =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 70f,
                heightDp = 110f,
                fontScale = 1f,
                labels = labels,
            )
        val short =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 110f,
                heightDp = 70f,
                fontScale = 1f,
                labels = labels,
            )

        assertEquals(31.05f, square, 0.001f)
        assertEquals(20.25f, narrow, 0.001f)
        assertEquals(17.55f, short, 0.001f)
    }

    @Test
    fun fontSizeGrowsWithWidgetSize() {
        val small =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 55f,
                heightDp = 110f,
                fontScale = 1f,
                labels = labels,
            )
        val large =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 220f,
                heightDp = 220f,
                fontScale = 1f,
                labels = labels,
            )

        assertTrue(large > small)
    }

    @Test
    fun fontScaleIsCompensatedToKeepTextVisible() {
        assertEquals(
            20.25f,
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 110f,
                heightDp = 110f,
                fontScale = 1.5f,
                labels = labels,
            ),
        )
    }

    @Test
    fun tinyWidgetStillReturnsAPositiveUsableSize() {
        assertEquals(
            1f,
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 0f,
                heightDp = 0f,
                fontScale = 1f,
                labels = listOf(""),
            ),
        )
    }

    @Test
    fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 110f,
                heightDp = 110f,
                fontScale = 1f,
                labels = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetFontSizeCalculator.calculateSp(
                widthDp = 110f,
                heightDp = 110f,
                fontScale = 0f,
                labels = labels,
            )
        }
    }
}
