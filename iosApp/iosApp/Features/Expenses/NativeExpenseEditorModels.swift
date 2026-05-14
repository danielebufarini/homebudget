@preconcurrency import ComposeApp
import SwiftUI

struct NativeExpenseCategory: Identifiable, Hashable {
    let id: String
    let name: String
    let iconKey: String
    let isCustom: Bool
}

struct NativeExpenseEditorIconSection: Identifiable {
    let id: String
    let title: String
    let iconKeys: [String]
}

@MainActor
final class NativeExpenseEditorViewModel: ObservableObject {
    @Published var isLoading = true
    @Published var didFailToLoad = false
    @Published var amount = ""
    @Published var selectedDate = Date()
    @Published var selectedCategoryId = ""
    @Published var description = ""
    @Published var isShared = false
    @Published var isRecurringMonthly = false
    @Published var recurringSeriesId: String?
    @Published var categories: [NativeExpenseCategory] = []
    @Published var isSaving = false

    private let expenseId: String
    private let editorController = IosExpenseEditorController()
    private let categoriesController = IosCategoriesController()
    private var hasStarted = false

    init(expenseId: String) {
        self.expenseId = expenseId
    }

    deinit {
        editorController.dispose()
        categoriesController.dispose()
    }

    func start() {
        guard !hasStarted else {
            return
        }

        hasStarted = true

        categoriesController.start { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.categories = snapshot.categories.map { item in
                    NativeExpenseCategory(
                        id: item.id,
                        name: item.name,
                        iconKey: item.iconKey,
                        isCustom: item.isCustom
                    )
                }
            }
        }

        editorController.loadExpense(id: expenseId) { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                guard let snapshot else {
                    self.isLoading = false
                    self.didFailToLoad = true
                    return
                }

                self.amount = snapshot.amountInput
                self.selectedDate = Date(timeIntervalSince1970: Double(snapshot.dateMillis) / 1000.0)
                self.selectedCategoryId = snapshot.categoryId
                self.description = snapshot.descriptionText
                self.isShared = snapshot.isShared
                self.isRecurringMonthly = snapshot.isRecurringMonthly
                self.recurringSeriesId = snapshot.recurringSeriesId
                self.didFailToLoad = false
                self.isLoading = false
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
        editorController.saveExpense(
            expenseId: expenseId,
            amountInput: amount,
            dateMillis: Int64(selectedDate.timeIntervalSince1970 * 1000.0),
            categoryId: selectedCategoryId,
            description: description,
            isShared: isShared,
            isRecurringMonthly: isRecurringMonthly,
            updateWholeSeries: updateWholeSeries
        ) { [weak self] result in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.isSaving = false
                onComplete(result.isSuccess ? nil : result.errorKey)
            }
        }
    }

    func delete(
        deleteWholeSeries: Bool,
        onComplete: @escaping (String?) -> Void
    ) {
        isSaving = true
        editorController.deleteExpense(
            expenseId: expenseId,
            deleteWholeSeries: deleteWholeSeries
        ) { [weak self] result in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.isSaving = false
                onComplete(result.isSuccess ? nil : result.errorKey)
            }
        }
    }

    func insertCategory(
        name: String,
        iconKey: String,
        onComplete: @escaping (String?) -> Void
    ) {
        categoriesController.insertCategoryAndReturnId(
            name: name,
            iconKey: iconKey
        ) { [weak self] categoryId in
            guard let self else {
                return
            }

            Task { @MainActor in
                if let categoryId {
                    self.selectedCategoryId = categoryId
                }
                onComplete(categoryId)
            }
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
