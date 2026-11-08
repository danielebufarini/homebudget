import UIKit

final class AppOrientationController: NSObject, UIApplicationDelegate {
    private static var supportedOrientations: UIInterfaceOrientationMask = .portrait
    private static weak var dashboardHost: UIViewController?
    private static weak var requestedScene: UIWindowScene?
    private static var requestedOrientations: UIInterfaceOrientationMask?

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        Self.supportedOrientations
    }

    @MainActor
    static func setDashboardBalanceExpanded(_ expanded: Bool) {
        guard let host = dashboardHost as? SafeAreaContainerViewController else { return }
        host.dashboardBalanceExpanded = expanded
        let orientations: UIInterfaceOrientationMask = expanded ? .landscape : .portrait
        guard supportedOrientations != orientations else { return }
        supportedOrientations = orientations
        updateDashboardGeometry()
    }

    @MainActor
    static func attachDashboard(_ host: SafeAreaContainerViewController) {
        dashboardHost = host
        supportedOrientations = host.dashboardBalanceExpanded ? .landscape : .portrait
        updateDashboardGeometry()
    }

    @MainActor
    static func detachDashboard(_ host: UIViewController) {
        guard dashboardHost === host else { return }
        supportedOrientations = .portrait
        updateDashboardGeometry()
        dashboardHost = nil
    }

    @MainActor
    private static func updateDashboardGeometry() {
        guard let host = dashboardHost, let scene = host.view.window?.windowScene else { return }
        host.setNeedsUpdateOfSupportedInterfaceOrientations()
        host.view.window?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        guard requestedScene !== scene || requestedOrientations != supportedOrientations else { return }
        requestedScene = scene
        requestedOrientations = supportedOrientations
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: supportedOrientations))
    }
}
