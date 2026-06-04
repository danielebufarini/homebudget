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

    private let expenseId: String
    private let editorController = IosExpenseEditorController()
    private let categoriesController = IosCategoriesController()
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private var categoriesTask: Task<Void, Never>?

    init(expenseId: String) {
        self.expenseId = expenseId
    }

    deinit {
        categoriesTask?.cancel()
    }

    func start() {
        if hasStarted {
            startCategoryObservationIfNeeded()
            return
        }

        hasStarted = true
        startCategoryObservationIfNeeded()

        Task { [weak self, editorController, expenseId] in
            guard let self else {
                return
            }

            guard let snapshot = try? await editorController.loadExpense(id: expenseId) else {
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
        categoriesTask?.cancel()
        categoriesTask = nil
    }

    private func startCategoryObservationIfNeeded() {
        guard categoriesTask == nil else {
            return
        }

        categoriesTask = Task { [weak self, categoriesController] in
            for await snapshot in categoriesController.snapshots() {
                guard let self else {
                    return
                }
                categories = snapshot.categories.map { item in
                    NativeExpenseCategory(
                        id: item.id,
                        name: item.name,
                        iconKey: item.iconKey
                    )
                }
            }
        }
    }

    var selectedCategory: NativeExpenseCategory? {
        categories.first(where: { $0.id == selectedCategoryId })
    }

    var canEdit: Bool {
        !didFailToLoad && !isLoading
    }

    var hasRecurringSeries: Bool {
        recurringSeriesId?.isEmpty == false
    }

    var recurringMonthlyYears: Int {
        Int(ExpenseInstallmentsKt.RECURRING_MONTHLY_OCCURRENCES / 12)
    }

    func save(
        updateWholeSeries: Bool,
        onComplete: @escaping (String?) -> Void
    ) {
        isSaving = true
        Task { [weak self, editorController] in
            guard let self else {
                return
            }

            let result: IosExpenseEditorOperationResult
            do {
                result = try await editorController.saveExpense(
                    expenseId: expenseId,
                    amountInput: amount,
                    dateMillis: Int64(selectedDate.timeIntervalSince1970 * 1000.0),
                    categoryId: selectedCategoryId,
                    description: description,
                    isShared: isShared,
                    isRecurringMonthly: isRecurringMonthly,
                    updateWholeSeries: updateWholeSeries
                )
            } catch {
                isSaving = false
                onComplete(error.localizedDescription)
                return
            }

            isSaving = false
            onComplete(result.isSuccess ? nil : result.errorKey)
        }
    }

    func delete(
        deleteWholeSeries: Bool,
        onComplete: @escaping (String?) -> Void
    ) {
        isSaving = true
        Task { [weak self, editorController, expenseId] in
            guard let self else {
                return
            }

            let result: IosExpenseEditorOperationResult
            do {
                result = try await editorController.deleteExpense(
                    expenseId: expenseId,
                    deleteWholeSeries: deleteWholeSeries
                )
            } catch {
                isSaving = false
                onComplete(error.localizedDescription)
                return
            }

            isSaving = false
            onComplete(result.isSuccess ? nil : result.errorKey)
        }
    }

    func insertCategory(
        name: String,
        iconKey: String,
        onComplete: @escaping (String?) -> Void
    ) {
        Task { [weak self, categoriesController] in
            let categoryId: String?
            do {
                categoryId = try await categoriesController.insertCategoryAndReturnId(
                    name: name,
                    iconKey: iconKey
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
}

let nativeExpenseEditorIconSections: [NativeExpenseEditorIconSection] = [
    NativeExpenseEditorIconSection(id: "home", title: appLocalized("Home & Bills"), iconKeys: ["home", "receipt", "build"]),
    NativeExpenseEditorIconSection(id: "food", title: appLocalized("Food & Dining"), iconKeys: ["shopping_cart", "restaurant", "local_cafe", "cake"]),
    NativeExpenseEditorIconSection(id: "travel", title: appLocalized("Transport & Travel"), iconKeys: ["directions_car", "directions_bus", "train", "local_taxi", "flight", "hotel", "beach_access"]),
    NativeExpenseEditorIconSection(id: "health", title: appLocalized("Health & Wellness"), iconKeys: ["local_hospital", "healing", "fitness_center", "spa"]),
    NativeExpenseEditorIconSection(id: "people", title: appLocalized("People & Work"), iconKeys: ["person", "work", "school"]),
    NativeExpenseEditorIconSection(id: "general", title: appLocalized("General & Hobbies"), iconKeys: ["pets", "category"])
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
