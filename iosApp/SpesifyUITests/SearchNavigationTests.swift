import XCTest

final class SearchNavigationTests: XCTestCase {
    @MainActor
    func testSearchPublishesSnapshotInEnglish() {
        checkSearch(language: "en", locale: "en_US", searchAction: "Search transactions", title: "Search Results",
                    empty: "No matching transactions", income: "Income")
    }

    @MainActor
    func testSearchPublishesSnapshotInItalian() {
        checkSearch(language: "it", locale: "it_IT", searchAction: "Cerca transazioni", title: "Risultati ricerca",
                    empty: "Nessun movimento trovato", income: "Entrate")
    }

    @MainActor
    private func checkSearch(language: String, locale: String, searchAction: String, title: String, empty: String, income: String) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        defer { app.terminate() }
        let field = app.textViews[searchAction + "..."]
        XCTAssertTrue(field.waitForExistence(timeout: 15), app.debugDescription)
        field.tap()
        field.typeText("zzrefactoringnomatch")
        let submit = app.buttons[searchAction]
        XCTAssertTrue(submit.isEnabled)
        submit.tap()
        XCTAssertTrue(app.staticTexts[title].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["zzrefactoringnomatch"].exists)
        XCTAssertTrue(app.staticTexts[empty].waitForExistence(timeout: 10))
        app.buttons[income].tap()
        XCTAssertTrue(app.staticTexts[empty].waitForExistence(timeout: 10))
    }
}
