import XCTest

final class EditorNavigationTests: XCTestCase {
    @MainActor
    func testExpenseAndIncomeEditorsInEnglish() {
        checkEditors(language: "en", locale: "en_US", add: "Add transaction", expenseTitle: "Add Expense",
                     saveExpense: "Save", income: "Income", incomeTitle: "Add Income", save: "Save", cancel: "Cancel")
    }

    @MainActor
    func testExpenseAndIncomeEditorsInItalian() {
        checkEditors(language: "it", locale: "it_IT", add: "Aggiungi transazione", expenseTitle: "Aggiungi spesa",
                     saveExpense: "Salva", income: "Entrate", incomeTitle: "Aggiungi entrata", save: "Salva", cancel: "Annulla")
    }

    @MainActor
    private func checkEditors(language: String, locale: String, add: String, expenseTitle: String,
                              saveExpense: String, income: String, incomeTitle: String, save: String, cancel: String) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        defer { app.terminate() }
        let addButton = app.buttons[add]
        XCTAssertTrue(addButton.waitForExistence(timeout: 15))
        addButton.tap()
        XCTAssertTrue(app.staticTexts[expenseTitle].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons[saveExpense].isHittable)
        XCTAssertFalse(app.buttons[saveExpense].isEnabled)
        app.buttons[income].tap()
        XCTAssertTrue(app.staticTexts[incomeTitle].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons[save].isHittable)
        XCTAssertFalse(app.buttons[save].isEnabled)
        XCTAssertTrue(app.buttons[cancel].isEnabled)
        app.buttons[cancel].tap()
        let returnedToDashboard = addButton.waitForExistence(timeout: 10)
        if !returnedToDashboard {
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.lifetime = .keepAlways
            self.add(screenshot)
        }
        XCTAssertTrue(returnedToDashboard, "Fresh dashboard query: \(app.buttons[add].exists)\n\(app.debugDescription)")
        XCTAssertTrue(addButton.isHittable)
    }
}
