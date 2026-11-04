import XCTest

final class CsvTransferNavigationTests: XCTestCase {
    @MainActor
    func testCsvExportPresentationAndCancellationInEnglish() {
        checkExport(language: "en", locale: "en_US", dashboard: "Dashboard", menu: "CSV Import / Export", title: "CSV Import & Export",
                    exportAction: "Export CSV…", export: "Export CSV", cancel: "Cancel")
    }

    @MainActor
    func testCsvExportPresentationAndCancellationInItalian() {
        checkExport(language: "it", locale: "it_IT", dashboard: "Riepilogo", menu: "Import/export CSV", title: "Import ed export CSV",
                    exportAction: "Esporta CSV…", export: "Esporta CSV", cancel: "Annulla")
    }

    @MainActor
    func testCsvImportPickerAndCancellationInEnglish() {
        checkImport(language: "en", locale: "en_US", dashboard: "Dashboard", menu: "CSV Import / Export", title: "CSV Import & Export",
                    importAction: "Import CSV", cancel: "Cancel")
    }

    @MainActor
    func testCsvImportPickerAndCancellationInItalian() {
        checkImport(language: "it", locale: "it_IT", dashboard: "Riepilogo", menu: "Import/export CSV", title: "Import ed export CSV",
                    importAction: "Importa CSV", cancel: "Annulla")
    }

    @MainActor
    private func checkExport(language: String, locale: String, dashboard: String, menu: String, title: String,
                             exportAction: String, export: String, cancel: String) {
        let app = launch(language: language, locale: locale)
        defer { app.terminate() }
        openTransfer(app, dashboard: dashboard, menu: menu, title: title)
        app.buttons[exportAction].tap()
        let exportButton = app.buttons[export]
        XCTAssertTrue(exportButton.waitForExistence(timeout: 10), app.debugDescription)
        app.buttons[cancel].tap()
        XCTAssertTrue(app.buttons[dashboard].firstMatch.waitForExistence(timeout: 10))
        openTransfer(app, dashboard: dashboard, menu: menu, title: title)
        app.buttons[exportAction].tap()
        XCTAssertTrue(exportButton.waitForExistence(timeout: 10))
        exportButton.tap()
        let picker = app.otherElements["Browse View (Picker)"].firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 15), app.debugDescription)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "CSV exporter system controls"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        // The iOS 27 export sheet has no visible Cancel button. Dismiss it with its native drag.
        let grabber = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08))
        grabber.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)))
        XCTAssertTrue(picker.waitForNonExistence(timeout: 10), app.debugDescription)
        assertDashboardReady(app, label: dashboard)
    }

    @MainActor
    private func checkImport(language: String, locale: String, dashboard: String, menu: String, title: String,
                             importAction: String, cancel: String) {
        let app = launch(language: language, locale: locale)
        defer { app.terminate() }
        openTransfer(app, dashboard: dashboard, menu: menu, title: title)
        app.buttons[importAction].tap()
        let picker = app.otherElements["Browse View (Picker)"].firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 15), app.debugDescription)
        let pickerCancel = app.navigationBars.buttons[cancel].firstMatch
        if pickerCancel.exists && pickerCancel.isHittable {
            pickerCancel.tap()
        } else {
            // Files can reopen its last folder with only its native sheet-dismiss gesture.
            let grabber = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08))
            grabber.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)))
        }
        XCTAssertTrue(picker.waitForNonExistence(timeout: 10), app.debugDescription)
        assertDashboardReady(app, label: dashboard)
    }

    @MainActor
    private func launch(language: String, locale: String) -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(language))", "-AppleLocale", locale]
        app.launch()
        return app
    }

    @MainActor
    private func openTransfer(_ app: XCUIApplication, dashboard: String, menu: String, title: String) {
        let navigationMenu = app.buttons[dashboard].firstMatch
        XCTAssertTrue(navigationMenu.waitForExistence(timeout: 15), app.debugDescription)
        navigationMenu.tap()
        let csv = app.staticTexts[menu]
        XCTAssertTrue(csv.waitForExistence(timeout: 10), app.debugDescription)
        csv.tap()
        XCTAssertTrue(app.staticTexts[title].waitForExistence(timeout: 10), app.debugDescription)
    }

    @MainActor
    private func assertDashboardReady(_ app: XCUIApplication, label: String) {
        let dashboard = app.buttons[label].firstMatch
        XCTAssertTrue(dashboard.waitForExistence(timeout: 10), app.debugDescription)
        let hittable = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: dashboard)
        XCTAssertEqual(XCTWaiter.wait(for: [hittable], timeout: 10), .completed, app.debugDescription)
    }
}
