import UIKit
import XCTest

final class ScreenshotSharingTests: XCTestCase {
    @MainActor
    func testInMemoryScreenshotCanBeSharedWithSpesify() throws {
        try checkSharing(openMore: false)
    }

    @MainActor
    func testInMemoryScreenshotCanBeSharedWithSpesifyFromMore() throws {
        try checkSharing(openMore: true)
    }

    @MainActor
    private func checkSharing(openMore: Bool) throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        app.terminate()
        let runner = XCUIApplication(bundleIdentifier: try XCTUnwrap(Bundle.main.bundleIdentifier))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let window = UIWindow(windowScene: scene)
        let host = UIViewController()
        host.view.backgroundColor = .systemBackground
        let share = UIButton(type: .system)
        share.setTitle("Share screenshot fixture", for: .normal)
        share.frame = CGRect(x: 30, y: 160, width: 300, height: 60)
        share.addAction(UIAction { [weak host] _ in
            let image = UIGraphicsImageRenderer(size: CGSize(width: 320, height: 120)).image { context in
                UIColor.white.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 320, height: 120))
                ("Payment €12.34\nCoffee shop" as NSString).draw(
                    at: CGPoint(x: 16, y: 20),
                    withAttributes: [.font: UIFont.systemFont(ofSize: 24), .foregroundColor: UIColor.black]
                )
            }
            host?.present(UIActivityViewController(activityItems: [image], applicationActivities: nil), animated: true)
        }, for: .touchUpInside)
        host.view.addSubview(share)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        runner.activate()
        let shareButton = runner.buttons["Share screenshot fixture"]
        XCTAssertTrue(shareButton.waitForExistence(timeout: 10), runner.debugDescription)
        shareButton.tap()
        let activities = runner.collectionViews["activityCollectionView"]
        XCTAssertTrue(activities.waitForExistence(timeout: 10), runner.debugDescription)
        var spesify = activities.cells["Spesify"].firstMatch
        if openMore || !spesify.exists {
            let more = activities.cells["More"].firstMatch
            XCTAssertTrue(more.waitForExistence(timeout: 10), runner.debugDescription)
            more.tap()
            let hierarchy = XCTAttachment(string: runner.debugDescription)
            hierarchy.name = "Apps list after More"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
            let apps = runner.otherElements["ShareSheet.EditAppsView"]
            XCTAssertTrue(apps.waitForExistence(timeout: 10), runner.debugDescription)
            // The Apps list labels the text inside each row, not the cell. A global
            // cells["Spesify"] query instead matches the obscured share-sheet icon.
            spesify = apps.tables.cells.containing(.staticText, identifier: "Spesify").firstMatch
        }
        XCTAssertTrue(spesify.waitForExistence(timeout: 10), runner.debugDescription)
        XCTAssertTrue(spesify.isHittable, runner.debugDescription)
        spesify.tap()
        let received = runner.staticTexts["Screenshot received. Open Spesify to review the expense."]
        XCTAssertTrue(received.waitForExistence(timeout: 15), runner.debugDescription)
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "Screenshot received directly from the system share sheet"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        runner.buttons["Done"].tap()
        XCTAssertTrue(received.waitForNonExistence(timeout: 10), runner.debugDescription)

        // Consume this test's queued image and dismiss its unsaved editor. Otherwise
        // the next UI test's launch opens this fixture instead of the dashboard.
        app.launch()
        defer { app.terminate() }
        let editor = app.staticTexts.matching(NSPredicate(
            format: "label IN %@", argumentArray: [["Add Expense", "Aggiungi spesa"]]
        )).firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 15), app.debugDescription)
        let cancel = app.buttons.matching(NSPredicate(
            format: "label IN %@", argumentArray: [["Cancel", "Annulla"]]
        )).firstMatch
        XCTAssertTrue(cancel.isHittable, app.debugDescription)
        cancel.tap()
        XCTAssertTrue(editor.waitForNonExistence(timeout: 10), app.debugDescription)
    }
}
