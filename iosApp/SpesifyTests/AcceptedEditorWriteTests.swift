@preconcurrency import ComposeApp
import Foundation
import Testing
@testable import Spesify

@MainActor
struct AcceptedEditorWriteTests {
    @Test(arguments: AcceptedNativeAction.allCases)
    fileprivate func acceptedNativeWriteFinishesAfterStop(action: AcceptedNativeAction) async throws {
        let fixture = AcceptedWriteFixture()
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var callbacks = 0
        let editor = action.makeEditor(fixture: fixture, writes: writes) { refreshes += 1 }
        action.submit(to: editor) { _ in callbacks += 1 }
        let pending = try #require(editor.savingTask)
        await fixture.gate.waitUntilEntered()
        editor.stop()
        fixture.gate.open()
        await pending.value
        #expect(fixture.ledger == [action.rawValue])
        #expect(refreshes == 1)
        #expect(callbacks == 0)
        #expect(writes.pendingCount == 0)
        #expect(!editor.isSaving)
    }

    @Test(arguments: [true, false])
    func acceptedNativeWriteDoesNotRetainEditor(expense: Bool) async throws {
        let fixture = AcceptedWriteFixture()
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        if expense {
            var model: NativeExpenseEditorViewModel? = NativeExpenseEditorViewModel(
                expenseId: "expense", editorController: fixture, writeTasks: writes,
                refreshWidget: { refreshes += 1 }
            )
            weak let released = model
            model?.delete(deleteWholeSeries: true) { _ in Issue.record("Released editor completed") }
            let pending = try #require(model?.savingTask)
            await fixture.gate.waitUntilEntered()
            model = nil
            #expect(released == nil)
            fixture.gate.open()
            await pending.value
            #expect(fixture.ledger == ["deleteExpense"])
        } else {
            var model: NativeTransactionEditorViewModel? = NativeTransactionEditorViewModel(
                initialKind: .expense, initialYear: nil, initialMonth: nil, editorController: fixture,
                writeTasks: writes, refreshWidget: { refreshes += 1 }
            )
            weak let released = model
            model?.save { _ in Issue.record("Released editor completed") }
            let pending = try #require(model?.savingTask)
            await fixture.gate.waitUntilEntered()
            model = nil
            #expect(released == nil)
            fixture.gate.open()
            await pending.value
            #expect(fixture.ledger == ["newExpense"])
        }
        #expect(refreshes == 1)
        #expect(writes.pendingCount == 0)
    }

    @Test(arguments: AcceptedWriteFailure.allCases)
    fileprivate func nativeFailureDoesNotRefreshWidget(failure: AcceptedWriteFailure) async throws {
        let fixture = AcceptedWriteFixture(failure: failure)
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var messages: [String?] = []
        let model = NativeTransactionEditorViewModel(
            initialKind: .income, initialYear: nil, initialMonth: nil, editorController: fixture,
            writeTasks: writes, refreshWidget: { refreshes += 1 }
        )
        model.save { messages.append($0) }
        let pending = try #require(model.savingTask)
        await fixture.gate.waitUntilEntered()
        fixture.gate.open()
        await pending.value
        #expect(fixture.ledger.isEmpty)
        #expect(refreshes == 0)
        #expect(!model.isSaving)
        #expect(messages == (failure == .cancellation ? [] : [failure.message]))
        #expect(writes.pendingCount == 0)
    }

    @Test(arguments: [true, false])
    func acceptedCategoryInsertFinishesAfterStop(expense: Bool) async throws {
        let fixture = AcceptedWriteFixture()
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var callbacks = 0
        let editor = AcceptedNativeAction.categoryEditor(expense: expense, fixture: fixture, writes: writes) {
            refreshes += 1
        }
        editor.insertCategory(name: "Refactoring category", iconKey: "category") { _ in callbacks += 1 }
        let pending = try #require(editor.categoryWriteTask)
        await fixture.gate.waitUntilEntered()
        editor.stop()
        fixture.gate.open()
        await pending.value
        #expect(fixture.ledger == ["Refactoring category"])
        #expect(editor.selectedCategoryId.isEmpty)
        #expect(refreshes == 1)
        #expect(callbacks == 0)
        #expect(writes.pendingCount == 0)
    }

    @Test(arguments: [true, false])
    func replacingCategoryAwaiterPreservesBothAcceptedInserts(expense: Bool) async throws {
        let fixture = AcceptedWriteFixture()
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var callbacks: [String?] = []
        let editor = AcceptedNativeAction.categoryEditor(expense: expense, fixture: fixture, writes: writes) {
            refreshes += 1
        }
        editor.insertCategory(name: "Refactoring first", iconKey: "category") { callbacks.append($0) }
        let first = try #require(editor.categoryWriteTask)
        await fixture.gate.waitUntilEntered()
        editor.insertCategory(name: "Refactoring second", iconKey: "category") { callbacks.append($0) }
        let second = try #require(editor.categoryWriteTask)
        fixture.gate.open()
        await first.value
        await second.value
        #expect(Set(fixture.ledger) == ["Refactoring first", "Refactoring second"])
        #expect(fixture.ledger.count == 2)
        #expect(refreshes == 2)
        #expect(callbacks == ["Refactoring second"])
        #expect(editor.selectedCategoryId == "Refactoring second")
        #expect(writes.pendingCount == 0)
    }

    @Test(arguments: [false, true])
    func acceptedVoiceWriteFinishesAfterDispose(update: Bool) async throws {
        let fixture = AcceptedWriteFixture()
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var callbacks = 0
        let model = await voiceModel(fixture: fixture, writes: writes, update: update) { refreshes += 1 }
        model.commit { callbacks += 1 }
        let pending = try #require(model.savingTask)
        await fixture.gate.waitUntilEntered()
        model.dispose()
        fixture.gate.open()
        await pending.value
        #expect(fixture.ledger == [update ? "voiceUpdate" : "voiceCreate"])
        #expect(refreshes == 1)
        #expect(callbacks == 0)
        #expect(fixture.snapshotLoads == 1)
        #expect(writes.pendingCount == 0)
        #expect(!model.isBusy)
    }

    @Test
    func acceptedVoiceWriteDoesNotRetainOwner() async throws {
        let fixture = AcceptedWriteFixture()
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var model: VoiceExpenseEntryViewModel? = await voiceModel(fixture: fixture, writes: writes) {
            refreshes += 1
        }
        weak let released = model
        model?.commit { Issue.record("Released voice owner completed") }
        let pending = try #require(model?.savingTask)
        await fixture.gate.waitUntilEntered()
        model = nil
        #expect(released == nil)
        fixture.gate.open()
        await pending.value
        #expect(fixture.ledger == ["voiceCreate"])
        #expect(refreshes == 1)
        #expect(fixture.snapshotLoads == 1)
        #expect(writes.pendingCount == 0)
    }

    @Test(arguments: AcceptedWriteFailure.allCases)
    fileprivate func voiceFailureDoesNotRefreshWidget(failure: AcceptedWriteFailure) async throws {
        let fixture = AcceptedWriteFixture(failure: failure)
        let writes = ApplicationWriteTasks()
        var refreshes = 0
        var callbacks = 0
        let model = await voiceModel(fixture: fixture, writes: writes) { refreshes += 1 }
        model.commit { callbacks += 1 }
        let pending = try #require(model.savingTask)
        await fixture.gate.waitUntilEntered()
        fixture.gate.open()
        await pending.value
        #expect(fixture.ledger.isEmpty)
        #expect(refreshes == 0)
        #expect(callbacks == 0)
        #expect(!model.isBusy)
        #expect((model.errorMessage == nil) == (failure == .cancellation))
        #expect(writes.pendingCount == 0)
        model.dispose()
    }

    private func voiceModel(
        fixture: AcceptedWriteFixture, writes: ApplicationWriteTasks, update: Bool = false,
        refreshWidget: @escaping @MainActor () -> Void
    ) async -> VoiceExpenseEntryViewModel {
        let model = VoiceExpenseEntryViewModel(
            controller: fixture, recorder: AcceptedWriteRecorder(), recognition: AcceptedWriteRecognizer(update: update),
            writeTasks: writes, refreshWidget: refreshWidget
        )
        await model.loadingTask?.value
        model.toggleRecording()
        await model.operationTask?.value
        model.toggleRecording()
        await model.operationTask?.value
        #expect(model.canCommit)
        return model
    }
}

// The check after the gate makes cancellation observable before a fixture can commit.
@MainActor
private final class AcceptedWriteFixture: NativeExpenseEditing, NativeTransactionEditing, NativeCategoryWriting, VoiceExpenseControlling {
    let gate = AcceptedWriteGate()
    private(set) var ledger: [String] = []
    private(set) var snapshotLoads = 0
    let failure: AcceptedWriteFailure?
    init(failure: AcceptedWriteFailure? = nil) { self.failure = failure }

    private func commit(_ entry: String) async throws -> Bool {
        await gate.wait()
        try Task.checkCancellation()
        if failure == .cancellation { throw CancellationError() }
        if failure == .thrown { throw AcceptedWriteError.failed }
        if failure == .business { return false }
        ledger.append(entry)
        return true
    }
    private func transaction(_ entry: String) async throws -> IosNativeTransactionEditorResult {
        let success = try await commit(entry)
        return IosNativeTransactionEditorResult(isSuccess: success, errorKey: success ? nil : "business_rejected")
    }
    private func expense(_ entry: String) async throws -> IosExpenseEditorOperationResult {
        let success = try await commit(entry)
        return IosExpenseEditorOperationResult(isSuccess: success, errorKey: success ? nil : "business_rejected")
    }
    func loadExpense(id: String) async -> IosExpenseEditorSnapshot? { nil }
    func loadIncome(id: String) async -> IosNativeIncomeEditorSnapshot? { nil }
    func saveExpense(expenseId: String, amountInput: String, dateMillis: Int64, categoryId: String, description: String,
                     isShared: Bool, isRecurringMonthly: Bool, updateWholeSeries: Bool) async throws -> IosExpenseEditorOperationResult {
        try await expense("saveExpense")
    }
    func deleteExpense(expenseId: String, deleteWholeSeries: Bool) async throws -> IosExpenseEditorOperationResult {
        try await expense("deleteExpense")
    }
    func saveExpense(amountInput: String, dateMillis: Int64, categoryId: String, description: String,
                     isShared: Bool, isRecurringMonthly: Bool, installmentCount: Int32) async throws -> IosNativeTransactionEditorResult {
        try await transaction("newExpense")
    }
    func saveIncome(amountInput: String, dateMillis: Int64, categoryId: String?, description: String,
                    isRecurringMonthly: Bool) async throws -> IosNativeTransactionEditorResult {
        try await transaction("newIncome")
    }
    func saveExistingIncome(incomeId: String, amountInput: String, dateMillis: Int64, categoryId: String?, description: String,
                            isRecurringMonthly: Bool, updateWholeSeries: Bool) async throws -> IosNativeTransactionEditorResult {
        try await transaction(updateWholeSeries ? "saveIncomeSeries" : "saveIncome")
    }
    func deleteIncome(incomeId: String, deleteWholeSeries: Bool) async throws -> IosNativeTransactionEditorResult {
        try await transaction("deleteIncome")
    }
    func insertCategoryAndReturnId(name: String, iconKey: String) async throws -> String? {
        try await commit(name) ? name : nil
    }
    func insertCategoryAndReturnIdForCategoryType(name: String, iconKey: String, categoryType: String) async throws -> String? {
        try await commit(name) ? name : nil
    }
    func loadSnapshotData() async -> VoiceExpenseSnapshotData? {
        snapshotLoads += 1
        return VoiceExpenseSnapshotData(
            categories: [.init(id: "food", name: "Cibo")],
            recentExpenses: [.init(id: "existing", amountInput: "12.00", categoryId: "food", categoryName: "Cibo",
                                   description: nil, date: Date(), isShared: false)]
        )
    }
    func persist(draft: VoiceExpenseDraft) async throws -> (success: Bool, message: String?) {
        let success = try await commit(draft.intent == .update ? "voiceUpdate" : "voiceCreate")
        return (success, success ? nil : "business_rejected")
    }
}

private enum AcceptedWriteFailure: String, CaseIterable, Sendable {
    case business, thrown, cancellation
    var message: String { self == .business ? "business_rejected" : "write_failed" }
}
private enum AcceptedWriteError: LocalizedError {
    case failed
    var errorDescription: String? { "write_failed" }
}

@MainActor
private protocol AcceptedNativeEditor: AnyObject {
    var savingTask: Task<Void, Never>? { get }
    var categoryWriteTask: Task<Void, Never>? { get }
    var selectedCategoryId: String { get }
    var isSaving: Bool { get }
    func stop()
    func insertCategory(name: String, iconKey: String, onComplete: @escaping (String?) -> Void)
}
extension NativeExpenseEditorViewModel: AcceptedNativeEditor {}
extension NativeTransactionEditorViewModel: AcceptedNativeEditor {}

private enum AcceptedNativeAction: String, CaseIterable, Sendable {
    case saveExpense, deleteExpense, newExpense, newIncome, saveIncome, saveIncomeSeries, deleteIncome
    @MainActor
    func makeEditor(fixture: AcceptedWriteFixture, writes: ApplicationWriteTasks,
                    refreshWidget: @escaping @MainActor () -> Void) -> any AcceptedNativeEditor {
        switch self {
        case .saveExpense, .deleteExpense:
            NativeExpenseEditorViewModel(expenseId: "expense", editorController: fixture, writeTasks: writes, refreshWidget: refreshWidget)
        case .newExpense, .newIncome:
            NativeTransactionEditorViewModel(initialKind: self == .newExpense ? .expense : .income, initialYear: nil, initialMonth: nil,
                                             editorController: fixture, writeTasks: writes, refreshWidget: refreshWidget)
        default:
            NativeTransactionEditorViewModel(incomeId: "income", editorController: fixture, writeTasks: writes, refreshWidget: refreshWidget)
        }
    }
    @MainActor
    func submit(to editor: any AcceptedNativeEditor, onComplete: @escaping (String?) -> Void) {
        switch self {
        case .saveExpense: (editor as! NativeExpenseEditorViewModel).save(updateWholeSeries: true, onComplete: onComplete)
        case .deleteExpense: (editor as! NativeExpenseEditorViewModel).delete(deleteWholeSeries: true, onComplete: onComplete)
        case .saveIncomeSeries: (editor as! NativeTransactionEditorViewModel).saveIncome(updateWholeSeries: true, onComplete: onComplete)
        case .deleteIncome: (editor as! NativeTransactionEditorViewModel).deleteIncome(deleteWholeSeries: true, onComplete: onComplete)
        default: (editor as! NativeTransactionEditorViewModel).save(onComplete: onComplete)
        }
    }
    @MainActor
    static func categoryEditor(expense: Bool, fixture: AcceptedWriteFixture, writes: ApplicationWriteTasks,
                               refreshWidget: @escaping @MainActor () -> Void) -> any AcceptedNativeEditor {
        if expense {
            NativeExpenseEditorViewModel(expenseId: "expense", editorController: fixture, categoryWriter: fixture,
                                          writeTasks: writes, refreshWidget: refreshWidget)
        } else {
            NativeTransactionEditorViewModel(initialKind: .income, initialYear: nil, initialMonth: nil,
                                             editorController: fixture, categoryWriter: fixture,
                                             writeTasks: writes, refreshWidget: refreshWidget)
        }
    }
}

@MainActor
private final class AcceptedWriteGate {
    private var entered = false
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        entered = true
        entryWaiters.forEach { $0.resume() }
        entryWaiters.removeAll()
        if !isOpen { await withCheckedContinuation { waiters.append($0) } }
    }
    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { entryWaiters.append($0) } }
    }
    func open() {
        isOpen = true
        waiters.forEach { $0.resume() }
        waiters.removeAll()
    }
}

@MainActor
private final class AcceptedWriteRecorder: VoiceExpenseRecording {
    func start(onTranscript: @escaping @MainActor @Sendable (String) -> Void,
               onFailure: @escaping @MainActor @Sendable (String) -> Void) async {
        onTranscript("Ho speso 12 euro per Cibo")
    }
    func stop() -> String { "Ho speso 12 euro per Cibo" }
}
@MainActor
private struct AcceptedWriteRecognizer: VoiceExpenseRecognizing {
    let update: Bool
    let isAvailable = true
    let unavailableMessage: String? = nil
    func interpret(transcript: String, categories: [VoiceExpenseCategory], expenses: [VoiceExpenseCandidate]) async -> VoiceExpenseInterpretation {
        VoiceExpenseInterpretation(intent: update ? .update : .create, expenseId: update ? "existing" : nil,
                                   amount: "12.00", categoryId: "food", categoryName: "Cibo", description: nil,
                                   date: nil, isShared: false, summary: "Cibo")
    }
}
