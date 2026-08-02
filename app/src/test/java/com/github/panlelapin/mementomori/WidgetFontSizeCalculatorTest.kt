package com.github.panlelapin.mementomori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFontSizeCalculatorTest {
    private val labels = listOf("10a", "120m", "521s")

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

        assertEquals(23f, square)
        assertEquals(19f, narrow)
        assertEquals(13f, short)
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
            15f,
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
