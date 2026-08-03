package com.github.panlelapin.mementomori

import android.R.string.cancel
import android.R.string.ok
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.R.style.TextAppearance_Material3_LabelLarge
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

private const val CHANNEL_MIN = 0f
private const val CHANNEL_MAX = 255f
private const val CHANNEL_STEP = 1f
private const val DIALOG_PADDING_DP = 24
private const val PREVIEW_HEIGHT_DP = 64
private const val PREVIEW_CORNER_DP = 16f
private const val PREVIEW_STROKE_DP = 1

/** Material RGB picker used for the light and dark widget colors. */
internal object MaterialColorPicker {
    fun show(
        context: Context,
        @StringRes title: Int,
        initialColor: Int,
        onColorSelected: (Int) -> Unit,
    ) {
        val controls = ColorControls(context = context, initialColor = initialColor)
        val builder = MaterialAlertDialogBuilder(context)
        builder
            .setTitle(title)
            .setView(controls.root)
            .setNegativeButton(cancel, null)
            .setPositiveButton(ok) { _, _ ->
                onColorSelected(controls.selectedColor())
            }
        val dialog = builder.create()
        dialog.show()
    }
}

private class ColorControls(
    context: Context,
    initialColor: Int,
) {
    val root: LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = context.toPixels(DIALOG_PADDING_DP)
            setPadding(padding, padding, padding, 0)
        }
    private val preview =
        MaterialCardView(context).apply {
            radius = context.toPixels(PREVIEW_CORNER_DP)
            strokeWidth = context.toPixels(PREVIEW_STROKE_DP)
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.toPixels(PREVIEW_HEIGHT_DP),
                )
        }
    private val red =
        addChannel(
            context = context,
            label = R.string.red,
            initialValue = Color.red(initialColor),
        )
    private val green =
        addChannel(
            context = context,
            label = R.string.green,
            initialValue = Color.green(initialColor),
        )
    private val blue =
        addChannel(
            context = context,
            label = R.string.blue,
            initialValue = Color.blue(initialColor),
        )

    init {
        root.addView(preview, 0)
        val listener = Slider.OnChangeListener { _, _, _ -> updatePreview() }
        red.addOnChangeListener(listener)
        green.addOnChangeListener(listener)
        blue.addOnChangeListener(listener)
        updatePreview()
    }

    fun selectedColor(): Int =
        Color.rgb(red.value.roundToInt(), green.value.roundToInt(), blue.value.roundToInt())

    private fun addChannel(
        context: Context,
        @StringRes label: Int,
        initialValue: Int,
    ): Slider {
        root.addView(
            TextView(context).apply {
                setText(label)
                setTextAppearance(TextAppearance_Material3_LabelLarge)
            },
        )
        return Slider(context).apply {
            valueFrom = CHANNEL_MIN
            valueTo = CHANNEL_MAX
            stepSize = CHANNEL_STEP
            value = initialValue.toFloat()
            contentDescription = context.getString(label)
            root.addView(this)
        }
    }

    private fun updatePreview() {
        preview.setCardBackgroundColor(selectedColor())
    }
}

private fun Context.toPixels(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

private fun Context.toPixels(value: Float): Float = value * resources.displayMetrics.density
