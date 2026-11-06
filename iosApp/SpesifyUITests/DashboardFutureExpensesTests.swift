import XCTest

final class DashboardFutureExpensesTests: XCTestCase {
    @MainActor
    func testPreferenceInCompactAndExpandedViewsAndAfterRelaunchInEnglish() {
        checkPreference(language: "en", locale: "en_US", label: "Hide future expenses",
                        expand: "Expand recent transactions", collapse: "Collapse recent transactions",
                        expenses: "Expenses")
    }

    @MainActor
    func testPreferenceInCompactAndExpandedViewsAndAfterRelaunchInItalian() {
        checkPreference(language: "it", locale: "it_IT", label: "Nascondi spese future",
                        expand: "Espandi movimenti recenti", collapse: "Comprimi movimenti recenti",
                        expenses: "Spese")
    }

    @MainActor
    private func checkPreference(language: String, locale: String, label: String,
                                 expand: String, collapse: String, expenses: String) {
        continueAfterFailure = false
        let app = XCUIApplication()
        // Open the card using the existing pin preference; the new preference is read from storage.
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale,
                                "-pinned_dashboard_card", "RecentTransactions"]
        app.launch()
        defer { app.terminate() }
        let toggle = app.switches["dashboard_hide_future_expenses"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 15), app.debugDescription)
        XCTAssertEqual(toggle.label, label)
        XCTAssertTrue(toggle.isHittable)
        // Compose exposes ToggleableState through UIAccessibilityTraitSelected on iOS.
        let original = toggle.isSelected
        XCTAssertEqual(toggle.elementType, .switch)
        toggle.tap()
        let changed = !original
        assertValue(toggle, equals: changed, app: app)
        attach(app, name: "compact toggle")

        app.buttons[expand].tap()
        assertValue(toggle, equals: changed, app: app)
        XCTAssertTrue(toggle.isHittable)
        toggle.tap()
        assertValue(toggle, equals: original, app: app)
        toggle.tap()
        assertValue(toggle, equals: changed, app: app)
        app.buttons[expenses].tap()
        assertValue(toggle, equals: changed, app: app)
        attach(app, name: "expanded toggle after filter change")
        app.buttons[collapse].tap()
        assertValue(toggle, equals: changed, app: app)

        app.terminate()
        app.launch()
        assertValue(toggle, equals: changed, app: app)
        XCUIDevice.shared.press(.home)
        app.activate()
        assertValue(toggle, equals: changed, app: app)
        toggle.tap()
        assertValue(toggle, equals: original, app: app)
        app.terminate()
        app.launch()
        assertValue(toggle, equals: original, app: app)
        attach(app, name: "restored preference after relaunch")
    }

    @MainActor
    private func assertValue(_ toggle: XCUIElement, equals value: Bool, app: XCUIApplication) {
        let predicate = NSPredicate(format: "exists == true AND selected == %@", NSNumber(value: value))
        let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: toggle)], timeout: 10)
        XCTAssertEqual(result, .completed, app.debugDescription)
    }

    @MainActor
    private func attach(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
