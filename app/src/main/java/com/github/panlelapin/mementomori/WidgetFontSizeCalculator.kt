package com.github.panlelapin.mementomori

import kotlin.math.floor

internal const val WIDGET_PADDING_DP = 8f

/** Computes a conservative monospace size that keeps every line fully visible. */
internal object WidgetFontSizeCalculator {
    private const val LINE_COUNT = 3
    private const val MONOSPACE_GLYPH_WIDTH_EM = 0.68f
    private const val LINE_HEIGHT_EM = 1.35f
    private const val MINIMUM_FONT_SIZE_SP = 1f
    private const val FONT_SIZE_MULTIPLIER = 1.5f

    fun calculateSp(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float,
        labels: List<String>,
    ): Float {
        require(labels.isNotEmpty())
        require(fontScale > 0f)

        val availableWidthDp = (widthDp - 2f * WIDGET_PADDING_DP).coerceAtLeast(0f)
        val availableHeightDp = (heightDp - 2f * WIDGET_PADDING_DP).coerceAtLeast(0f)
        val longestLineLength = labels.maxOf { it.length }.coerceAtLeast(1)
        val widthLimitedSp =
            availableWidthDp / (fontScale * longestLineLength * MONOSPACE_GLYPH_WIDTH_EM)
        val heightLimitedSp = availableHeightDp / (fontScale * LINE_COUNT * LINE_HEIGHT_EM)

        return (floor(minOf(a = widthLimitedSp, b = heightLimitedSp)) * FONT_SIZE_MULTIPLIER)
            .coerceAtLeast(MINIMUM_FONT_SIZE_SP)
    }
}
