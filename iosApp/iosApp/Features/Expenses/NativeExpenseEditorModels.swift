@preconcurrency import ComposeApp
import SwiftUI
import Observation

struct NativeExpenseCategory: Identifiable, Hashable {
    let id: String
    let name: String
    let iconKey: String
}

struct NativeExpenseEditorIconSection: Identifiable {
    let id: String
    let title: String
    let iconKeys: [String]
}

@MainActor
@Observable
final class NativeExpenseEditorViewModel {
    var isLoading = true
    var didFailToLoad = false
    var amount = ""
    var selectedDate = Date()
    var selectedCategoryId = ""
    var description = ""
    var isShared = false
    var isRecurringMonthly = false
    var recurringSeriesId: String?
    var categories: [NativeExpenseCategory] = []
    var isSaving = false
    let categoriesReadState = SnapshotReadState()

    private let expenseId: String
    private let editorController: any NativeExpenseEditing
    private let categoriesController: IosCategoriesController
    private let categoryWriter: any NativeCategoryWriting
    private let writeTasks: ApplicationWriteTasks
    private let refreshWidget: @MainActor () -> Void
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private(set) var loadingTask: Task<Void, Never>?
    @ObservationIgnored private(set) var savingTask: Task<Void, Never>?
    @ObservationIgnored private(set) var categoryWriteTask: Task<Void, Never>?
    @ObservationIgnored private var lifecycleGeneration = 0
    @ObservationIgnored private var isStopped = false

    init(
        expenseId: String, editorController: any NativeExpenseEditing = IosExpenseEditorController(),
        categoriesController: IosCategoriesController = IosCategoriesController(),
        categoryWriter: (any NativeCategoryWriting)? = nil,
        writeTasks: ApplicationWriteTasks = .shared,
        refreshWidget: @escaping @MainActor () -> Void = { SpesifyWidgetSummaryRefresher.shared.refresh() }
    ) {
        self.expenseId = expenseId
        self.editorController = editorController
        self.categoriesController = categoriesController
        self.categoryWriter = categoryWriter ?? categoriesController
        self.writeTasks = writeTasks
        self.refreshWidget = refreshWidget
    }

    deinit {
        loadingTask?.cancel()
        savingTask?.cancel()
        categoryWriteTask?.cancel()
    }

    func start() {
        isStopped = false
        if hasStarted && !isLoading {
            startCategoryObservationIfNeeded()
            return
        }

        hasStarted = true
        startCategoryObservationIfNeeded()

        loadingTask?.cancel()
        let generation = lifecycleGeneration
        loadingTask = Task { [weak self, editorController, expenseId] in
            guard !Task.isCancelled else { return }
            let snapshot = try? await editorController.loadExpense(id: expenseId)
            guard let self, !Task.isCancelled, !isStopped, generation == lifecycleGeneration else { return }
            guard let snapshot else {
                self.isLoading = false
                self.didFailToLoad = true
                return
            }

            amount = snapshot.amountInput
            selectedDate = Date(timeIntervalSince1970: Double(snapshot.dateMillis) / 1000.0)
            selectedCategoryId = snapshot.categoryId
            description = snapshot.descriptionText
            isShared = snapshot.isShared
            isRecurringMonthly = snapshot.isRecurringMonthly
            recurringSeriesId = snapshot.recurringSeriesId
            didFailToLoad = false
            isLoading = false
        }
    }

    func stop() {
        isStopped = true
        lifecycleGeneration += 1
        categoriesReadState.stop()
        loadingTask?.cancel()
        savingTask?.cancel()
        categoryWriteTask?.cancel()
        isSaving = false
    }

    private func startCategoryObservationIfNeeded() {
        guard categoriesReadState.task == nil || categoriesReadState.isStopped else { return }
        observeCategories()
    }

    func retryCategories() {
        guard !isStopped else { return }
        observeCategories()
    }

    private func observeCategories() {
        let lifecycle = lifecycleGeneration
        categoriesReadState.start { [weak self, categoriesController] generation in
            for await state in categoriesController.states() {
                guard let self, !isStopped, lifecycle == lifecycleGeneration,
                      categoriesReadState.accepts(generation) else { return }
                switch onEnum(of: state) {
                case let .data(data):
                    categories = data.snapshot.categories.map { item in
                        NativeExpenseCategory(id: item.id, name: item.name, iconKey: item.iconKey)
                    }
                    categoriesReadState.receivedData()
                case .failed:
                    categoriesReadState.receivedFailure()
                }
            }
        }
    }

    var selectedCategory: NativeExpenseCategory? {
        categories.first(where: { $0.id == selectedCategoryId })
    }

    var categoryValue: String {
        if categoriesReadState.isUnavailable { return appLocalized("Data unavailable") }
        return selectedCategory?.name ?? appLocalized("Select Category")
    }

    var canEdit: Bool {
        !didFailToLoad && !isLoading
    }

    var hasRecurringSeries: Bool {
        recurringSeriesId?.isEmpty == false
    }

    var hasValidAmount: Bool {
        nativeFormattedPositiveAmountResult(amount) != nil
    }

    var recurringMonthlyYears: Int {
        Int(RECURRING_MONTHLY_OCCURRENCES / 12)
    }

    func save(
        updateWholeSeries: Bool,
        onComplete: @escaping (String?) -> Void
    ) {
        guard !isStopped, !isSaving else { return }
        let amount = self.amount
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)
        let selectedCategoryId = self.selectedCategoryId
        let description = self.description
        let isShared = self.isShared
        let isRecurringMonthly = self.isRecurringMonthly
        performWrite(onComplete: onComplete) { [editorController, expenseId] in
            NativeEditorWriteOutcome(try await editorController.saveExpense(
                expenseId: expenseId,
                amountInput: amount,
                dateMillis: dateMillis,
                categoryId: selectedCategoryId,
                description: description,
                isShared: isShared,
                isRecurringMonthly: isRecurringMonthly,
                updateWholeSeries: updateWholeSeries
            ))
        }
    }

    func delete(
        deleteWholeSeries: Bool,
        onComplete: @escaping (String?) -> Void
    ) {
        guard !isStopped, !isSaving else { return }
        performWrite(onComplete: onComplete) { [editorController, expenseId] in
            NativeEditorWriteOutcome(try await editorController.deleteExpense(
                expenseId: expenseId,
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
        // Only this UI waiter belongs to the editor; its cancellation cannot cancel the worker.
        savingTask = Task { [weak self] in
            do {
                let result = try await worker.value
                guard let self, !Task.isCancelled, !isStopped, generation == lifecycleGeneration else { return }
                isSaving = false
                onComplete(result.isSuccess ? nil : result.errorKey)
            } catch {
                guard let self, !isStopped, generation == lifecycleGeneration else { return }
                isSaving = false
                guard !Task.isCancelled, !(error is CancellationError) else { return }
                onComplete(error.localizedDescription)
            }
        }
    }

    func insertCategory(
        name: String,
        iconKey: String,
        onComplete: @escaping (String?) -> Void
    ) {
        guard !isStopped else { return }
        categoryWriteTask?.cancel()
        let generation = lifecycleGeneration
        let worker = writeTasks.perform { [categoryWriter, refreshWidget] in
            let categoryId = try await categoryWriter.insertCategoryAndReturnId(name: name, iconKey: iconKey)
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
            guard let self, !Task.isCancelled, !isStopped, generation == lifecycleGeneration else {
                return
            }

            if let categoryId {
                selectedCategoryId = categoryId
            }
            onComplete(categoryId)
        }
    }
}

let nativeExpenseEditorIconSections: [NativeExpenseEditorIconSection] = [
    NativeExpenseEditorIconSection(id: "home", title: appLocalized("Home & Bills"), iconKeys: ["home", "receipt", "build"]),
    NativeExpenseEditorIconSection(id: "food", title: appLocalized("Food & Dining"), iconKeys: ["shopping_cart", "restaurant", "local_cafe", "cake"]),
    NativeExpenseEditorIconSection(id: "travel", title: appLocalized("Transport & Travel"), iconKeys: ["directions_car", "directions_bus", "train", "local_taxi", "flight", "hotel", "beach_access"]),
    NativeExpenseEditorIconSection(id: "health", title: appLocalized("Health & Wellness"), iconKeys: ["local_hospital", "healing", "fitness_center", "spa"]),
    NativeExpenseEditorIconSection(id: "people", title: appLocalized("People & Work"), iconKeys: ["person", "work", "school"]),
    NativeExpenseEditorIconSection(id: "general", title: appLocalized("General & Hobbies"), iconKeys: ["attach_money", "savings", "pets", "category"])
]

func nativeExpenseEditorDateString(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale.current
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: date)
}

func nativeExpenseCategorySystemImageName(_ iconKey: String?) -> String {
    switch nativeExpenseNormalizedCategoryIconKey(iconKey) {
    case "home":
        return "house.fill"
    case "build":
        return "hammer.fill"
    case "shopping_cart":
        return "cart.fill"
    case "restaurant":
        return "fork.knife"
    case "local_cafe":
        return "cup.and.saucer.fill"
    case "cake":
        return "birthday.cake.fill"
    case "directions_car":
        return "car.fill"
    case "directions_bus":
        return "bus.fill"
    case "train":
        return "tram.fill"
    case "local_taxi":
        return "car.side.fill"
    case "flight":
        return "airplane"
    case "hotel":
        return "bed.double.fill"
    case "beach_access":
        return "beach.umbrella.fill"
    case "local_hospital":
        return "cross.case.fill"
    case "healing":
        return "bandage.fill"
    case "fitness_center":
        return "figure.strengthtraining.traditional"
    case "spa":
        return "leaf.fill"
    case "person":
        return "person.fill"
    case "work":
        return "briefcase.fill"
    case "school":
        return "graduationcap.fill"
    case "attach_money":
        return "dollarsign.circle.fill"
    case "savings":
        return "banknote.fill"
    case "pets":
        return "pawprint.fill"
    default:
        return "square.grid.2x2.fill"
    }
}

func nativeExpenseNormalizedCategoryIconKey(_ iconKey: String?) -> String {
    switch iconKey?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case nil, "":
        return "category"
    case "household_expenses":
        return "home"
    case "food":
        return "shopping_cart"
    case "car_expenses":
        return "directions_car"
    case "travel":
        return "flight"
    case "healthcare_expenses":
        return "local_hospital"
    case "bills":
        return "receipt"
    case "personal_expenses", "personal_expeses":
        return "person"
    case "miscellaneous":
        return "category"
    case let key?:
        return key
    }
}

extension Array {
    func chunked(into size: Int) -> [ArraySlice<Element>] {
        guard size > 0 else {
            return []
        }

        return stride(from: 0, to: count, by: size).map { start in
            self[start ..< Swift.min(start + size, count)]
        }
    }
}
