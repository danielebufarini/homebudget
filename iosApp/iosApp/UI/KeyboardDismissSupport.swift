import SwiftUI
import UIKit

struct KeyboardDismissOnTapInstaller: UIViewControllerRepresentable {
    func makeCoordinator() -> KeyboardDismissOnTapCoordinator {
        KeyboardDismissOnTapCoordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let viewController = KeyboardDismissInstallingViewController()
        viewController.coordinator = context.coordinator
        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        if let viewController = uiViewController as? KeyboardDismissInstallingViewController {
            viewController.coordinator = context.coordinator
        }
        context.coordinator.requestInstall(from: uiViewController)
    }
}

private final class KeyboardDismissInstallingViewController: UIViewController {
    weak var coordinator: KeyboardDismissOnTapCoordinator?

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        coordinator?.requestInstall(from: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        coordinator?.requestInstall(from: self)
    }

    override func didMove(toParent parent: UIViewController?) {
        super.didMove(toParent: parent)
        coordinator?.requestInstall(from: self)
    }
}

final class KeyboardDismissOnTapCoordinator: NSObject, UIGestureRecognizerDelegate {
    private weak var installedWindow: UIWindow?
    private weak var gestureRecognizer: UITapGestureRecognizer?

    func requestInstall(from viewController: UIViewController) {
        install(from: viewController)

        [0.05, 0.15, 0.35].forEach { delay in
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self, weak viewController] in
                guard let self, let viewController else {
                    return
                }
                self.install(from: viewController)
            }
        }
    }

    private func install(from viewController: UIViewController) {
        guard let window = viewController.view.window ?? keyWindow else {
            return
        }

        if installedWindow === window, gestureRecognizer != nil {
            return
        }

        if let previousGesture = gestureRecognizer {
            installedWindow?.removeGestureRecognizer(previousGesture)
        }

        let gesture = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard(_:)))
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        window.addGestureRecognizer(gesture)

        installedWindow = window
        gestureRecognizer = gesture
    }

    private var keyWindow: UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
    }

    @objc private func dismissKeyboard(_ gestureRecognizer: UITapGestureRecognizer) {
        guard gestureRecognizer.state == .ended else {
            return
        }

        installedWindow?.endEditing(true)
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldReceive touch: UITouch
    ) -> Bool {
        guard let firstResponder = installedWindow?.currentFirstResponder else {
            return false
        }
        guard let touchedView = touch.view else {
            return true
        }
        if firstResponder.isDescendantOfComposeBackedView || touchedView.isDescendantOfComposeBackedView {
            return false
        }

        return !touchedView.isDescendantOfKeyboardProtectedView
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }
}

private extension UIView {
    var currentFirstResponder: UIView? {
        if isFirstResponder {
            return self
        }

        for subview in subviews {
            if let responder = subview.currentFirstResponder {
                return responder
            }
        }

        return nil
    }

    var isDescendantOfKeyboardProtectedView: Bool {
        var view: UIView? = self
        while let current = view {
            if current is UITextField || current is UITextView || current is UISearchTextField {
                return true
            }
            if current.isSystemTextInputRelatedView {
                return true
            }
            view = current.superview
        }
        return false
    }

    private var isSystemTextInputRelatedView: Bool {
        let className = NSStringFromClass(type(of: self))
        return className.contains("TextField") ||
            className.contains("TextView") ||
            className.contains("TextInput") ||
            className.contains("InputSet") ||
            className.contains("InputAssistant") ||
            className.contains("Keyboard")
    }

    var isDescendantOfComposeBackedView: Bool {
        var view: UIView? = self
        while let current = view {
            if current.isComposeBackedView {
                return true
            }
            view = current.superview
        }
        return false
    }

    private var isComposeBackedView: Bool {
        let className = NSStringFromClass(type(of: self))
        return className.contains("Compose") ||
            className.contains("Skiko")
    }
}
