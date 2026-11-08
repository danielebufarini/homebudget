import XCTest

final class GroupingCapsuleTests: XCTestCase {
    @MainActor
    func testExclusiveGroupingInExpensesIncomeAndSearch() {
        checkGrouping(largeText: false)
    }

    @MainActor
    func testGroupingWithLargeText() {
        checkGrouping(largeText: true)
    }

    @MainActor
    private func checkGrouping(largeText: Bool) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if largeText {
            app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        }
        app.launch()
        defer { app.terminate() }
        let expenses = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Expenses")).firstMatch
        XCTAssertTrue(expenses.waitForExistence(timeout: 15), app.debugDescription)
        expenses.tap()
        checkCapsules(app)
        app.buttons["Income"].tap()
        checkCapsules(app)
        app.terminate()
        app.launch()
        let search = app.textViews["Search transactions..."]
        XCTAssertTrue(search.waitForExistence(timeout: 15))
        search.tap()
        search.typeText("capsule verification")
        app.buttons["Search transactions"].tap()
        XCTAssertTrue(app.staticTexts["Search Results"].waitForExistence(timeout: 10))
        checkCapsules(app)
    }

    @MainActor
    private func checkCapsules(_ app: XCUIApplication) {
        let category = app.buttons["grouping_category"]
        let date = app.buttons["grouping_date"]
        XCTAssertTrue(category.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(date.exists)
        XCTAssertTrue(category.isHittable)
        XCTAssertGreaterThanOrEqual(category.frame.height, 44)
        XCTAssertGreaterThanOrEqual(date.frame.height, 44)
        XCTAssertGreaterThan(date.frame.minX, category.frame.maxX)
        date.tap()
        XCTAssertTrue(date.isSelected)
        XCTAssertFalse(category.isSelected)
        category.tap()
        XCTAssertTrue(category.isSelected)
        XCTAssertFalse(date.isSelected)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "grouping capsules"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
