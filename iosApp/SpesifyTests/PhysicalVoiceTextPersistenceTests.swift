#if !targetEnvironment(simulator)
@preconcurrency import ComposeApp
import Foundation
import Testing
@testable import Spesify

/// Run explicitly after a full native-database preflight establishes that the marker is absent.
/// Only the transcript boundary is supplied as text; model, writes, reads and deletion are real.
@MainActor
struct PhysicalVoiceTextPersistenceTests {
    @Test
    func suppliedTextPersistsAndRemovesOnlyItsIdentifiedExpense() async throws {
        let marker = "collaudo zaffiro"
        let controller = IosVoiceExpenseController()
        let baseline = try #require(try await controller.loadSnapshotData())
        let baselineIDs = Set(baseline.recentExpenses.map(\.id))
        try #require(!baseline.recentExpenses.contains { $0.description?.lowercased().contains(marker) == true })
        let recognizer = SystemVoiceExpenseRecognizer()
        try #require(recognizer.isAvailable)
        let text = "Ho speso dodici euro e trentaquattro oggi per cibo, descrizione collaudo zaffiro."
        let interpretation = try await recognizer.interpret(
            transcript: text, categories: baseline.categories.sorted { $0.name < $1.name },
            expenses: Array(baseline.recentExpenses.sorted { $0.date > $1.date }.prefix(80))
        )
        let draft = try #require(buildVoiceExpenseDraft(
            from: interpretation, transcript: text,
            categoriesById: Dictionary(uniqueKeysWithValues: baseline.categories.map { ($0.id, $0) }),
            expensesById: Dictionary(uniqueKeysWithValues: baseline.recentExpenses.map { ($0.id, $0) })
        ))
        // Every write precondition is fatal: an incorrect model result must never be saved.
        try #require(draft.intent == .create && draft.expenseId == nil)
        try #require(draft.amountInput == "12.34")
        try #require(draft.categoryName == "Food")
        let description = try #require(draft.description)
        try #require(description.lowercased().contains(marker))
        try #require(Calendar.current.isDateInToday(draft.date) && !draft.isShared)

        let workflow = ApplicationWriteTasks.shared.perform {
            try #require(try await Self.fixtureIDs(description: description, baselineIDs: baselineIDs).isEmpty)
            let editor = IosExpenseEditorController()
            var fixtureID: String?
            // Await ordinary single-item deletion even when verification throws. Never delete
            // a series, an amount match, or an unidentified row; preserve all preexisting IDs.
            defer {
                do {
                    // Save may commit before an error reaches its awaiter. Recover the unique
                    // marker through full paginated search, including outside recent120.
                    if fixtureID == nil {
                        let recovered = try await Self.fixtureIDs(description: description, baselineIDs: baselineIDs)
                        if recovered.count == 1 { fixtureID = recovered.first }
                        else { #expect(recovered.isEmpty, "Ambiguous fixture prohibits cleanup") }
                    }
                    if let id = fixtureID {
                        let row = try #require(try await editor.loadExpense(id: id))
                        try #require(row.descriptionText == description && !baselineIDs.contains(id))
                        let result = try await editor.deleteExpense(expenseId: id, deleteWholeSeries: false)
                        #expect(result.isSuccess)
                        if result.isSuccess { SpesifyWidgetSummaryRefresher.shared.refresh() }
                    }
                } catch {
                    Issue.record("Single fixture cleanup failed: \(String(reflecting: type(of: error)))")
                }
            }
            let result = try await controller.persist(draft: draft)
            try #require(result.success)
            SpesifyWidgetSummaryRefresher.shared.refresh()
            let matches = try await Self.fixtureIDs(description: description, baselineIDs: baselineIDs)
            try #require(matches.count == 1, "Exactly one new fixture ID is required before cleanup")
            let id = try #require(matches.first)
            fixtureID = id
            FileHandle.standardOutput.write(Data("PHYSICAL_TEXT_FIXTURE_ID \(id)\n".utf8))
            let reloaded = try #require(try await editor.loadExpense(id: id))
            try #require(reloaded.id == id && reloaded.amountInput == "12.34")
            try #require(reloaded.categoryId == draft.categoryId && reloaded.descriptionText == description)
            try #require(!reloaded.isShared && reloaded.recurringSeriesId == nil)
            let date = Date(timeIntervalSince1970: Double(reloaded.dateMillis) / 1000)
            try #require(Calendar.current.isDateInToday(date))
            FileHandle.standardOutput.write(Data("PHYSICAL_VOICE_SAVED\n".utf8))
            // A short observation window lets the external read-only collector copy SQLite/WAL.
            // Acceptance still depends on the actual copy and its audit, not on this delay.
            try await Task.sleep(for: .seconds(3))
            return true
        }
        try #require(try await workflow.value)
        let final = try #require(try await IosVoiceExpenseController().loadSnapshotData())
        try #require(Set(final.recentExpenses.map(\.id)) == baselineIDs)
        try #require(!final.recentExpenses.contains { $0.description?.lowercased().contains(marker) == true })
        FileHandle.standardOutput.write(Data("PHYSICAL_TEXT_PERSISTENCE_AND_CLEANUP_COMPLETE\n".utf8))
    }

    private static func fixtureIDs(description: String, baselineIDs: Set<String>) async throws -> [String] {
        let search = IosTransactionSearchObserver(query: "collaudo zaffiro", initialGroupingMode: "category")
        try await search.start()
        for await snapshot in search.snapshots {
            guard let snapshot else { continue }
            if snapshot.canLoadMoreExpenseResults {
                try await search.loadMoreResults()
                continue
            }
            return snapshot.expenseSnapshot.sections.flatMap(\.rows)
                .filter { $0.title == description && !baselineIDs.contains($0.id) }.map(\.id)
        }
        return []
    }
}
#endif
