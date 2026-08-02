package com.github.panlelapin.mementomori

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate

private const val WIDGET_FOREGROUND_ARGB = 0xFFFFFFFF

/** Renders the home-screen widget at the exact size supplied by the launcher. */
class BasicWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val countdown = CountdownCalculator.from(LocalDate.now())
        provideContent { WidgetContent(countdown) }
    }

    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    @Composable
    private fun WidgetContent(countdown: Countdown) {
        val labels = countdown.labels()
        val widgetSize = LocalSize.current
        val fontScale = LocalContext.current.resources.configuration.fontScale
        val fontSize =
            WidgetFontSizeCalculator.calculateSp(
                widthDp = widgetSize.width.value,
                heightDp = widgetSize.height.value,
                fontScale = fontScale,
                labels = labels,
            )

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(WIDGET_PADDING_DP.dp)
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.End,
            ) {
                labels.forEach { label -> CountdownText(label, fontSize) }
            }
        }
    }

    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    @Composable
    private fun CountdownText(
        text: String,
        fontSizeSp: Float,
    ) {
        Text(
            text = text,
            style =
                TextStyle(
                    color = ColorProvider(Color(WIDGET_FOREGROUND_ARGB)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
    }
}

/** Receives widget lifecycle events and supplies [BasicWidget]. */
class BasicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BasicWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DailyUpdateReceiver.schedule(context)
        refreshAllWidgets(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DailyUpdateReceiver.schedule(context)
    }

    override fun onDisabled(context: Context) {
        DailyUpdateReceiver.cancel(context)
        super.onDisabled(context)
    }
}

/** Recalculates and redraws the touched widget instance. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        BasicWidget().update(context, glanceId)
    }
}
