import XCTest

final class KeyboardVisibilityTests: XCTestCase {
    @MainActor
    func testExpenseDescriptionAboveKeyboard() {
        checkDescription(income: false)
    }

    @MainActor
    func testIncomeDescriptionAboveKeyboard() {
        checkDescription(income: true)
    }

    @MainActor
    private func checkDescription(income: Bool) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let add = app.buttons["Add transaction"]
        XCTAssertTrue(add.waitForExistence(timeout: 15))
        add.tap()
        if income { app.buttons["Income"].tap() }
        let field = app.descendants(matching: .any).matching(NSPredicate(
            format: "elementType == %d OR elementType == %d",
            XCUIElement.ElementType.textField.rawValue, XCUIElement.ElementType.textView.rawValue
        )).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), app.debugDescription)
        field.tap()
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        field.typeText("Description " + String(repeating: "long text ", count: 25))
        assertVisible(field, above: keyboard, app: app)
        app.staticTexts["Description"].tap()
        XCTAssertTrue(keyboard.waitForNonExistence(timeout: 10))
        field.tap()
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        field.typeText("reopened")
        assertVisible(field, above: keyboard, app: app)
    }

    @MainActor
    private func assertVisible(_ field: XCUIElement, above keyboard: XCUIElement, app: XCUIApplication) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "description and keyboard"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let geometry = XCTAttachment(string: "field=\(field.frame) keyboard=\(keyboard.frame)")
        geometry.lifetime = .keepAlways
        add(geometry)
        XCTAssertTrue(field.isHittable)
        XCTAssertLessThanOrEqual(field.frame.maxY, keyboard.frame.minY, "\(field.frame) keyboard=\(keyboard.frame)")
        XCTAssertGreaterThanOrEqual(field.frame.minY, app.frame.minY)
    }
}
