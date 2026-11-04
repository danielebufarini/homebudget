@preconcurrency import ComposeApp
import Testing
@testable import Spesify

@MainActor
struct NativeEditorLifecycleTests {
    @Test(arguments: [true, false])
    func closingAnEditorIgnoresLateLoadedFields(expense: Bool) async throws {
        let controller = ControlledNativeEditor()
        if expense {
            let model = NativeExpenseEditorViewModel(expenseId: "expense", editorController: controller)
            model.start()
            let pending = try #require(model.loadingTask)
            await controller.gate.waitUntilEntered()
            model.stop()
            controller.gate.open()
            await pending.value
            #expect(model.amount.isEmpty)
        } else {
            let model = NativeTransactionEditorViewModel(incomeId: "income", editorController: controller)
            model.start()
            let pending = try #require(model.loadingTask)
            await controller.gate.waitUntilEntered()
            model.stop()
            controller.gate.open()
            await pending.value
            #expect(model.amount.isEmpty)
        }
    }

    @Test(arguments: [true, false])
    func closedEditorCannotDeliverASaveCompletion(expense: Bool) async throws {
        let controller = ControlledNativeEditor()
        var completions = 0
        if expense {
            let model = NativeExpenseEditorViewModel(expenseId: "expense", editorController: controller)
            model.save(updateWholeSeries: false) { _ in completions += 1 }
            let pending = try #require(model.savingTask)
            await controller.gate.waitUntilEntered()
            model.stop()
            controller.gate.open()
            await pending.value
            #expect(!model.isSaving)
        } else {
            let model = NativeTransactionEditorViewModel(initialKind: .income, initialYear: nil, initialMonth: nil, editorController: controller)
            model.save { _ in completions += 1 }
            let pending = try #require(model.savingTask)
            await controller.gate.waitUntilEntered()
            model.stop()
            controller.gate.open()
            await pending.value
            #expect(!model.isSaving)
        }
        #expect(completions == 0)
    }
}

@MainActor
private final class NativeEditorGate {
    private var isOpen = false
    private var entered = false
    private var waiting: [CheckedContinuation<Void, Never>] = []
    private var entering: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        entered = true
        entering.forEach { $0.resume() }
        entering.removeAll()
        if !isOpen { await withCheckedContinuation { waiting.append($0) } }
    }
    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { entering.append($0) } }
    }
    func open() {
        isOpen = true
        waiting.forEach { $0.resume() }
        waiting.removeAll()
    }
}

@MainActor
private final class ControlledNativeEditor: NativeExpenseEditing, NativeTransactionEditing {
    let gate = NativeEditorGate()
    func loadExpense(id: String) async throws -> IosExpenseEditorSnapshot? {
        await gate.wait()
        return IosExpenseEditorSnapshot(id: id, amountInput: "33.00", dateMillis: 1, categoryId: "food", descriptionText: "late", isShared: false, isRecurringMonthly: false, recurringSeriesId: nil)
    }
    func loadIncome(id: String) async throws -> IosNativeIncomeEditorSnapshot? {
        await gate.wait()
        return IosNativeIncomeEditorSnapshot(id: id, amountInput: "33.00", dateMillis: 1, categoryId: nil, descriptionText: "late", isRecurringMonthly: false, recurringSeriesId: nil)
    }
    func saveExpense(
        expenseId: String, amountInput: String, dateMillis: Int64, categoryId: String, description: String,
        isShared: Bool, isRecurringMonthly: Bool, updateWholeSeries: Bool
    ) async throws -> IosExpenseEditorOperationResult {
        await gate.wait()
        return IosExpenseEditorOperationResult(isSuccess: true, errorKey: nil)
    }
    func saveExpense(
        amountInput: String, dateMillis: Int64, categoryId: String, description: String,
        isShared: Bool, isRecurringMonthly: Bool, installmentCount: Int32
    ) async throws -> IosNativeTransactionEditorResult {
        await gate.wait()
        return IosNativeTransactionEditorResult(isSuccess: true, errorKey: nil)
    }
    func saveIncome(
        amountInput: String, dateMillis: Int64, categoryId: String?, description: String, isRecurringMonthly: Bool
    ) async throws -> IosNativeTransactionEditorResult {
        await gate.wait()
        return IosNativeTransactionEditorResult(isSuccess: true, errorKey: nil)
    }
    func saveExistingIncome(
        incomeId: String, amountInput: String, dateMillis: Int64, categoryId: String?, description: String,
        isRecurringMonthly: Bool, updateWholeSeries: Bool
    ) async throws -> IosNativeTransactionEditorResult {
        await gate.wait()
        return IosNativeTransactionEditorResult(isSuccess: true, errorKey: nil)
    }
    func deleteIncome(incomeId: String, deleteWholeSeries: Bool) async throws -> IosNativeTransactionEditorResult {
        await gate.wait()
        return IosNativeTransactionEditorResult(isSuccess: true, errorKey: nil)
    }
    func deleteExpense(expenseId: String, deleteWholeSeries: Bool) async throws -> IosExpenseEditorOperationResult {
        await gate.wait()
        return IosExpenseEditorOperationResult(isSuccess: true, errorKey: nil)
    }
}
