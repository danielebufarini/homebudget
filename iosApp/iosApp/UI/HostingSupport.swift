import SwiftUI
import UIKit

@MainActor
func appDismissKeyboard() {
    for scene in UIApplication.shared.connectedScenes {
        guard let windowScene = scene as? UIWindowScene else {
            continue
        }
        for window in windowScene.windows where window.isKeyWindow {
            window.endEditing(true)
        }
    }
}

struct KotlinViewControllerHost: UIViewControllerRepresentable {
    let constrainToSafeArea: Bool
    let makeViewController: () -> UIViewController

    init(
        constrainToSafeArea: Bool = true,
        makeViewController: @escaping () -> UIViewController
    ) {
        self.constrainToSafeArea = constrainToSafeArea
        self.makeViewController = makeViewController
    }

    func makeUIViewController(context: Context) -> UIViewController {
        SafeAreaContainerViewController(
            contentViewController: makeViewController(),
            constrainToSafeArea: constrainToSafeArea
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

final class SafeAreaContainerViewController: UIViewController {
    private let contentViewController: UIViewController
    private let constrainToSafeArea: Bool

    init(
        contentViewController: UIViewController,
        constrainToSafeArea: Bool
    ) {
        self.contentViewController = contentViewController
        self.constrainToSafeArea = constrainToSafeArea
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .clear
        view.isOpaque = false

        addChild(contentViewController)
        contentViewController.view.translatesAutoresizingMaskIntoConstraints = false
        contentViewController.view.backgroundColor = .clear
        contentViewController.view.isOpaque = false
        view.addSubview(contentViewController.view)

        if constrainToSafeArea {
            let guide = view.safeAreaLayoutGuide
            NSLayoutConstraint.activate([
                contentViewController.view.topAnchor.constraint(equalTo: guide.topAnchor),
                contentViewController.view.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
                contentViewController.view.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
                contentViewController.view.bottomAnchor.constraint(equalTo: guide.bottomAnchor)
            ])
        } else {
            NSLayoutConstraint.activate([
                contentViewController.view.topAnchor.constraint(equalTo: view.topAnchor),
                contentViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                contentViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                contentViewController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            ])
        }

        contentViewController.didMove(toParent: self)
    }
}

private struct InteractivePopGestureRestorer: UIViewControllerRepresentable {
    func makeCoordinator() -> InteractivePopGestureCoordinator {
        InteractivePopGestureCoordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let viewController = InteractivePopGestureRestoringViewController()
        viewController.coordinator = context.coordinator
        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        if let viewController = uiViewController as? InteractivePopGestureRestoringViewController {
            viewController.coordinator = context.coordinator
        }
        context.coordinator.requestInstall(from: uiViewController)
    }
}

private final class InteractivePopGestureRestoringViewController: UIViewController {
    weak var coordinator: InteractivePopGestureCoordinator?

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        coordinator?.requestInstall(from: self)
    }

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

private final class InteractivePopGestureCoordinator: NSObject, UIGestureRecognizerDelegate {
    private weak var navigationController: UINavigationController?

    func requestInstall(from viewController: UIViewController) {
        install(from: viewController)

        // SwiftUI can reassign the interactive pop gesture delegate after layout/navigation
        // updates, especially when navigationBarBackButtonHidden(true) is used. Re-apply
        // the delegate a few times on the next run-loop ticks so the hidden visible back
        // button does not disable the native edge-swipe gesture.
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
        guard let navigationController = resolveNavigationController(from: viewController),
              let gesture = navigationController.interactivePopGestureRecognizer else {
            return
        }

        self.navigationController = navigationController
        gesture.isEnabled = true
        gesture.delegate = self
    }

    private func resolveNavigationController(from viewController: UIViewController) -> UINavigationController? {
        if let navigationController = viewController.navigationController {
            return navigationController
        }

        var parent = viewController.parent
        while let current = parent {
            if let navigationController = current as? UINavigationController {
                return navigationController
            }
            if let navigationController = current.navigationController {
                return navigationController
            }
            parent = current.parent
        }

        if let window = viewController.view.window,
           let navigationController = findNavigationController(
            in: window.rootViewController,
            containing: viewController.view,
            matching: window
           ) {
            return navigationController
        }

        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else {
                continue
            }
            for window in windowScene.windows where window.isKeyWindow {
                if let navigationController = findNavigationController(
                    in: window.rootViewController,
                    containing: nil,
                    matching: window
                ) {
                    return navigationController
                }
            }
        }

        return nil
    }

    private func findNavigationController(
        in root: UIViewController?,
        containing view: UIView?,
        matching window: UIWindow
    ) -> UINavigationController? {
        guard let root else {
            return nil
        }

        if let navigationController = root as? UINavigationController,
           navigationController.view.window == window,
           view == nil || view?.isDescendant(of: navigationController.view) == true {
            return navigationController
        }

        if let presented = root.presentedViewController,
           let navigationController = findNavigationController(in: presented, containing: view, matching: window) {
            return navigationController
        }

        for child in root.children {
            if let navigationController = findNavigationController(in: child, containing: view, matching: window) {
                return navigationController
            }
        }

        return nil
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let navigationController else {
            return true
        }

        return navigationController.viewControllers.count > 1 && navigationController.transitionCoordinator == nil
    }
}

private struct KeyboardDismissOnTapInstaller: UIViewControllerRepresentable {
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

private final class KeyboardDismissOnTapCoordinator: NSObject, UIGestureRecognizerDelegate {
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
        guard let touchedView = touch.view else {
            return true
        }

        return !touchedView.isDescendantOfTextInput
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }
}

private extension UIView {
    var isDescendantOfTextInput: Bool {
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
}

extension View {
    func restoresInteractivePopGesture() -> some View {
        background(
            InteractivePopGestureRestorer()
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
        )
    }

    func dismissesKeyboardOnTap() -> some View {
        background(
            KeyboardDismissOnTapInstaller()
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
        )
    }
}
