import Foundation
import XCTest

final class CsvTransferRoundTripTests: XCTestCase {
    @MainActor
    func testLocalCsvExportAndActualImportInEnglish() {
        checkRoundTrip(copy: .english)
    }

    @MainActor
    func testLocalCsvExportAndActualImportInItalian() {
        checkRoundTrip(copy: .italian)
    }

    @MainActor
    private func checkRoundTrip(copy: Copy) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(copy.language))", "-AppleLocale", copy.locale]
        app.launch()
        defer { app.terminate() }
        let fixtureFolder = "SpesifyCSV-\(copy.language)-\(UUID().uuidString.lowercased())"

        openTransfer(app, copy: copy)
        app.buttons[copy.exportAction].tap()
        require(app.buttons[copy.export], in: app, stage: "export range")
        app.buttons[copy.export].tap()
        let exporter = app.otherElements["Browse View (Picker)"].firstMatch
        require(exporter, in: app, stage: "exporter appeared", timeout: 15)
        openLocalLocation(app, stage: "export local location", atRoot: true)
        let exportedName = exportedDocumentName(app)
        createLocalFolder(app, named: fixtureFolder)

        let save = app.buttons["DOCPicker.actionButton"]
        require(save, in: app, stage: "export save action")
        XCTAssertTrue(["Copy", "Copia"].contains(save.label), app.debugDescription)
        XCTAssertTrue(save.isEnabled, app.debugDescription)
        XCTAssertTrue(save.isHittable, app.debugDescription)
        attach(app, stage: "local CSV ready to save")
        save.tap()
        require(app.staticTexts[copy.exported], in: app, stage: "actual export completion", timeout: 15)
        XCTAssertTrue(exporter.waitForNonExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.buttons[copy.dashboard].firstMatch.isHittable, app.debugDescription)
        attach(app, stage: "CSV exported callback")
        XCTAssertTrue(app.staticTexts[copy.exported].waitForNonExistence(timeout: 10), app.debugDescription)

        openTransfer(app, copy: copy)
        app.buttons[copy.importAction].tap()
        let importer = app.otherElements["Browse View (Picker)"].firstMatch
        require(importer, in: app, stage: "importer appeared", timeout: 15)
        openLocalLocation(app, stage: "import local location")
        openFolder(app, named: fixtureFolder)
        let stem = exportedName.hasSuffix(".csv") ? String(exportedName.dropLast(4)) : exportedName
        let savedFile = app.collectionViews["File View"].descendants(matching: .any).matching(
            labels([exportedName, stem, stem + ".csv"])
        ).firstMatch
        require(savedFile, in: app, stage: "exported fixture visible in importer", timeout: 15)
        let savedCell = app.collectionViews["File View"].cells.containing(labels([exportedName, stem, stem + ".csv"])).firstMatch
        require(savedCell, in: app, stage: "exact exported CSV cell")
        attach(app, stage: "exact exported CSV selected")
        // Tap the document icon; tapping its filename can enter Files' inline interaction.
        savedCell.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)).tap()
        // Some document-picker presentations ask for an explicit Open after selection.
        let open = systemButton(app, labels: ["Open", "Apri"])
        if open.exists && open.isHittable && open.isEnabled { open.tap() }

        let importFeedback = app.staticTexts.matching(copy.importFeedback).firstMatch
        require(importFeedback, in: app, stage: "actual import completion", timeout: 20)
        XCTAssertTrue(importer.waitForNonExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(app.buttons[copy.dashboard].firstMatch.isHittable, app.debugDescription)
        attach(app, stage: "CSV imported callback")
        XCTAssertTrue(importFeedback.waitForNonExistence(timeout: 10), app.debugDescription)
        openTransfer(app, copy: copy)
        app.buttons[copy.cancel].tap()
        XCTAssertTrue(app.staticTexts[copy.title].waitForNonExistence(timeout: 10), app.debugDescription)
        require(app.buttons[copy.dashboard].firstMatch, in: app, stage: "transfer can be reopened after round trip")
        XCTAssertTrue(app.buttons[copy.dashboard].firstMatch.isHittable, app.debugDescription)
    }

    @MainActor
    private func openTransfer(_ app: XCUIApplication, copy: Copy) {
        let menu = app.buttons[copy.dashboard].firstMatch
        require(menu, in: app, stage: "dashboard navigation menu", timeout: 15)
        XCTAssertTrue(menu.isHittable, app.debugDescription)
        menu.tap()
        require(app.staticTexts[copy.menu], in: app, stage: "CSV menu item")
        app.staticTexts[copy.menu].tap()
        require(app.staticTexts[copy.title], in: app, stage: "CSV transfer sheet")
    }

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

    @MainActor
    private struct Copy {
        let language: String
        let locale: String
        let dashboard: String
        let menu: String
        let title: String
        let exportAction: String
        let export: String
        let importAction: String
        let cancel: String
        let exported: String
        let importFeedback: NSPredicate

        static let english = Copy(
            language: "en", locale: "en_US", dashboard: "Dashboard", menu: "CSV Import / Export", title: "CSV Import & Export",
            exportAction: "Export CSV…", export: "Export CSV", importAction: "Import CSV", cancel: "Cancel", exported: "CSV file exported",
            importFeedback: NSPredicate(
                format: "(label BEGINSWITH %@ AND label CONTAINS %@) OR label == %@",
                argumentArray: ["Imported ", "items from CSV", "No importable rows were found in the CSV file"]
            )
        )
        static let italian = Copy(
            language: "it", locale: "it_IT", dashboard: "Riepilogo", menu: "Import/export CSV", title: "Import ed export CSV",
            exportAction: "Esporta CSV…", export: "Esporta CSV", importAction: "Importa CSV", cancel: "Annulla", exported: "File CSV esportato",
            importFeedback: NSPredicate(
                format: "(label BEGINSWITH %@ AND label CONTAINS %@) OR label == %@",
                argumentArray: ["Importate ", "voci dal CSV", "Nessuna riga importabile trovata nel file CSV"]
            )
        )
    }
}
