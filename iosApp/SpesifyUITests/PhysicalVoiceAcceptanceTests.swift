#if !targetEnvironment(simulator)
import Foundation
import XCTest

/// One real microphone/model/persistence path. The device preflight must establish that the
/// reserved marker is absent; cleanup additionally requires one uniquely identified result row.
final class PhysicalVoiceAcceptanceTests: XCTestCase {
    private let marker = "collaudo zaffiro"

    @MainActor
    func testItalianSpeechCreatesPersistsAndDeletesOnlyItsFixture() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(it)", "-AppleLocale", "it_IT"]
        app.launch()

        let baseline = try dashboardEvidence(app)
        openVoice(app)

        // ContentView requests auto-start. Never tap the recording button blindly: it may already
        // be the Stop action by the time permission handling finishes.
        let stop = app.buttons["Ferma registrazione"]
        // Normal permission controls stay with the user. They can disappear between snapshots
        // when accepted manually; only the real active-recording state is acceptance evidence.
        require(stop, in: app, stage: "real microphone recording after permissions", timeout: 120)
        XCTAssertTrue(stop.isEnabled && stop.isHittable, app.debugDescription)
        attach(app, stage: "physical microphone ready for one spoken command")
        FileHandle.standardOutput.write(Data("PHYSICAL_VOICE_READY\n".utf8))

        let transcript = app.staticTexts.matching(NSPredicate(
            format: "label CONTAINS[c] %@ AND (label CONTAINS %@ OR label CONTAINS[c] %@)",
            argumentArray: ["zaffiro", "12", "dodici"]
        )).firstMatch
        require(transcript, in: app, stage: "real Italian speech transcript with amount and final marker", timeout: 120)
        attach(app, stage: "real Italian speech transcript before stopping")
        stop.tap()

        let save = app.buttons["Salva spesa"]
        require(save, in: app, stage: "model produced a new expense draft", timeout: 120)
        require(labeledValue(app, label: "Azione", value: "Crea nuova spesa"), in: app, stage: "draft CREATE action")
        require(labeledValue(app, label: "Importo", value: "€ 12.34"), in: app, stage: "draft amount 12.34")
        requireDescriptionFromModel(app)
        assertNonemptyCategory(app)
        attach(app, stage: "real model CREATE draft with amount category and description")
        XCTAssertTrue(save.isEnabled && save.isHittable, app.debugDescription)
        save.tap() // Exactly one explicit accepted write.
        XCTAssertTrue(app.navigationBars["Spesa vocale"].waitForNonExistence(timeout: 30), app.debugDescription)
        assertDashboard(app, baseline: baseline, delta: 1, stage: "voice save completed")
        FileHandle.standardOutput.write(Data("PHYSICAL_VOICE_SAVED\n".utf8))

        app.terminate()
        app.launch()
        assertDashboard(app, baseline: baseline, delta: 1, stage: "voice expense persists after relaunch")
        searchForFixture(app)

        let rows = fixtureRows(app)
        let row = rows.firstMatch
        require(row, in: app, stage: "persisted voice fixture in real transaction search", timeout: 30)
        guard rows.count == 1 else {
            attach(app, stage: "ambiguous marker results prohibit cleanup")
            XCTFail("Cleanup requires exactly one marker result\n" + app.debugDescription)
            return
        }
        XCTAssertTrue(row.label.contains("12.34"), app.debugDescription)
        let date = DateFormatter()
        date.locale = Locale(identifier: "en_US_POSIX")
        date.timeZone = .autoupdatingCurrent
        date.dateFormat = "yyyy-MM-dd"
        XCTAssertTrue(row.label.contains(date.string(from: Date())), "Fixture must have today's date\n" + app.debugDescription)
        let description = row.staticTexts.firstMatch.label
        guard !description.isEmpty, description.lowercased().contains(marker) else {
            attach(app, stage: "unidentified row title prohibits cleanup")
            XCTFail("The actual description must identify the marker fixture\n" + app.debugDescription)
            return
        }
        attach(app, stage: "unique persisted voice fixture description date and amount")

        // Never select by amount or category. The preceding unique marker and actual full title
        // are both required before the ordinary row-delete confirmation is accepted.
        row.swipeLeft()
        let delete = app.buttons["Elimina"].firstMatch
        require(delete, in: app, stage: "only marker row swipe delete")
        delete.tap()
        require(app.staticTexts["\"" + description + "\" verrà eliminato definitivamente."], in: app, stage: "delete confirmation names the actual fixture description")
        let confirmation = app.buttons.matching(NSPredicate(format: "label == %@", argumentArray: ["Elimina"]))
            .allElementsBoundByIndex.filter { $0.isHittable }
        XCTAssertEqual(confirmation.count, 1, "Only the active confirmation may delete\n" + app.debugDescription)
        attach(app, stage: "only unique physical voice fixture confirmed for cleanup")
        confirmation.first?.tap()
        XCTAssertTrue(row.waitForNonExistence(timeout: 30), app.debugDescription)
        require(app.staticTexts["Nessun movimento trovato"], in: app, stage: "marker search empty after cleanup", timeout: 30)
        attach(app, stage: "physical voice marker removed from real search")

        app.terminate()
        app.launch()
        assertDashboard(app, baseline: baseline, delta: 0, stage: "cleanup restored persisted dashboard baseline")
        attach(app, stage: "physical voice acceptance complete and fixture cleaned")
    }

    @MainActor
    private func openVoice(_ app: XCUIApplication) {
        let more = app.buttons["Altre opzioni di inserimento"]
        require(more, in: app, stage: "dashboard transaction input menu", timeout: 30)
        more.tap()
        let voice = app.buttons["Inserimento vocale"]
        require(voice, in: app, stage: "real voice entry action")
        voice.tap()
        require(app.navigationBars["Spesa vocale"], in: app, stage: "real voice entry sheet", timeout: 30)
        attach(app, stage: "physical voice sheet before capture")
    }

    @MainActor
    private func labeledValue(_ app: XCUIApplication, label: String, value: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(
            format: "label == %@ OR label == %@ OR (label == %@ AND value == %@)",
            argumentArray: [value, label + ", " + value, label, value]
        )).firstMatch
    }

    @MainActor
    private func requireDescriptionFromModel(_ app: XCUIApplication) {
        // parseSimpleExpenseIntent always supplies description=nil. A CREATE draft with this
        // structured description therefore requires a successful real model response, rather
        // than merely the Ready to Save state that is also available through the fallback.
        let description = app.descendants(matching: .any).matching(NSPredicate(
            format: "label ==[c] %@ OR (label BEGINSWITH %@ AND label CONTAINS[c] %@) OR (label == %@ AND value CONTAINS[c] %@)",
            argumentArray: [marker, "Descrizione, ", marker, "Descrizione", marker]
        )).firstMatch
        require(description, in: app, stage: "CREATE description marker proves real model output")
        let label = app.descendants(matching: .any).matching(NSPredicate(
            format: "label == %@ OR label BEGINSWITH %@", argumentArray: ["Descrizione", "Descrizione, "]
        )).firstMatch
        require(label, in: app, stage: "structured draft Description field")
    }

    @MainActor
    private func assertNonemptyCategory(_ app: XCUIApplication) {
        let element = app.descendants(matching: .any).matching(NSPredicate(
            format: "label == %@ OR label BEGINSWITH %@", argumentArray: ["Categoria", "Categoria, "]
        )).firstMatch
        require(element, in: app, stage: "draft category")
        if element.label.hasPrefix("Categoria, ") {
            XCTAssertFalse(String(element.label.dropFirst("Categoria, ".count)).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, app.debugDescription)
        } else if let value = element.value as? String, !value.isEmpty {
            XCTAssertFalse(value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, app.debugDescription)
        } else {
            // Some LabeledContent layouts expose separate key/value StaticTexts. Find the
            // adjacent, nonempty value on the same visual row without assuming a category name.
            let key = app.staticTexts["Categoria"]
            let values = app.staticTexts.allElementsBoundByIndex.filter {
                !$0.label.isEmpty && $0.label != "Categoria" &&
                abs($0.frame.midY - key.frame.midY) < max(12, key.frame.height) &&
                $0.frame.minX >= key.frame.maxX
            }
            XCTAssertEqual(values.count, 1, "Expected one nonempty draft category value\n" + app.debugDescription)
        }
    }

    @MainActor
    private func searchForFixture(_ app: XCUIApplication) {
        let field = app.textViews["Cerca transazioni..."]
        require(field, in: app, stage: "real dashboard transaction search", timeout: 30)
        field.tap()
        field.typeText(marker)
        let search = app.buttons["Cerca transazioni"]
        require(search, in: app, stage: "search action")
        XCTAssertTrue(search.isEnabled, app.debugDescription)
        search.tap()
        require(app.staticTexts["Risultati ricerca"].firstMatch, in: app, stage: "real marker search results", timeout: 30)
        let category = app.buttons["Visualizza transazioni per Categoria"]
        require(category, in: app, stage: "marker search category grouping")
        category.tap()
    }

    @MainActor
    private func fixtureRows(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", argumentArray: [marker]))
    }

    @MainActor
    private func dashboardEvidence(_ app: XCUIApplication) throws -> DashboardEvidence {
        let count = expenseCountMetric(app)
        require(count, in: app, stage: "baseline expense count", timeout: 30)
        let income = app.descendants(matching: .any).matching(NSPredicate(
            format: "label MATCHES %@", argumentArray: ["^Entrate[\\s,:]*.*[0-9]+\\.[0-9]{2}$"]
        )).firstMatch
        require(income, in: app, stage: "baseline income amount")
        guard let value = Int(count.label.filter(\.isNumber)) else {
            attach(app, stage: "unreadable dashboard count")
            throw EvidenceError.unreadableCount(count.label)
        }
        attach(app, stage: "physical device baseline count and income")
        return DashboardEvidence(expenseCount: value, incomeLabel: income.label)
    }

    @MainActor
    private func expenseCountMetric(_ app: XCUIApplication, expected: Int? = nil) -> XCUIElement {
        let pattern = "^Spese[\\s,:]*" + (expected.map(String.init) ?? "[0-9]+") + "$"
        return app.descendants(matching: .any).matching(NSPredicate(format: "label MATCHES %@", argumentArray: [pattern])).firstMatch
    }

    @MainActor
    private func assertDashboard(_ app: XCUIApplication, baseline: DashboardEvidence, delta: Int, stage: String) {
        require(expenseCountMetric(app, expected: baseline.expenseCount + delta), in: app, stage: stage + " count", timeout: 30)
        let income = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", argumentArray: [baseline.incomeLabel])).firstMatch
        require(income, in: app, stage: stage + " unchanged income")
        attach(app, stage: stage)
    }

    @MainActor
    private func require(_ element: XCUIElement, in app: XCUIApplication, stage: String, timeout: TimeInterval = 15) {
        let exists = element.waitForExistence(timeout: timeout)
        if !exists { attach(app, stage: stage) }
        XCTAssertTrue(exists, stage + "\n" + app.debugDescription)
    }

    @MainActor
    private func attach(_ app: XCUIApplication, stage: String) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = stage + " hierarchy"
        tree.lifetime = .keepAlways
        add(tree)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = stage
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    private struct DashboardEvidence {
        let expenseCount: Int
        let incomeLabel: String
    }
    private enum EvidenceError: Error {
        case unreadableCount(String)
    }
}
#endif
