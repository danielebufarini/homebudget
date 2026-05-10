package it.homebudget.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.widget.HOME_BUDGET_WIDGET_REFRESH_ACTION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

open class HomeBudgetWidgetProvider : AppWidgetProvider() {

    private val widgetLayoutMode: HomeBudgetWidgetLayoutMode
        get() = if (this is HomeBudgetWideWidgetProvider) {
            HomeBudgetWidgetLayoutMode.Wide
        } else {
            HomeBudgetWidgetLayoutMode.Auto
        }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == HOME_BUDGET_WIDGET_REFRESH_ACTION) {
            HomeBudgetWidgetUpdater.updateWidgetsForProvider(
                context = context,
                providerClass = this::class.java,
                layoutMode = widgetLayoutMode
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            HomeBudgetWidgetUpdater.updateWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                layoutMode = widgetLayoutMode
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        HomeBudgetWidgetUpdater.updateWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
            layoutMode = widgetLayoutMode
        )
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            HomeBudgetWidgetUpdater.updateAllWidgets(context)
        }
    }
}

class HomeBudgetWideWidgetProvider : HomeBudgetWidgetProvider()

private enum class HomeBudgetWidgetLayoutMode {
    Auto,
    Wide
}

private enum class HomeBudgetWidgetResolvedLayout {
    Small,
    Medium,
    Wide
}

private object HomeBudgetWidgetUpdater {
    private const val SMALL_WIDGET_MAX_WIDTH_DP = 120
    private const val SMALL_WIDGET_MAX_HEIGHT_DP = 120
    private const val WIDE_WIDGET_MIN_WIDTH_DP = 300
    private const val WIDE_WIDGET_MAX_MIN_HEIGHT_DP = 90

    fun updateAllWidgets(context: Context) {
        updateWidgetsForProvider(
            context = context,
            providerClass = HomeBudgetWidgetProvider::class.java,
            layoutMode = HomeBudgetWidgetLayoutMode.Auto
        )
        updateWidgetsForProvider(
            context = context,
            providerClass = HomeBudgetWideWidgetProvider::class.java,
            layoutMode = HomeBudgetWidgetLayoutMode.Wide
        )
    }

    fun updateWidgetsForProvider(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        layoutMode: HomeBudgetWidgetLayoutMode
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass))
        widgetIds.forEach { appWidgetId ->
            updateWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                layoutMode = layoutMode
            )
        }
    }

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutMode: HomeBudgetWidgetLayoutMode
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val resolvedLayout = resolveWidgetLayout(options, layoutMode)
        val layoutResId = when (resolvedLayout) {
            HomeBudgetWidgetResolvedLayout.Small -> R.layout.home_budget_widget
            HomeBudgetWidgetResolvedLayout.Medium -> R.layout.home_budget_widget_medium
            HomeBudgetWidgetResolvedLayout.Wide -> R.layout.home_budget_widget_wide
        }
        val summary = loadCurrentMonthWidgetSummary(context)

        val views = RemoteViews(context.packageName, layoutResId).apply {
            setTextViewText(
                R.id.widget_month_title,
                if (resolvedLayout == HomeBudgetWidgetResolvedLayout.Wide) {
                    summary.monthNameText
                } else {
                    summary.monthTitle
                }
            )
            setTextViewText(R.id.widget_expenses_amount, summary.expensesAmountText)
            setOnClickPendingIntent(R.id.widget_add_expense_button, voiceExpensePendingIntent(context))

            if (resolvedLayout != HomeBudgetWidgetResolvedLayout.Wide) {
                setTextViewText(R.id.widget_income_amount, summary.incomeAmountText)
                setViewVisibility(R.id.widget_income_row, View.GONE)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun resolveWidgetLayout(
        options: Bundle,
        layoutMode: HomeBudgetWidgetLayoutMode
    ): HomeBudgetWidgetResolvedLayout {
        if (layoutMode == HomeBudgetWidgetLayoutMode.Wide) {
            return HomeBudgetWidgetResolvedLayout.Wide
        }

        val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val isWideAndShort = maxWidthDp >= WIDE_WIDGET_MIN_WIDTH_DP &&
            minHeightDp in 1..WIDE_WIDGET_MAX_MIN_HEIGHT_DP

        return when {
            isWideAndShort -> HomeBudgetWidgetResolvedLayout.Wide
            maxWidthDp in 1 until SMALL_WIDGET_MAX_WIDTH_DP &&
                maxHeightDp in 1 until SMALL_WIDGET_MAX_HEIGHT_DP -> {
                HomeBudgetWidgetResolvedLayout.Small
            }
            else -> HomeBudgetWidgetResolvedLayout.Medium
        }
    }

    private fun loadCurrentMonthWidgetSummary(context: Context): HomeBudgetWidgetSummary {
        val today = LocalDate.now()
        val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        val monthTitle = "$monthName ${today.year}"
        val currencySymbol = context.getString(R.string.widget_currency_symbol)

        return runCatching {
            val repository = GlobalContext.get().get<ExpenseRepository>()
            val monthSummary = runBlocking {
                repository.getDashboardMonthSummary(
                    year = today.year,
                    month = today.monthValue
                ).first()
            }
            HomeBudgetWidgetSummary(
                monthTitle = monthTitle,
                monthNameText = monthName,
                expensesAmountText = formatAmount(monthSummary.totalAmount, currencySymbol),
                incomeAmountText = formatAmount(monthSummary.incomeAmount, currencySymbol)
            )
        }.getOrElse {
            HomeBudgetWidgetSummary(
                monthTitle = monthTitle,
                monthNameText = monthName,
                expensesAmountText = formatAmount(ZERO, currencySymbol),
                incomeAmountText = formatAmount(ZERO, currencySymbol)
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

private data class HomeBudgetWidgetSummary(
    val monthTitle: String,
    val monthNameText: String,
    val expensesAmountText: String,
    val incomeAmountText: String
)
