package it.danielebufarini.homebudget.widget

const val HOME_BUDGET_WIDGET_REFRESH_ACTION = "it.danielebufarini.homebudget.action.REFRESH_HOME_BUDGET_WIDGETS"

expect object HomeBudgetWidgetRefresh {
    fun requestRefresh()
}
