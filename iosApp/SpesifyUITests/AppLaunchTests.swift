import XCTest

final class AppLaunchTests: XCTestCase {
    @MainActor
    func testDashboardLaunchesInEnglish() {
        assertDashboardLaunches(language: "en", locale: "en_US", addLabel: "Add transaction")
    }

    @MainActor
    func testDashboardLaunchesInItalian() {
        assertDashboardLaunches(language: "it", locale: "it_IT", addLabel: "Aggiungi transazione")
    }

    @MainActor
    private func assertDashboardLaunches(language: String, locale: String, addLabel: String) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        defer { app.terminate() }

        let addTransaction = app.buttons[addLabel]
        XCTAssertTrue(addTransaction.waitForExistence(timeout: 15))
        XCTAssertTrue(addTransaction.isHittable)
        XCTAssertEqual(app.state, .runningForeground)
    }
}
