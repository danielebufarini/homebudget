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
    private let editorController: any NativeTransactionEditing
    private let categoriesController = IosCategoriesController()
    private let categoryWriter: any NativeCategoryWriting
    private let writeTasks: ApplicationWriteTasks
    private let refreshWidget: @MainActor () -> Void
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private var categoriesTask: Task<Void, Never>?
    @ObservationIgnored private(set) var loadingTask: Task<Void, Never>?
    @ObservationIgnored private(set) var savingTask: Task<Void, Never>?
    @ObservationIgnored private(set) var categoryWriteTask: Task<Void, Never>?
    @ObservationIgnored private var lifecycleGeneration = 0
    @ObservationIgnored private var categoriesGeneration = 0
    @ObservationIgnored private var isStopped = false

    init(
        initialKind: AddTransactionKind, initialYear: Int?, initialMonth: Int?,
        editorController: any NativeTransactionEditing = IosNativeTransactionEditorController(),
        categoryWriter: (any NativeCategoryWriting)? = nil,
        writeTasks: ApplicationWriteTasks = .shared,
        refreshWidget: @escaping @MainActor () -> Void = { SpesifyWidgetSummaryRefresher.shared.refresh() }
    ) {
        self.editorController = editorController
        self.categoryWriter = categoryWriter ?? categoriesController
        self.writeTasks = writeTasks
        self.refreshWidget = refreshWidget
        incomeId = nil
        selectedKind = initialKind
        selectedDate = Self.initialDate(year: initialYear, month: initialMonth)
        isLoading = false
    }

    init(
        prefilledExpense: NativeExpenseEditorPrefill,
        editorController: any NativeTransactionEditing = IosNativeTransactionEditorController(),
        categoryWriter: (any NativeCategoryWriting)? = nil,
        writeTasks: ApplicationWriteTasks = .shared,
        refreshWidget: @escaping @MainActor () -> Void = { SpesifyWidgetSummaryRefresher.shared.refresh() }
    ) {
        self.editorController = editorController
        self.categoryWriter = categoryWriter ?? categoriesController
        self.writeTasks = writeTasks
        self.refreshWidget = refreshWidget
        incomeId = nil
        selectedKind = .expense
        selectedDate = Self.date(fromMillis: prefilledExpense.dateMillis) ?? Date()
        amount = prefilledExpense.amountInput
        selectedCategoryId = prefilledExpense.categoryId ?? ""
        description = prefilledExpense.descriptionText
        isLoading = false
    }

    init(
        incomeId: String, editorController: any NativeTransactionEditing = IosNativeTransactionEditorController(),
        categoryWriter: (any NativeCategoryWriting)? = nil,
        writeTasks: ApplicationWriteTasks = .shared,
        refreshWidget: @escaping @MainActor () -> Void = { SpesifyWidgetSummaryRefresher.shared.refresh() }
    ) {
        self.editorController = editorController
        self.categoryWriter = categoryWriter ?? categoriesController
        self.writeTasks = writeTasks
        self.refreshWidget = refreshWidget
        self.incomeId = incomeId
        selectedKind = .income
        selectedDate = Date()
        isLoading = true
    }

    deinit {
        categoriesTask?.cancel()
        loadingTask?.cancel()
        savingTask?.cancel()
        categoryWriteTask?.cancel()
    }

    func start() {
        isStopped = false
        if hasStarted {
            if categoriesTask == nil {
                reloadCategories()
            }
            if isLoading { loadIncomeIfNeeded() }
            return
        }

        hasStarted = true
        reloadCategories()
        loadIncomeIfNeeded()
    }

    func stop() {
        isStopped = true
        lifecycleGeneration += 1
        categoriesTask?.cancel()
        categoriesTask = nil
        loadingTask?.cancel()
        savingTask?.cancel()
        categoryWriteTask?.cancel()
        isSaving = false
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
        guard !isStopped, !isSaving else { return }
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)
        let amount = self.amount
        let selectedCategoryId = self.selectedCategoryId
        let description = self.description
        let isShared = self.isShared
        let isRecurringMonthly = self.isRecurringMonthly
        let installmentCount = self.installmentCount
        let incomeId = self.incomeId

        switch selectedKind {
        case .expense:
            performWrite(onComplete: onComplete) { [editorController] in
                NativeEditorWriteOutcome(try await editorController.saveExpense(
                    amountInput: amount,
                    dateMillis: dateMillis,
                    categoryId: selectedCategoryId,
                    description: description,
                    isShared: isShared,
                    isRecurringMonthly: isRecurringMonthly,
                    installmentCount: Int32(installmentCount)
                ))
            }
        case .income:
            performWrite(onComplete: onComplete) { [editorController] in
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
                return NativeEditorWriteOutcome(result)
            }
        }
    }

    func saveIncome(updateWholeSeries: Bool, onComplete: @escaping (String?) -> Void) {
        guard let incomeId else {
            save(onComplete: onComplete)
            return
        }

        guard !isStopped, !isSaving else { return }
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)
        let amount = self.amount
        let selectedCategoryId = self.selectedCategoryId
        let description = self.description
        let isRecurringMonthly = self.isRecurringMonthly
        performWrite(onComplete: onComplete) { [editorController] in
            NativeEditorWriteOutcome(try await editorController.saveExistingIncome(
                incomeId: incomeId,
                amountInput: amount,
                dateMillis: dateMillis,
                categoryId: selectedCategoryId.isEmpty ? nil : selectedCategoryId,
                description: description,
                isRecurringMonthly: isRecurringMonthly,
                updateWholeSeries: updateWholeSeries
            ))
        }
    }

    func deleteIncome(deleteWholeSeries: Bool, onComplete: @escaping (String?) -> Void) {
        guard let incomeId, !isStopped, !isSaving else { return }
        performWrite(onComplete: onComplete) { [editorController] in
            NativeEditorWriteOutcome(try await editorController.deleteIncome(
                incomeId: incomeId,
                deleteWholeSeries: deleteWholeSeries
            ))
        }
    }

    private func performWrite(
        onComplete: @escaping (String?) -> Void,
        operation: @escaping @MainActor () async throws -> NativeEditorWriteOutcome
    ) {
        isSaving = true
        let generation = lifecycleGeneration
        let worker = writeTasks.perform { [refreshWidget] in
            let result = try await operation()
            if result.isSuccess { refreshWidget() }
            return result
        }
        savingTask = Task { [weak self] in
            do {
                let result = try await worker.value
                guard let self, !Task.isCancelled, !isStopped, generation == lifecycleGeneration else { return }
                completeSave(result: result, onComplete: onComplete)
            } catch {
                guard let self, !isStopped, generation == lifecycleGeneration else { return }
                isSaving = false
                guard !Task.isCancelled, !(error is CancellationError) else { return }
                onComplete(error.localizedDescription)
            }
        }
    }

    func insertCategory(name: String, iconKey: String, onComplete: @escaping (String?) -> Void) {
        guard !isStopped else { return }
        categoryWriteTask?.cancel()
        let generation = lifecycleGeneration
        let categoryType = selectedKind.categoryType
        let worker = writeTasks.perform { [categoryWriter, refreshWidget] in
            let categoryId = try await categoryWriter.insertCategoryAndReturnIdForCategoryType(
                name: name, iconKey: iconKey, categoryType: categoryType
            )
            if categoryId != nil { refreshWidget() }
            return categoryId
        }
        categoryWriteTask = Task { [weak self] in
            let categoryId: String?
            do {
                categoryId = try await worker.value
            } catch is CancellationError {
                return
            } catch {
                categoryId = nil
            }
            guard let self, !Task.isCancelled, !isStopped, generation == lifecycleGeneration,
                  selectedKind.categoryType == categoryType else {
                return
            }

            if let categoryId {
                selectedCategoryId = categoryId
            }
            onComplete(categoryId)
        }
    }

    private func reloadCategories() {
        guard !isStopped else { return }
        categoriesTask?.cancel()
        categoriesGeneration += 1
        let generation = categoriesGeneration
        let categoryType = selectedKind.categoryType
        categoriesTask = Task { [weak self, categoriesController] in
            for await snapshot in categoriesController.snapshotsForCategoryType(categoryType: categoryType) {
                guard let self, !Task.isCancelled, !isStopped, generation == categoriesGeneration else {
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

        loadingTask?.cancel()
        let generation = lifecycleGeneration
        loadingTask = Task { [weak self, editorController] in
            guard !Task.isCancelled else { return }
            let snapshot = try? await editorController.loadIncome(id: incomeId)
            guard let self, !Task.isCancelled, !isStopped, generation == lifecycleGeneration else { return }
            guard let snapshot else {
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
        result: NativeEditorWriteOutcome,
        onComplete: @escaping (String?) -> Void
    ) {
        isSaving = false
        onComplete(result.isSuccess ? nil : result.errorKey)
    }

    private static func date(fromMillis millis: Int64?) -> Date? {
        guard let millis, millis > 0 else {
            return nil
        }

        return Date(timeIntervalSince1970: Double(millis) / 1000.0)
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
