package com.github.panlelapin.mementomori

/** Pure fitting rule; dimensions are pixels measured with the actual typeface and spacing. */
internal object WidgetFontSizeCalculator {
    /** Shrinks uniformly to preserve glyph proportions and both padding constraints. */
    fun fitScale(
        width: Float,
        height: Float,
        textWidth: Float,
        textHeight: Float,
    ): Float {
        require(width.isFinite() && height.isFinite() && width >= 0f && height >= 0f)
        require(textWidth.isFinite() && textHeight.isFinite() && textWidth > 0f && textHeight > 0f)
        return minOf(a = 1f, b = width / textWidth, c = height / textHeight)
    }
}
