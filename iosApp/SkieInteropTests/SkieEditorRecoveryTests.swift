// Fixture signals use thread-safe Kotlin StateFlow/Channel; ObjC cannot express Sendable.
@preconcurrency import ComposeApp
import Foundation
import Testing
@testable import Spesify

@Suite(.serialized)
@MainActor
struct SkieEditorRecoveryTests {
    @Test func existingExpenseRetriesOnlyCategoriesAndPreservesEdits() async throws {
        let fixture = SkieReadFixture()
        let controller = LoadedExpenseEditor()
        let model = NativeExpenseEditorViewModel(
            expenseId: "expense", editorController: controller,
            categoriesController: fixture.categoriesController, refreshWidget: {}
        )
        model.start()
        await model.loadingTask?.value
        model.amount = "98.76"
        model.description = "Unsaved changes"
        model.selectedCategoryId = "expense"
        model.isShared = true
        let date = model.selectedDate
        try await fixture.awaitStarted(count: 1)
        let first = try #require(model.categoriesReadState.task)
        fixture.sendData()
        fixture.failRead()
        await first.value
        #expect(model.categoriesReadState.showsError)
        #expect(model.categories.map(\.id) == ["expense"])
        model.retryCategories()
        let retry = try #require(model.categoriesReadState.task)
        try await fixture.awaitStarted(count: 2)
        fixture.sendData()
        fixture.completeRead()
        await retry.value
        #expect(model.amount == "98.76")
        #expect(model.description == "Unsaved changes")
        #expect(model.selectedCategoryId == "expense")
        #expect(model.selectedDate == date)
        #expect(model.isShared)
        #expect(!model.categoriesReadState.showsError)
        #expect(controller.loads == 1)
        #expect(fixture.maximumActiveCollectors == 1)
        model.stop()
    }

    @Test(arguments: [AddTransactionKind.expense, .income])
    func transactionCategoriesRetryPreservesForm(kind: AddTransactionKind) async throws {
        let fixture = SkieReadFixture()
        let model = NativeTransactionEditorViewModel(
            initialKind: kind, initialYear: nil, initialMonth: nil,
            categoriesController: fixture.categoriesController, refreshWidget: {}
        )
        model.start()
        model.amount = "98.76"
        model.description = "Unsaved changes"
        model.selectedCategoryId = kind.categoryType
        model.isRecurringMonthly = true
        let date = model.selectedDate
        try await fixture.awaitStarted(count: 1)
        let first = try #require(model.categoriesReadState.task)
        fixture.sendData()
        fixture.failRead()
        await first.value
        #expect(model.categoriesReadState.showsError)
        #expect(model.categories.map(\.id) == [kind.categoryType])
        model.retryCategories()
        let retry = try #require(model.categoriesReadState.task)
        try await fixture.awaitStarted(count: 2)
        fixture.sendData()
        fixture.completeRead()
        await retry.value
        #expect(model.amount == "98.76")
        #expect(model.description == "Unsaved changes")
        #expect(model.selectedCategoryId == kind.categoryType)
        #expect(model.selectedDate == date)
        #expect(model.selectedKind == kind)
        #expect(model.isRecurringMonthly)
        #expect(!model.categoriesReadState.showsError)
        #expect(fixture.maximumActiveCollectors == 1)
        model.stop()
    }

    @Test func categoryTypeChangeWaitsForCleanupAndRejectsOldGeneration() async throws {
        let fixture = SkieReadFixture()
        let model = NativeTransactionEditorViewModel(
            initialKind: .expense, initialYear: nil, initialMonth: nil,
            categoriesController: fixture.categoriesController, refreshWidget: {}
        )
        model.start()
        try await fixture.awaitStarted(count: 1)
        model.amount = "98.76"
        model.selectedKind = .income
        try await fixture.awaitStarted(count: 2)
        #expect(fixture.cleanedCollectors == 1)
        #expect(fixture.lastCategoryType == "income")
        let current = try #require(model.categoriesReadState.task)
        fixture.sendData()
        fixture.completeRead()
        await current.value
        #expect(model.categories.map(\.id) == ["income"])
        #expect(model.amount == "98.76")
        #expect(fixture.maximumActiveCollectors == 1)
        model.stop()
    }

    @Test func closingDuringCategoriesRetryPreventsLateCategoriesAndDialog() async throws {
        let fixture = SkieReadFixture()
        let model = NativeTransactionEditorViewModel(
            initialKind: .expense, initialYear: nil, initialMonth: nil,
            categoriesController: fixture.categoriesController, refreshWidget: {}
        )
        model.start()
        try await fixture.awaitStarted(count: 1)
        let first = try #require(model.categoriesReadState.task)
        fixture.failRead()
        await first.value
        #expect(model.categoriesReadState.isUnavailable)
        model.retryCategories()
        try await fixture.awaitStarted(count: 2)
        let retry = try #require(model.categoriesReadState.task)
        model.stop()
        fixture.sendData()
        fixture.failRead()
        await retry.value
        try await fixture.awaitCleanup(count: 2)
        #expect(model.categories.isEmpty)
        #expect(!model.categoriesReadState.showsError)
        #expect(fixture.activeCollectors == 0)
    }
}

@MainActor
private final class LoadedExpenseEditor: NativeExpenseEditing {
    var loads = 0
    func loadExpense(id: String) async throws -> IosExpenseEditorSnapshot? {
        loads += 1
        return IosExpenseEditorSnapshot(
            id: id, amountInput: "12.34", dateMillis: 1_794_182_400_000,
            categoryId: "expense", descriptionText: "Original", isShared: false,
            isRecurringMonthly: false, recurringSeriesId: nil
        )
    }
    func saveExpense(
        expenseId: String, amountInput: String, dateMillis: Int64, categoryId: String,
        description: String, isShared: Bool, isRecurringMonthly: Bool, updateWholeSeries: Bool
    ) async throws -> IosExpenseEditorOperationResult {
        Issue.record("A categories retry must not save the editor")
        return IosExpenseEditorOperationResult(isSuccess: false, errorKey: nil)
    }
    func deleteExpense(expenseId: String, deleteWholeSeries: Bool) async throws -> IosExpenseEditorOperationResult {
        Issue.record("A categories retry must not delete the editor")
        return IosExpenseEditorOperationResult(isSuccess: false, errorKey: nil)
    }
}
