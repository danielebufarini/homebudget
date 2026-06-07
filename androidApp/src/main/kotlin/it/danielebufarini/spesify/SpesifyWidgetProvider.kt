package it.danielebufarini.spesify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.widget.SPESIFY_WIDGET_REFRESH_ACTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

open class SpesifyWidgetProvider : AppWidgetProvider() {

    private val widgetLayoutMode: SpesifyWidgetLayoutMode
        get() = if (this is SpesifyWideWidgetProvider) {
            SpesifyWidgetLayoutMode.Wide
        } else {
            SpesifyWidgetLayoutMode.Auto
        }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == SPESIFY_WIDGET_REFRESH_ACTION) {
            SpesifyWidgetUpdater.updateWidgetsForProviderAsync(
                context = context.applicationContext,
                providerClass = this::class.java,
                layoutMode = widgetLayoutMode,
                pendingResult = goAsync()
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        SpesifyWidgetUpdater.updateWidgetsAsync(
            context = context.applicationContext,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            layoutMode = widgetLayoutMode
        )
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        SpesifyWidgetUpdater.updateWidgetAsync(
            context = context.applicationContext,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
            layoutMode = widgetLayoutMode
        )
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            SpesifyWidgetUpdater.updateAllWidgetsAsync(context.applicationContext)
        }
    }
}

class SpesifyWideWidgetProvider : SpesifyWidgetProvider()

private enum class SpesifyWidgetLayoutMode {
    Auto,
    Wide
}

private enum class SpesifyWidgetResolvedLayout {
    Small,
    Wide
}

private object SpesifyWidgetUpdater {
    private const val WIDE_WIDGET_MIN_WIDTH_DP = 300
    private const val WIDE_WIDGET_MAX_MIN_HEIGHT_DP = 90

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAllWidgetsAsync(context: Context) {
        launchWidgetUpdate {
            updateAllWidgets(context.applicationContext)
        }
    }

    fun updateWidgetsForProviderAsync(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        layoutMode: SpesifyWidgetLayoutMode,
        pendingResult: android.content.BroadcastReceiver.PendingResult? = null
    ) {
        launchWidgetUpdate(pendingResult) {
            updateWidgetsForProvider(
                context = context.applicationContext,
                providerClass = providerClass,
                layoutMode = layoutMode
            )
        }
    }

    fun updateWidgetsAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        layoutMode: SpesifyWidgetLayoutMode
    ) {
        launchWidgetUpdate {
            updateWidgets(
                context = context.applicationContext,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds,
                layoutMode = layoutMode
            )
        }
    }

    fun updateWidgetAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutMode: SpesifyWidgetLayoutMode
    ) {
        launchWidgetUpdate {
            val summary = loadCurrentMonthWidgetSummary(context.applicationContext)
            updateWidget(
                context = context.applicationContext,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                layoutMode = layoutMode,
                summary = summary
            )
        }
    }

    private fun launchWidgetUpdate(
        pendingResult: android.content.BroadcastReceiver.PendingResult? = null,
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val defaultWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SpesifyWidgetProvider::class.java)
        )
        val wideWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SpesifyWideWidgetProvider::class.java)
        )
        if (defaultWidgetIds.isEmpty() && wideWidgetIds.isEmpty()) {
            return
        }

        val summary = loadCurrentMonthWidgetSummary(context)
        updateWidgets(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = defaultWidgetIds,
            layoutMode = SpesifyWidgetLayoutMode.Auto,
            summary = summary
        )
        updateWidgets(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = wideWidgetIds,
            layoutMode = SpesifyWidgetLayoutMode.Wide,
            summary = summary
        )
    }

    private suspend fun updateWidgetsForProvider(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        layoutMode: SpesifyWidgetLayoutMode
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass))
        updateWidgets(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = widgetIds,
            layoutMode = layoutMode
        )
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        layoutMode: SpesifyWidgetLayoutMode,
        summary: SpesifyWidgetSummary? = null
    ) {
        if (appWidgetIds.isEmpty()) {
            return
        }

        val resolvedSummary = summary ?: loadCurrentMonthWidgetSummary(context)
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                layoutMode = layoutMode,
                summary = resolvedSummary
            )
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutMode: SpesifyWidgetLayoutMode,
        summary: SpesifyWidgetSummary
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val resolvedLayout = resolveWidgetLayout(options, layoutMode)
        val layoutResId = when (resolvedLayout) {
            SpesifyWidgetResolvedLayout.Small -> R.layout.spesify_widget
            SpesifyWidgetResolvedLayout.Wide -> R.layout.spesify_widget_wide
        }
        val views = RemoteViews(context.packageName, layoutResId).apply {
            setTextViewText(
                R.id.widget_month_title,
                if (resolvedLayout == SpesifyWidgetResolvedLayout.Wide) {
                    summary.monthTitle
                } else {
                    summary.monthNameText
                }
            )
            setTextViewText(R.id.widget_expenses_amount, summary.expensesAmountText)
            setOnClickPendingIntent(R.id.widget_add_expense_button, voiceExpensePendingIntent(context))

            if (resolvedLayout != SpesifyWidgetResolvedLayout.Wide) {
                setTextViewText(R.id.widget_income_amount, summary.incomeAmountText)
                setViewVisibility(R.id.widget_income_row, View.GONE)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun resolveWidgetLayout(
        options: Bundle,
        layoutMode: SpesifyWidgetLayoutMode
    ): SpesifyWidgetResolvedLayout {
        if (layoutMode == SpesifyWidgetLayoutMode.Wide) {
            return SpesifyWidgetResolvedLayout.Wide
        }

        val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val isWideAndShort = maxWidthDp >= WIDE_WIDGET_MIN_WIDTH_DP &&
            minHeightDp in 1..WIDE_WIDGET_MAX_MIN_HEIGHT_DP

        return when {
            isWideAndShort -> SpesifyWidgetResolvedLayout.Wide
            else -> SpesifyWidgetResolvedLayout.Small
        }
    }

    private suspend fun loadCurrentMonthWidgetSummary(context: Context): SpesifyWidgetSummary {
        val today = LocalDate.now()
        val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        val monthTitle = "$monthName ${today.year}"
        val currencySymbol = context.getString(R.string.widget_currency_symbol)

        return runCatching {
            withContext(Dispatchers.Default) {
                val repository = GlobalContext.get().get<ExpenseRepository>()
                val monthSummary = repository.getWidgetMonthSummary(
                    year = today.year,
                    month = today.monthValue
                )
                SpesifyWidgetSummary(
                    monthTitle = monthTitle,
                    monthNameText = monthName,
                    expensesAmountText = formatAmount(monthSummary.expenseAmount, currencySymbol),
                    incomeAmountText = formatAmount(monthSummary.incomeAmount, currencySymbol)
                )
            }
        }.getOrElse {
            SpesifyWidgetSummary(
                monthTitle = monthTitle,
                monthNameText = monthName,
                expensesAmountText = formatAmount(0L, currencySymbol),
                incomeAmountText = formatAmount(0L, currencySymbol)
            )
        }
    }

    private fun voiceExpensePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_VOICE_EXPENSE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

private data class SpesifyWidgetSummary(
    val monthTitle: String,
    val monthNameText: String,
    val expensesAmountText: String,
    val incomeAmountText: String
)
