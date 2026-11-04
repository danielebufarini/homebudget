import Foundation
import XCTest

final class CsvAcceptedImportTests: XCTestCase {
    @MainActor
    func testNonemptyCsvImportCommitsAnExportedExpenseInEnglish() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let token = "RefactoringCsv-" + UUID().uuidString.lowercased()
        let fixtureFolder = "SpesifyCSV-en-" + UUID().uuidString.lowercased()
        let baseline = try dashboardEvidence(app)
        openExpenses(app)
        let baselineTotal = try expenseTotal(app)
        returnToDashboard(app)

        let categoryName = createFixtureExpense(app, token: token)
        assertDashboard(app, count: baseline.expenseCount + 1, incomeLabel: baseline.incomeLabel, stage: "fixture saved")
        openExpenses(app)
        assertExpenseTotal(app, baseline: baselineTotal, delta: 1234, stage: "saved fixture total")
        assertFixtureRow(app, token: token, categoryName: categoryName)
        returnToDashboard(app)

        let exportedName = exportFixture(app, folder: fixtureFolder)
        openExpenses(app)
        deleteOnlyFixture(app, token: token, categoryName: categoryName)
        assertExpenseTotal(app, baseline: baselineTotal, delta: 0, stage: "fixture removed before import")
        returnToDashboard(app)
        assertDashboard(app, count: baseline.expenseCount, incomeLabel: baseline.incomeLabel, stage: "baseline restored before import")

        importFixture(app, folder: fixtureFolder, fileName: exportedName)
        // A second launch distinguishes persisted imported rows from a transient presentation result.
        app.terminate()
        app.launch()
        assertDashboard(app, count: baseline.expenseCount + 1, incomeLabel: baseline.incomeLabel, stage: "accepted import persisted")
        openExpenses(app)
        assertExpenseTotal(app, baseline: baselineTotal, delta: 1234, stage: "imported fixture total")
        assertFixtureRow(app, token: token, categoryName: categoryName)
        attach(app, stage: "exactly one imported fixture with its amount and total")

        deleteOnlyFixture(app, token: token, categoryName: categoryName)
        assertExpenseTotal(app, baseline: baselineTotal, delta: 0, stage: "fixture cleanup total")
        returnToDashboard(app)
        assertDashboard(app, count: baseline.expenseCount, incomeLabel: baseline.incomeLabel, stage: "fixture cleanup restored baseline")
    }

    @MainActor
    private func createFixtureExpense(_ app: XCUIApplication, token: String) -> String {
        let addTransaction = app.buttons["Add transaction"]
        require(addTransaction, in: app, stage: "native add transaction", timeout: 15)
        XCTAssertTrue(addTransaction.isHittable, app.debugDescription)
        addTransaction.tap()
        require(app.staticTexts["Add Expense"], in: app, stage: "native expense editor")
        attach(app, stage: "native expense editor before fixture input")

        let amount = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", argumentArray: ["Amount"])).firstMatch
        require(amount, in: app, stage: "native amount card")
        amount.tap()
        require(app.staticTexts["Calculator"], in: app, stage: "native calculator")
        for key in ["C", "1", "2", ".", "3", "4", "="] {
            let button = app.buttons[key]
            require(button, in: app, stage: "calculator key " + key)
            XCTAssertTrue(button.isEnabled && button.isHittable, app.debugDescription)
            button.tap()
        }
        XCTAssertTrue(app.staticTexts["Calculator"].waitForNonExistence(timeout: 10), app.debugDescription)

        let category = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", argumentArray: ["Select Category"])).firstMatch
        require(category, in: app, stage: "native category row")
        category.tap()
        require(app.staticTexts["Select Category"], in: app, stage: "native category picker")
        attach(app, stage: "existing expense category picker")
        let existingCategory = app.scrollViews.buttons.allElementsBoundByIndex.first {
            $0.isHittable && !$0.label.contains("Add Category")
        }
        guard let existingCategory else {
            attach(app, stage: "existing expense category required")
            XCTFail("Expected an existing selectable expense category\n" + app.debugDescription)
            return ""
        }
        let categoryName = existingCategory.staticTexts.firstMatch.label
        XCTAssertFalse(categoryName.isEmpty, app.debugDescription)
        existingCategory.tap()
        XCTAssertTrue(app.staticTexts["Select Category"].waitForNonExistence(timeout: 10), app.debugDescription)

        // The editor has one editable description. Its vertical TextField can expose either AX type.
        let description = app.textFields.firstMatch.exists ? app.textFields.firstMatch : app.textViews.firstMatch
        require(description, in: app, stage: "native expense description")
        description.tap()
        description.typeText(token)
        XCTAssertEqual(description.value as? String, token, app.debugDescription)
        app.staticTexts["Expense Details"].tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 10), app.debugDescription)
        XCTAssertEqual(description.value as? String, token, app.debugDescription)
        attach(app, stage: "native fixture amount category and unique description")
        let save = app.buttons["Save"]
        require(save, in: app, stage: "native expense save")
        XCTAssertTrue(save.isEnabled && save.isHittable, app.debugDescription)
        save.tap()
        require(app.buttons["Add transaction"], in: app, stage: "dashboard after fixture save", timeout: 15)
        XCTAssertTrue(app.staticTexts["Add Expense"].waitForNonExistence(timeout: 10), app.debugDescription)
        return categoryName
    }

    @MainActor
    private func exportFixture(_ app: XCUIApplication, folder: String) -> String {
        openTransfer(app)
        app.buttons["Export CSV…"].tap()
        require(app.buttons["Export CSV"], in: app, stage: "export range")
        app.buttons["Export CSV"].tap()
        let exporter = app.otherElements["Browse View (Picker)"].firstMatch
        require(exporter, in: app, stage: "exporter appeared", timeout: 15)
        openLocalLocation(app, stage: "export local location", atRoot: true)
        let exportedName = exportedDocumentName(app)
        createLocalFolder(app, named: folder)
        let save = app.buttons["DOCPicker.actionButton"]
        require(save, in: app, stage: "export save action")
        XCTAssertTrue(["Copy", "Copia"].contains(save.label), app.debugDescription)
        XCTAssertTrue(save.isEnabled && save.isHittable, app.debugDescription)
        attach(app, stage: "nonempty CSV ready to save in unique local folder")
        save.tap()
        require(app.staticTexts["CSV file exported"], in: app, stage: "actual nonempty export completion", timeout: 15)
        XCTAssertTrue(exporter.waitForNonExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.buttons["Dashboard"].firstMatch.isHittable, app.debugDescription)
        attach(app, stage: "nonempty CSV exported callback")
        XCTAssertTrue(app.staticTexts["CSV file exported"].waitForNonExistence(timeout: 10), app.debugDescription)
        return exportedName
    }

    @MainActor
    private func importFixture(_ app: XCUIApplication, folder: String, fileName: String) {
        openTransfer(app)
        app.buttons["Import CSV"].tap()
        let importer = app.otherElements["Browse View (Picker)"].firstMatch
        require(importer, in: app, stage: "importer appeared", timeout: 15)
        openLocalLocation(app, stage: "import local location")
        openFolder(app, named: folder)
        let stem = fileName.hasSuffix(".csv") ? String(fileName.dropLast(4)) : fileName
        let file = app.collectionViews["File View"].descendants(matching: .any).matching(labels([fileName, stem, stem + ".csv"])).firstMatch
        require(file, in: app, stage: "exact nonempty exported CSV available", timeout: 15)
        let cell = app.collectionViews["File View"].cells.containing(labels([fileName, stem, stem + ".csv"])).firstMatch
        require(cell, in: app, stage: "exact nonempty exported CSV cell")
        attach(app, stage: "exact nonempty exported CSV selected")
        cell.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)).tap()
        let open = systemButton(app, labels: ["Open", "Apri"])
        if open.exists && open.isHittable && open.isEnabled { open.tap() }

        // Existing exported rows may be skipped. Exactly the deleted fixture must be newly inserted.
        let positive = app.staticTexts.matching(NSPredicate(
            format: "label MATCHES %@",
            argumentArray: ["^Imported 1 items from CSV(, skipped [0-9]+)?$"]
        )).firstMatch
        require(positive, in: app, stage: "actual import inserted exactly one item", timeout: 20)
        XCTAssertTrue(importer.waitForNonExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.buttons["Dashboard"].firstMatch.isHittable, app.debugDescription)
        attach(app, stage: "positive CSV commit callback")
        XCTAssertTrue(positive.waitForNonExistence(timeout: 10), app.debugDescription)
        openTransfer(app)
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.staticTexts["CSV Import & Export"].waitForNonExistence(timeout: 10), app.debugDescription)
        require(app.buttons["Dashboard"].firstMatch, in: app, stage: "transfer can reopen after committed import")
        XCTAssertTrue(app.buttons["Dashboard"].firstMatch.isHittable, app.debugDescription)
    }

    @MainActor
    private func openTransfer(_ app: XCUIApplication) {
        let menu = app.buttons["Dashboard"].firstMatch
        require(menu, in: app, stage: "dashboard navigation menu", timeout: 15)
        XCTAssertTrue(menu.isHittable, app.debugDescription)
        menu.tap()
        require(app.staticTexts["CSV Import / Export"], in: app, stage: "CSV menu item")
        app.staticTexts["CSV Import / Export"].tap()
        require(app.staticTexts["CSV Import & Export"], in: app, stage: "CSV transfer sheet")
    }

    @MainActor
    private func openExpenses(_ app: XCUIApplication) {
        let metric = expenseCountMetric(app)
        require(metric, in: app, stage: "dashboard expense count metric", timeout: 15)
        XCTAssertTrue(metric.isHittable, app.debugDescription)
        metric.tap()
        require(expenseTotalElement(app), in: app, stage: "native monthly expense total")
        require(app.buttons["Back"], in: app, stage: "native monthly back action")
        attach(app, stage: "native monthly expenses")
    }

    @MainActor
    private func assertFixtureRow(_ app: XCUIApplication, token: String, categoryName: String) {
        let row = fixtureRows(app, token: token).firstMatch
        if !row.exists {
            // The monthly screen intentionally starts with collapsed category sections.
            let categoryMode = app.buttons["View transactions by Category"]
            require(categoryMode, in: app, stage: "monthly category grouping")
            categoryMode.tap()
            let categorySection = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", argumentArray: [categoryName])).firstMatch
            require(categorySection, in: app, stage: "fixture category section")
            // Its plain HStack button has a spacer in the center; tap the visible category title.
            let title = categorySection.staticTexts[categoryName]
            require(title, in: app, stage: "visible fixture category title")
            title.tap()
        }
        require(row, in: app, stage: "unique fixture expense row", timeout: 15)
        XCTAssertEqual(fixtureRows(app, token: token).count, 1, "Only the UUID fixture may be selected\n" + app.debugDescription)
        XCTAssertTrue(row.label.contains("12.34"), "Fixture row must retain its amount\n" + app.debugDescription)
        XCTAssertTrue(row.isHittable, app.debugDescription)
        attach(app, stage: "unique fixture expense and amount")
    }

    @MainActor
    private func deleteOnlyFixture(_ app: XCUIApplication, token: String, categoryName: String) {
        assertFixtureRow(app, token: token, categoryName: categoryName)
        let row = fixtureRows(app, token: token).firstMatch
        row.swipeLeft()
        let delete = app.buttons["Delete"].firstMatch
        require(delete, in: app, stage: "fixture row swipe Delete")
        delete.tap()
        let confirmation = app.staticTexts["\"" + token + "\" will be permanently deleted."]
        require(confirmation, in: app, stage: "delete confirmation names only the fixture")
        attach(app, stage: "only UUID fixture confirmed for deletion")
        let visibleDeletes = app.buttons.matching(NSPredicate(format: "label == %@", argumentArray: ["Delete"]))
            .allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(visibleDeletes.count, 1, "Expected only the active confirmation Delete\n" + app.debugDescription)
        visibleDeletes.first?.tap()
        XCTAssertTrue(row.waitForNonExistence(timeout: 15), "Fixture deletion must reach the observed store\n" + app.debugDescription)
    }

    @MainActor
    private func fixtureRows(_ app: XCUIApplication, token: String) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", argumentArray: [token]))
    }

    @MainActor
    private func returnToDashboard(_ app: XCUIApplication) {
        let back = app.buttons["Back"]
        require(back, in: app, stage: "return from monthly expenses")
        back.tap()
        require(app.buttons["Dashboard"].firstMatch, in: app, stage: "dashboard returned", timeout: 15)
        XCTAssertTrue(app.buttons["Add transaction"].isHittable, app.debugDescription)
    }

    @MainActor
    private func dashboardEvidence(_ app: XCUIApplication) throws -> DashboardEvidence {
        let count = expenseCountMetric(app)
        require(count, in: app, stage: "baseline expense COUNT", timeout: 15)
        let income = app.descendants(matching: .any).matching(NSPredicate(
            format: "label MATCHES %@", argumentArray: ["^Income[\\s,:]*.*[0-9]+\\.[0-9]{2}$"]
        )).firstMatch
        require(income, in: app, stage: "baseline income amount")
        let digits = count.label.filter(\.isNumber)
        guard let value = Int(digits) else {
            attach(app, stage: "unreadable dashboard expense count")
            throw EvidenceError.unreadableCount(count.label)
        }
        attach(app, stage: "baseline expense count and income amount")
        return DashboardEvidence(expenseCount: value, incomeLabel: income.label)
    }

    @MainActor
    private func expenseCountMetric(_ app: XCUIApplication, expected: Int? = nil) -> XCUIElement {
        let pattern = "^Expenses[\\s,:]*" + (expected.map(String.init) ?? "[0-9]+") + "$"
        return app.descendants(matching: .any).matching(NSPredicate(format: "label MATCHES %@", argumentArray: [pattern])).firstMatch
    }

    @MainActor
    private func assertDashboard(_ app: XCUIApplication, count: Int, incomeLabel: String, stage: String) {
        require(expenseCountMetric(app, expected: count), in: app, stage: stage + " expense count", timeout: 15)
        let income = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", argumentArray: [incomeLabel])).firstMatch
        require(income, in: app, stage: stage + " unchanged income")
        attach(app, stage: stage)
    }

    @MainActor
    private func expenseTotalElement(_ app: XCUIApplication, text: String? = nil) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(
            format: text == nil ? "label BEGINSWITH %@" : "label == %@", argumentArray: [text ?? "Expenses • "]
        )).firstMatch
    }

    @MainActor
    private func expenseTotal(_ app: XCUIApplication) throws -> ExpenseTotalEvidence {
        let element = expenseTotalElement(app)
        require(element, in: app, stage: "baseline native monthly expense total")
        let label = element.label
        let expression = try NSRegularExpression(pattern: "^(Expenses • .*?)(-?[0-9]+)\\.([0-9]{2})$")
        let source = label as NSString
        guard let match = expression.firstMatch(in: label, range: NSRange(location: 0, length: source.length)),
              let units = Int(source.substring(with: match.range(at: 2))),
              let cents = Int(source.substring(with: match.range(at: 3))) else {
            attach(app, stage: "unreadable native monthly total")
            throw EvidenceError.unreadableTotal(label)
        }
        return ExpenseTotalEvidence(prefix: source.substring(with: match.range(at: 1)), minorUnits: units * 100 + (units < 0 ? -cents : cents))
    }

    @MainActor
    private func assertExpenseTotal(_ app: XCUIApplication, baseline: ExpenseTotalEvidence, delta: Int, stage: String) {
        let amount = baseline.minorUnits + delta
        let digits = String(abs(amount) / 100) + "." + String(format: "%02d", abs(amount) % 100)
        let expected = baseline.prefix + (amount < 0 ? "-" : "") + digits
        require(expenseTotalElement(app, text: expected), in: app, stage: stage, timeout: 15)
        attach(app, stage: stage)
    }

    // Local document-provider helpers copied from the current round-trip UI test.
    @MainActor
    private func openLocalLocation(_ app: XCUIApplication, stage: String, atRoot: Bool = false) {
        let localNames = ["On My iPhone", "Su iPhone", "Sul mio iPhone"]
        let localTitle = app.navigationBars.staticTexts.matching(labels(localNames)).firstMatch
        if localProvider(app).exists {
            if atRoot {
                // The export picker can remember a folder selected by an earlier run.
                for _ in 0..<20 {
                    if localTitle.waitForExistence(timeout: 1) { break }
                    let back = app.buttons["BackButton"]
                    require(back, in: app, stage: "\(stage): parent folder")
                    back.tap()
                }
                require(localTitle, in: app, stage: "\(stage): local root")
            }
            attach(app, stage: stage)
            return
        }
        if !localTitle.exists {
            var local = app.descendants(matching: .any).matching(labels(localNames)).firstMatch
            if !local.exists {
                let browse = app.buttons.matching(labels(["Browse", "Sfoglia"])).firstMatch
                require(browse, in: app, stage: "\(stage): Browse")
                browse.tap()
                local = app.descendants(matching: .any).matching(labels(localNames)).firstMatch
            }
            require(local, in: app, stage: "\(stage): local provider")
            local.tap()
        }
        require(localTitle, in: app, stage: stage)
        require(localProvider(app), in: app, stage: "\(stage): LocalStorage provider")
        attach(app, stage: stage)
    }

    @MainActor
    private func exportedDocumentName(_ app: XCUIApplication) -> String {
        let field = app.textFields["DOCPicker.filenameTextField"]
        require(field, in: app, stage: "export document name")
        let name = (field.value as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        XCTAssertTrue(name.hasPrefix("full_database_"), "Unexpected document name: \(name)\n\(app.debugDescription)")
        XCTAssertFalse(name.contains("...") || name.contains("…"), "Document name must be readable in full: \(name)")
        return name
    }

    @MainActor
    private func createLocalFolder(_ app: XCUIApplication, named name: String) {
        let more = app.buttons["OverflowBarButtonItem"]
        require(more, in: app, stage: "local Files More menu")
        XCTAssertTrue(more.isHittable, app.debugDescription)
        more.tap()
        let newFolderNames = ["New Folder", "Nuova cartella"]
        var newFolder = app.buttons.matching(labels(newFolderNames)).firstMatch
        if !newFolder.exists { newFolder = app.menuItems.matching(labels(newFolderNames)).firstMatch }
        require(newFolder, in: app, stage: "local Files New Folder command")
        XCTAssertTrue(newFolder.isEnabled, app.debugDescription)
        newFolder.tap()
        attach(app, stage: "local Files folder creation")

        // Files creates the folder immediately and selects its temporary name for inline rename.
        let field = app.textViews["DOC.inlineRenameField"]
        require(field, in: app, stage: "local Files inline folder name")
        XCTAssertTrue(field.isEnabled && field.isHittable, app.debugDescription)
        field.typeText(name)
        XCTAssertEqual(field.value as? String, name, app.debugDescription)
        field.typeText("\n")
        XCTAssertTrue(field.waitForNonExistence(timeout: 10), app.debugDescription)
        openFolder(app, named: name)
    }

    @MainActor
    private func openFolder(_ app: XCUIApplication, named name: String) {
        let folderTitle = app.navigationBars.buttons.matching(NSPredicate(
            format: "label BEGINSWITH %@",
            argumentArray: [name + ", "]
        )).firstMatch
        if !folderTitle.waitForExistence(timeout: 3) {
            let folder = app.collectionViews["File View"].descendants(matching: .any).matching(labels([name])).firstMatch
            require(folder, in: app, stage: "unique local fixture folder", timeout: 15)
            folder.tap()
        }
        require(folderTitle, in: app, stage: "inside unique fixture folder")
        require(localProvider(app), in: app, stage: "unique fixture remains in LocalStorage")
        attach(app, stage: "unique local fixture folder")
    }

    @MainActor
    private func localProvider(_ app: XCUIApplication) -> XCUIElement {
        app.otherElements.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", argumentArray: ["DOC.browsingRoot Source: com.apple.FileProvider.LocalStorage,"])
        ).firstMatch
    }

    @MainActor
    private func systemButton(_ app: XCUIApplication, labels names: [String]) -> XCUIElement {
        app.navigationBars.buttons.matching(labels(names)).firstMatch
    }

    private func labels(_ names: [String]) -> NSPredicate {
        NSPredicate(format: "label IN %@", argumentArray: [names])
    }

    @MainActor
    private func require(_ element: XCUIElement, in app: XCUIApplication, stage: String, timeout: TimeInterval = 10) {
        let exists = element.waitForExistence(timeout: timeout)
        if !exists { attach(app, stage: stage) }
        XCTAssertTrue(exists, "\(stage)\n\(app.debugDescription)")
    }

    @MainActor
    private func attach(_ app: XCUIApplication, stage: String) {
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = stage + " hierarchy"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = stage
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    private struct DashboardEvidence {
        let expenseCount: Int
        let incomeLabel: String
    }

    private struct ExpenseTotalEvidence {
        let prefix: String
        let minorUnits: Int
    }

    private enum EvidenceError: Error {
        case unreadableCount(String)
        case unreadableTotal(String)
    }
}
