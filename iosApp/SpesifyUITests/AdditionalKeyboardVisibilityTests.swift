import XCTest
import UIKit

final class AdditionalKeyboardVisibilityTests: XCTestCase {
    @MainActor
    func testPastedMultilineDescriptionAboveKeyboard() {
        continueAfterFailure = false
        let pasted = "Pasted description\n" + String(repeating: "long text ", count: 25)
        UIPasteboard.general.string = pasted
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.buttons["Add transaction"].waitForExistence(timeout: 15))
        app.buttons["Add transaction"].tap()
        let field = app.textFields["editor_description"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.press(forDuration: 1)
        let paste = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Paste")).firstMatch
        XCTAssertTrue(paste.waitForExistence(timeout: 10), app.debugDescription)
        paste.tap()
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        XCTAssertEqual(field.value as? String, pasted)
        XCTAssertLessThanOrEqual(field.frame.maxY, keyboard.frame.minY)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "pasted multiline description above keyboard"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    @MainActor
    func testCategoryNameInSheetAndDashboardSearchAboveKeyboard() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let add = app.buttons["Add transaction"]
        XCTAssertTrue(add.waitForExistence(timeout: 15))
        add.tap()
        let picker = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Select Category")).firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 10))
        picker.tap()
        app.buttons["Add Category"].tap()
        let name = app.textFields["category_name"]
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.tap()
        name.typeText("Category keyboard verification")
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        XCTAssertLessThanOrEqual(name.frame.maxY, keyboard.frame.minY)
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "category name above keyboard"
        shot.lifetime = .keepAlways
        self.add(shot)
        app.buttons["Done"].firstMatch.tap()
        XCTAssertTrue(keyboard.waitForNonExistence(timeout: 10))
        name.tap()
        name.typeText(" reopened")
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        XCTAssertLessThanOrEqual(name.frame.maxY, keyboard.frame.minY)
        app.terminate()
        app.launch()
        let search = app.textViews["dashboard_search_field"]
        XCTAssertTrue(search.waitForExistence(timeout: 15))
        search.tap()
        search.typeText("long search query " + String(repeating: "query ", count: 15))
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        XCTAssertLessThanOrEqual(search.frame.maxY, keyboard.frame.minY)
    }
}
