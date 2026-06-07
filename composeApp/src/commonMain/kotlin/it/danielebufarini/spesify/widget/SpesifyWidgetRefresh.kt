package it.danielebufarini.spesify.widget

const val SPESIFY_WIDGET_REFRESH_ACTION = "it.danielebufarini.spesify.action.REFRESH_SPESIFY_WIDGETS"

expect object SpesifyWidgetRefresh {
    fun requestRefresh()
}
