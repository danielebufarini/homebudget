import UIKit

final class AppOrientationController: NSObject, UIApplicationDelegate {
    private static var supportedOrientations: UIInterfaceOrientationMask = .portrait

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        Self.supportedOrientations
    }

    @MainActor
    static func setDashboardBalanceExpanded(_ expanded: Bool) {
        supportedOrientations = expanded ? .landscape : .portrait

        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first else {
            return
        }

        windowScene.windows.first?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        windowScene.requestGeometryUpdate(
            .iOS(interfaceOrientations: supportedOrientations)
        )
    }
}
