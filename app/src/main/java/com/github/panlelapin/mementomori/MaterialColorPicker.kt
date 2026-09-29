package com.github.panlelapin.mementomori

import android.R.string.cancel
import android.R.string.ok
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
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

/** Restorable Material RGB dialog; unsaved selection lives in fragment state, not preferences. */
class MaterialColorPicker : DialogFragment() {
    private var controls: ColorControls? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val arguments = requireArguments()
        val initialColor = savedInstanceState?.getInt(COLOR_KEY) ?: arguments.getInt(COLOR_KEY)
        val currentControls = ColorControls(context, initialColor)
        controls = currentControls
        val isDarkMode = arguments.getBoolean(DARK_MODE_KEY)
        val title = if (isDarkMode) R.string.dark_mode else R.string.light_mode
        val builder =
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(ScrollView(context).apply { addView(currentControls.root) })
                .setNegativeButton(cancel, null)
                .setPositiveButton(ok) { _, _ ->
                    parentFragmentManager.setFragmentResult(
                        RESULT_KEY,
                        Bundle().apply {
                            putInt(COLOR_KEY, currentControls.selectedColor())
                            putBoolean(DARK_MODE_KEY, arguments.getBoolean(DARK_MODE_KEY))
                        },
                    )
                }
        return builder.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(
            COLOR_KEY,
            controls?.selectedColor() ?: requireArguments().getInt(COLOR_KEY),
        )
    }

    // Detekt 2 alpha does not resolve this inherited AndroidX super call; it is present below.
    @Suppress("MissingSuperCall")
    override fun onDestroyView() {
        super.onDestroyView()
        controls = null
    }

    /** Stable keys also identify results restored after a configuration change. */
    companion object {
        internal const val RESULT_KEY = "font_color_picker"
        internal const val COLOR_KEY = "color"
        internal const val DARK_MODE_KEY = "dark_mode"

        internal fun newInstance(
            darkMode: Boolean,
            color: Int,
        ): MaterialColorPicker {
            val arguments =
                Bundle().apply {
                    putBoolean(DARK_MODE_KEY, darkMode)
                    putInt(COLOR_KEY, color)
                }
            return MaterialColorPicker().apply { this.arguments = arguments }
        }
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
