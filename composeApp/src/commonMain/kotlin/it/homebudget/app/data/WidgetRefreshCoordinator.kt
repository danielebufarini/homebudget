package it.homebudget.app.data

import it.homebudget.app.widget.HomeBudgetWidgetRefresh

class WidgetRefreshCoordinator {
    fun requestRefresh() {
        HomeBudgetWidgetRefresh.requestRefresh()
    }
}
