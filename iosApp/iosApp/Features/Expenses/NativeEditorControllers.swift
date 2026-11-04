@preconcurrency import ComposeApp

@MainActor
protocol NativeTransactionEditing {
    func loadIncome(id: String) async throws -> IosNativeIncomeEditorSnapshot?
    func saveExpense(
        amountInput: String, dateMillis: Int64, categoryId: String, description: String,
        isShared: Bool, isRecurringMonthly: Bool, installmentCount: Int32
    ) async throws -> IosNativeTransactionEditorResult
    func saveIncome(
        amountInput: String, dateMillis: Int64, categoryId: String?, description: String,
        isRecurringMonthly: Bool
    ) async throws -> IosNativeTransactionEditorResult
    func saveExistingIncome(
        incomeId: String, amountInput: String, dateMillis: Int64, categoryId: String?, description: String,
        isRecurringMonthly: Bool, updateWholeSeries: Bool
    ) async throws -> IosNativeTransactionEditorResult
    func deleteIncome(incomeId: String, deleteWholeSeries: Bool) async throws -> IosNativeTransactionEditorResult
}

extension IosNativeTransactionEditorController: NativeTransactionEditing {}

@MainActor
protocol NativeExpenseEditing {
    func loadExpense(id: String) async throws -> IosExpenseEditorSnapshot?
    func saveExpense(
        expenseId: String, amountInput: String, dateMillis: Int64, categoryId: String, description: String,
        isShared: Bool, isRecurringMonthly: Bool, updateWholeSeries: Bool
    ) async throws -> IosExpenseEditorOperationResult
    func deleteExpense(expenseId: String, deleteWholeSeries: Bool) async throws -> IosExpenseEditorOperationResult
}

extension IosExpenseEditorController: NativeExpenseEditing {}

@MainActor
protocol NativeCategoryWriting {
    func insertCategoryAndReturnId(name: String, iconKey: String) async throws -> String?
    func insertCategoryAndReturnIdForCategoryType(
        name: String, iconKey: String, categoryType: String
    ) async throws -> String?
}

extension IosCategoriesController: NativeCategoryWriting {}

/// Copies Kotlin operation results before returning them across a Swift task boundary.
struct NativeEditorWriteOutcome: Sendable {
    let isSuccess: Bool
    let errorKey: String?

    @MainActor
    init(_ result: IosExpenseEditorOperationResult) {
        isSuccess = result.isSuccess
        errorKey = result.errorKey
    }

    @MainActor
    init(_ result: IosNativeTransactionEditorResult) {
        isSuccess = result.isSuccess
        errorKey = result.errorKey
    }
}
