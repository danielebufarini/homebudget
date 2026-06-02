@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.IdGenerator
import platform.Foundation.NSNotificationCenter

private const val IOS_NATIVE_DATE_PICKER_NOTIFICATION = "HomeBudget.IosNativeDatePicker.Request"

private val pendingIosDatePickerCallbacks = mutableMapOf<String, (Long) -> Unit>()

internal fun requestIosNativeDatePicker(
    initialDateMillis: Long?,
    onDateSelected: (Long) -> Unit
) {
    val requestId = IdGenerator.newId("ios-date-picker")
    pendingIosDatePickerCallbacks[requestId] = onDateSelected

    val userInfo = buildMap<Any?, Any?> {
        put("requestId", requestId)
        initialDateMillis?.let { put("initialDateMillis", it.toString()) }
    }

    NSNotificationCenter.defaultCenter.postNotificationName(
        IOS_NATIVE_DATE_PICKER_NOTIFICATION,
        null,
        userInfo
    )
}

fun iosNativeDatePickerNotificationName(): String = IOS_NATIVE_DATE_PICKER_NOTIFICATION

fun completeIosNativeDatePickerRequest(
    requestId: String,
    selectedDateMillis: Long
) {
    pendingIosDatePickerCallbacks.remove(requestId)?.invoke(selectedDateMillis)
}

fun cancelIosNativeDatePickerRequest(requestId: String) {
    pendingIosDatePickerCallbacks.remove(requestId)
}
