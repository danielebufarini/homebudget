package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

actual class PlatformOptionPicker(
    private val showPicker: (String, List<String>, String?, (String) -> Unit) -> Unit
) {
    actual fun show(
        title: String,
        options: List<String>,
        selectedOption: String?,
        onOptionSelected: (String) -> Unit
    ) {
        showPicker(title, options, selectedOption, onOptionSelected)
    }
}

@Composable
actual fun rememberPlatformOptionPicker(): PlatformOptionPicker {
    return remember {
        PlatformOptionPicker { title, options, _, onOptionSelected ->
            if (options.isEmpty()) return@PlatformOptionPicker

            val presenter = optionPickerTopViewController(optionPickerKeyWindow()?.rootViewController)
                ?: return@PlatformOptionPicker

            val alert = UIAlertController.alertControllerWithTitle(
                title,
                null,
                UIAlertControllerStyleAlert
            )

            options.forEach { option ->
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        option,
                        UIAlertActionStyleDefault
                    ) { _ ->
                        onOptionSelected(option)
                    }
                )
            }

            alert.addAction(
                UIAlertAction.actionWithTitle(
                    "Cancel",
                    UIAlertActionStyleCancel,
                    null
                )
            )

            presenter.presentViewController(alert, true, completion = null)
        }
    }
}

private fun optionPickerKeyWindow(): UIWindow? {
    return UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
}

private fun optionPickerTopViewController(controller: UIViewController?): UIViewController? {
    return when {
        controller == null -> null
        controller.presentedViewController != null -> optionPickerTopViewController(controller.presentedViewController)
        else -> controller
    }
}
