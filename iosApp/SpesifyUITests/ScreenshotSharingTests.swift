import UIKit
import XCTest

final class ScreenshotSharingTests: XCTestCase {
    @MainActor
    func testInMemoryScreenshotCanBeSharedWithSpesify() throws {
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
        XCTAssertTrue(runner.collectionViews["activityCollectionView"].waitForExistence(timeout: 10), runner.debugDescription)
        let spesify = runner.cells["Spesify"].firstMatch
        if !spesify.exists {
            let more = runner.cells["More"].firstMatch
            XCTAssertTrue(more.waitForExistence(timeout: 10), runner.debugDescription)
            more.tap()
        }
        XCTAssertTrue(spesify.waitForExistence(timeout: 10), runner.debugDescription)
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
