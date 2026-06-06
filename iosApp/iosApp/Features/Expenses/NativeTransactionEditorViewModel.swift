@preconcurrency import ComposeApp
import Foundation
import Observation

@MainActor
@Observable
final class NativeTransactionEditorViewModel {
    var selectedKind: AddTransactionKind {
        didSet {
            guard editableKindSelection, oldValue != selectedKind, hasStarted else {
                return
            }

            selectedCategoryId = ""
            isShared = false
            installmentCount = 1
            reloadCategories()
        }
    }

    var amount = ""
    var selectedDate: Date
    var selectedCategoryId = ""
    var description = ""
    var isShared = false
    var isRecurringMonthly = false
    var installmentCount = 1
    var categories: [NativeExpenseCategory] = []
    var isSaving = false
    var isLoading: Bool
    var didFailToLoad = false
    var recurringSeriesId: String?

    private let incomeId: String?
    private let editorController = IosNativeTransactionEditorController()
    private let categoriesController = IosCategoriesController()
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private var categoriesTask: Task<Void, Never>?

    init(initialKind: AddTransactionKind, initialYear: Int?, initialMonth: Int?) {
        incomeId = nil
        selectedKind = initialKind
        selectedDate = Self.initialDate(year: initialYear, month: initialMonth)
        isLoading = false
    }

    init(incomeId: String) {
        self.incomeId = incomeId
        selectedKind = .income
        selectedDate = Date()
        isLoading = true
    }

    deinit {
        categoriesTask?.cancel()
    }

    func start() {
        if hasStarted {
            if categoriesTask == nil {
                reloadCategories()
            }
            return
        }

        hasStarted = true
        reloadCategories()
        loadIncomeIfNeeded()
    }

    func stop() {
        categoriesTask?.cancel()
        categoriesTask = nil
    }

    var selectedCategory: NativeExpenseCategory? {
        categories.first { $0.id == selectedCategoryId }
    }

    var title: String {
        if isEditingIncome {
            return appLocalized("Edit Income")
        }
        return selectedKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense")
    }

    var categoryValue: String {
        selectedCategory?.name ?? appLocalized("Select Category")
    }

    var isIncome: Bool {
        selectedKind == .income
    }

    var isEditingIncome: Bool {
        incomeId?.isEmpty == false
    }

    var editableKindSelection: Bool {
        !isEditingIncome
    }

    var canEdit: Bool {
        !isLoading && !didFailToLoad
    }

    var hasRecurringSeries: Bool {
        recurringSeriesId?.isEmpty == false
    }

    var recurringMonthlyYears: Int {
        Int(ExpenseInstallmentsKt.RECURRING_MONTHLY_OCCURRENCES / 12)
    }

    var hasValidAmount: Bool {
        nativeFormattedPositiveAmountResult(amount) != nil
    }

    func setRecurringMonthly(_ enabled: Bool) {
        isRecurringMonthly = enabled
        if enabled {
            installmentCount = 1
        }
    }

    func setInstallmentCount(_ count: Int) {
        let normalizedCount = min(max(count, 1), 30)
        installmentCount = normalizedCount
        if normalizedCount > 1 {
            setRecurringMonthly(false)
        }
    }

    func save(onComplete: @escaping (String?) -> Void) {
        isSaving = true
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)

        switch selectedKind {
        case .expense:
            Task { [weak self, editorController] in
                guard let self else {
                    return
                }

                do {
                    let result = try await editorController.saveExpense(
                        amountInput: amount,
                        dateMillis: dateMillis,
                        categoryId: selectedCategoryId,
                        description: description,
                        isShared: isShared,
                        isRecurringMonthly: isRecurringMonthly,
                        installmentCount: Int32(installmentCount)
                    )
                    completeSave(result: result, onComplete: onComplete)
                } catch {
                    isSaving = false
                    onComplete(error.localizedDescription)
                }
            }
        case .income:
            Task { [weak self, editorController] in
                guard let self else {
                    return
                }

                do {
                    let categoryId = selectedCategoryId.isEmpty ? nil : selectedCategoryId
                    let result: IosNativeTransactionEditorResult
                    if let incomeId {
                        result = try await editorController.saveExistingIncome(
                            incomeId: incomeId,
                            amountInput: amount,
                            dateMillis: dateMillis,
                            categoryId: categoryId,
                            description: description,
                            isRecurringMonthly: isRecurringMonthly,
                            updateWholeSeries: false
                        )
                    } else {
                        result = try await editorController.saveIncome(
                            amountInput: amount,
                            dateMillis: dateMillis,
                            categoryId: categoryId,
                            description: description,
                            isRecurringMonthly: isRecurringMonthly
                        )
                    }
                    completeSave(result: result, onComplete: onComplete)
                } catch {
                    isSaving = false
                    onComplete(error.localizedDescription)
                }
            }
        }
    }

    func saveIncome(updateWholeSeries: Bool, onComplete: @escaping (String?) -> Void) {
        guard let incomeId else {
            save(onComplete: onComplete)
            return
        }

        isSaving = true
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)
        Task { [weak self, editorController] in
            guard let self else {
                return
            }

            do {
                let result = try await editorController.saveExistingIncome(
                    incomeId: incomeId,
                    amountInput: amount,
                    dateMillis: dateMillis,
                    categoryId: selectedCategoryId.isEmpty ? nil : selectedCategoryId,
                    description: description,
                    isRecurringMonthly: isRecurringMonthly,
                    updateWholeSeries: updateWholeSeries
                )
                completeSave(result: result, onComplete: onComplete)
            } catch {
                isSaving = false
                onComplete(error.localizedDescription)
            }
        }
    }

    func deleteIncome(deleteWholeSeries: Bool, onComplete: @escaping (String?) -> Void) {
        guard let incomeId else {
            return
        }

        isSaving = true
        Task { [weak self, editorController] in
            guard let self else {
                return
            }

            do {
                let result = try await editorController.deleteIncome(
                    incomeId: incomeId,
                    deleteWholeSeries: deleteWholeSeries
                )
                completeSave(result: result, onComplete: onComplete)
            } catch {
                isSaving = false
                onComplete(error.localizedDescription)
            }
        }
    }

    func insertCategory(name: String, iconKey: String, onComplete: @escaping (String?) -> Void) {
        let categoryType = selectedKind.categoryType
        Task { [weak self, categoriesController] in
            let categoryId: String?
            do {
                categoryId = try await categoriesController.insertCategoryAndReturnIdForCategoryType(
                    name: name,
                    iconKey: iconKey,
                    categoryType: categoryType
                )
            } catch {
                categoryId = nil
            }
            guard let self else {
                return
            }

            if let categoryId {
                selectedCategoryId = categoryId
            }
            onComplete(categoryId)
        }
    }

    private func reloadCategories() {
        categoriesTask?.cancel()
        let categoryType = selectedKind.categoryType
        categoriesTask = Task { [weak self, categoriesController] in
            for await snapshot in categoriesController.snapshotsForCategoryType(categoryType: categoryType) {
                guard let self else {
                    return
                }
                categories = snapshot.categories.map {
                    NativeExpenseCategory(id: $0.id, name: $0.name, iconKey: $0.iconKey)
                }
            }
        }
    }

    private func loadIncomeIfNeeded() {
        guard let incomeId else {
            return
        }

        Task { [weak self, editorController] in
            guard let self else {
                return
            }

            guard let snapshot = try? await editorController.loadIncome(id: incomeId) else {
                isLoading = false
                didFailToLoad = true
                return
            }

            amount = snapshot.amountInput
            selectedDate = Date(timeIntervalSince1970: Double(snapshot.dateMillis) / 1000.0)
            selectedCategoryId = snapshot.categoryId ?? ""
            description = snapshot.descriptionText
            isRecurringMonthly = snapshot.isRecurringMonthly
            recurringSeriesId = snapshot.recurringSeriesId
            didFailToLoad = false
            isLoading = false
        }
    }

    private func completeSave(
        result: IosNativeTransactionEditorResult,
        onComplete: @escaping (String?) -> Void
    ) {
        isSaving = false
        onComplete(result.isSuccess ? nil : result.errorKey)
    }

    private static func initialDate(year: Int?, month: Int?) -> Date {
        let calendar = Calendar(identifier: .gregorian)
        let now = Date()

        guard let year, let month else {
            return now
        }

        var components = calendar.dateComponents([.day], from: now)
        components.year = year
        components.month = month

        let firstDay = calendar.date(from: DateComponents(year: year, month: month, day: 1)) ?? now
        let maximumDay = calendar.range(of: .day, in: .month, for: firstDay)?.count ?? 28
        components.day = min(components.day ?? 1, maximumDay)

        return calendar.date(from: components) ?? now
    }
}
