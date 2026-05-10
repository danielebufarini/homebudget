package it.homebudget.app.widget

const val HOME_BUDGET_WIDGET_REFRESH_ACTION = "it.homebudget.app.action.REFRESH_HOME_BUDGET_WIDGETS"

expect object HomeBudgetWidgetRefresh {
    fun requestRefresh()
}
