import XCTest

final class DashboardAnimationTests: XCTestCase {
    @MainActor
    func testBalanceExpansionRotationAndRepeatedClose() {
        let app = launch(card: "Balance")
        defer { app.terminate() }
        for index in 0..<3 {
            let expand = app.buttons["Expand cumulative balance chart"]
            XCTAssertTrue(expand.waitForExistence(timeout: 15))
            expand.tap()
            waitForOrientation(app, landscape: true)
            let close = app.buttons["Collapse cumulative balance chart"]
            XCTAssertTrue(close.waitForExistence(timeout: 10))
            XCTAssertTrue(close.isHittable)
            attach(app, name: "balance landscape \(index)")
            close.tap()
            waitForOrientation(app, landscape: false)
            XCTAssertTrue(expand.waitForExistence(timeout: 10))
        }
        attach(app, name: "balance restored portrait")
    }

    @MainActor
    func testCategoryAndRecentTitleSwipeAndPageRestoration() {
        for (card, title, action) in [
            ("ExpensesByCategory", "Expenses by category", "top categories"),
            ("RecentTransactions", "Recent transactions", "recent transactions")
        ] {
            let app = launch(card: card)
            let heading = app.staticTexts[title]
            XCTAssertTrue(heading.waitForExistence(timeout: 15), app.debugDescription)
            let start = heading.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            for offset in [CGVector(dx: 0, dy: -20), CGVector(dx: 100, dy: -40), CGVector(dx: 0, dy: 100)] {
                start.press(forDuration: 0.05, thenDragTo: start.withOffset(offset))
                XCTAssertFalse(app.buttons["Collapse \(action)"].exists)
            }
            start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: -120)))
            let close = app.buttons["Collapse \(action)"]
            XCTAssertTrue(close.waitForExistence(timeout: 10), app.debugDescription)
            attach(app, name: "\(card) expanded by title swipe")
            close.tap()
            let expand = app.buttons["Expand \(action)"]
            XCTAssertTrue(expand.waitForExistence(timeout: 10))
            expand.tap()
            XCTAssertTrue(close.waitForExistence(timeout: 10))
            close.tap()
            XCTAssertTrue(heading.waitForExistence(timeout: 10))
            attach(app, name: "\(card) restored page")
            app.terminate()
        }
    }

    @MainActor
    private func launch(card: String) -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-pinned_dashboard_card", card]
        app.launch()
        return app
    }

    @MainActor
    private func waitForOrientation(_ app: XCUIApplication, landscape: Bool) {
        let expected = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            let frame = app.windows.firstMatch.frame
            return landscape ? frame.width > frame.height : frame.height > frame.width
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [expected], timeout: 10), .completed)
    }

    @MainActor
    private func attach(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
