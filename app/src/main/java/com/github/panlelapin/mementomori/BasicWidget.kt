package com.github.panlelapin.mementomori

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private const val WIDGET_FOREGROUND_ARGB = 0xFF1D1B20

/** Renders the basic home-screen widget. */
class BasicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val widgetText = context.getString(R.string.widget_text)
        provideContent { WidgetContent(widgetText) }
    }

    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    @Composable
    private fun WidgetContent(widgetText: String) {
        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(R.color.widget_background)
                    .cornerRadius(16.dp)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = widgetText,
                style =
                    TextStyle(
                        color = ColorProvider(Color(WIDGET_FOREGROUND_ARGB)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}

/** Receives widget lifecycle events and supplies [BasicWidget]. */
class BasicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BasicWidget()
}
