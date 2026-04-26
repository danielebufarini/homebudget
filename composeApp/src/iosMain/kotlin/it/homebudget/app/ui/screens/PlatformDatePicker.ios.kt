@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIViewController
import kotlin.time.Clock

actual class PlatformDatePicker(
    private val showPicker: (Long?, (Long) -> Unit) -> Unit
) {
    actual fun show(initialDateMillis: Long?, onDateSelected: (Long) -> Unit) {
        showPicker(initialDateMillis, onDateSelected)
    }
}

@Composable
actual fun rememberPlatformDatePicker(): PlatformDatePicker {
    return remember {
        PlatformDatePicker { initialDateMillis, onDateSelected ->
            val referenceOffsetSeconds = 978307200.0
            val presenter = topViewController(UIApplication.sharedApplication.keyWindow?.rootViewController)
                ?: return@PlatformDatePicker

            val alert = UIAlertController.alertControllerWithTitle(
                "Select Date",
                "\n\n\n\n\n\n\n\n\n",
                UIAlertControllerStyleAlert
            )

            val picker = UIDatePicker(frame = CGRectMake(8.0, 48.0, 250.0, 160.0)).apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeDate
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                date = NSDate(
                    timeIntervalSinceReferenceDate =
                        ((initialDateMillis ?: Clock.System.now().toEpochMilliseconds()).toDouble() / 1000.0) - referenceOffsetSeconds
                )
            }

            alert.view.addSubview(picker)
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    "Cancel",
                    UIAlertActionStyleCancel,
                    null
                )
            )
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    "OK",
                    UIAlertActionStyleDefault
                ) { _ ->
                    onDateSelected(((picker.date.timeIntervalSinceReferenceDate + referenceOffsetSeconds) * 1000.0).toLong())
                }
            )

            presenter.presentViewController(alert, true, completion = null)
        }
    }
}

private fun topViewController(controller: UIViewController?): UIViewController? {
    return when {
        controller == null -> null
        controller.presentedViewController != null -> topViewController(controller.presentedViewController)
        else -> controller
    }
}
