@preconcurrency import ComposeApp
import SwiftUI
import UIKit

private enum Route: Hashable {
    case categories
    case addExpense(expenseId: String?, readOnly: Bool)
    case addIncome(incomeId: String?, year: Int?, month: Int?)
    case monthlyIncomes(year: Int, month: Int)
    case monthlyExpenses(year: Int, month: Int)
    case sharedExpenses(year: Int, month: Int)
    case categoryExpenses(year: Int, month: Int, categoryName: String)
}

private struct KotlinViewControllerHost: UIViewControllerRepresentable {
    let makeViewController: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        SafeAreaContainerViewController(contentViewController: makeViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class SafeAreaContainerViewController: UIViewController {
    private let contentViewController: UIViewController

    init(contentViewController: UIViewController) {
        self.contentViewController = contentViewController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .clear

        addChild(contentViewController)
        contentViewController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentViewController.view)

        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            contentViewController.view.topAnchor.constraint(equalTo: guide.topAnchor),
            contentViewController.view.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
            contentViewController.view.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            contentViewController.view.bottomAnchor.constraint(equalTo: guide.bottomAnchor)
        ])

        contentViewController.didMove(toParent: self)
    }
}

struct ContentView: View {
    @State private var path = NavigationPath()
    @State private var showVoiceExpenseSheet = false

    var body: some View {
        NavigationStack(path: $path) {
            DashboardRootView(path: $path)
                .navigationTitle("Dashboard")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Categories") {
                            path.append(Route.categories)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showVoiceExpenseSheet = true
                        } label: {
                            Image(systemName: "waveform.badge.mic")
                        }
                    }
                }
                .sheet(isPresented: $showVoiceExpenseSheet) {
                    VoiceExpenseEntrySheet {
                        showVoiceExpenseSheet = false
                    }
                }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .categories:
                        CategoriesRootView()
                            .navigationTitle("Categories")
                            .navigationBarTitleDisplayMode(.inline)
                    case let .addExpense(expenseId, readOnly):
                        ExpenseEditorRootView(
                            expenseId: expenseId,
                            readOnly: readOnly
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .navigationTitle(addExpenseTitle(expenseId: expenseId, readOnly: readOnly))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .addIncome(incomeId, year, month):
                        IncomeEditorRootView(
                            incomeId: incomeId,
                            initialYear: year,
                            initialMonth: month
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .navigationTitle(incomeId == nil ? appLocalized("Add Income") : appLocalized("Edit Income"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .monthlyIncomes(year, month):
                        MonthlyIncomesRootView(
                            year: Int(year),
                            month: Int(month),
                            path: $path
                        )
                        .navigationTitle(appMonthlyTitle(month: month, key: "Income"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .monthlyExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .monthly,
                            year: Int(year),
                            month: Int(month),
                            onAddExpense: {
                                path.append(Route.addExpense(expenseId: nil, readOnly: false))
                            }
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle(appMonthlyTitle(month: month, key: "Expenses"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .sharedExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .shared,
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle(appMonthlyTitle(month: month, key: "Shared Expenses"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .categoryExpenses(year, month, categoryName):
                        GroupedExpensesSectionsScreen(
                            kind: .category(name: categoryName),
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(monthName(month)) \(categoryName)")
                        .navigationBarTitleDisplayMode(.inline)
                    }
                }
        }
    }
}

@MainActor
private final class ExpenseEditorDeletionViewModel: ObservableObject {
    @Published var pendingSeriesId: String?

    private let controller = IosEditItemDeletionController()

    func disposeController() {
        controller.dispose()
    }

    func requestDelete(
        expenseId: String,
        onClose: @escaping () -> Void
    ) {
        controller.loadExpenseMetadata(id: expenseId) { [weak self] metadata in
            guard let self, let metadata else {
                return
            }

            Task { @MainActor in
                if let seriesId = metadata.recurringSeriesId, !seriesId.isEmpty {
                    self.pendingSeriesId = seriesId
                } else {
                    self.deleteExpense(expenseId: metadata.id, onClose: onClose)
                }
            }
        }
    }

    func deleteExpense(
        expenseId: String,
        onClose: @escaping () -> Void
    ) {
        controller.deleteExpense(id: expenseId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                onClose()
            }
        }
    }

    func deleteWholeSeries(onClose: @escaping () -> Void) {
        guard let pendingSeriesId else {
            return
        }

        controller.deleteRecurringExpenseSeries(seriesId: pendingSeriesId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                self.pendingSeriesId = nil
                onClose()
            }
        }
    }
}

@MainActor
private final class IncomeEditorDeletionViewModel: ObservableObject {
    @Published var pendingSeriesId: String?

    private let controller = IosEditItemDeletionController()

    func disposeController() {
        controller.dispose()
    }

    func requestDelete(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        controller.loadIncomeMetadata(id: incomeId) { [weak self] metadata in
            guard let self, let metadata else {
                return
            }

            Task { @MainActor in
                if let seriesId = metadata.recurringSeriesId, !seriesId.isEmpty {
                    self.pendingSeriesId = seriesId
                } else {
                    self.deleteIncome(incomeId: metadata.id, onClose: onClose)
                }
            }
        }
    }

    func deleteIncome(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        controller.deleteIncome(id: incomeId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                onClose()
            }
        }
    }

    func deleteWholeSeries(onClose: @escaping () -> Void) {
        guard let pendingSeriesId else {
            return
        }

        controller.deleteRecurringIncomeSeries(seriesId: pendingSeriesId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                self.pendingSeriesId = nil
                onClose()
            }
        }
    }
}

private struct ExpenseEditorRootView: View {
    let expenseId: String?
    let readOnly: Bool
    let onClose: () -> Void

    @StateObject private var deletionViewModel = ExpenseEditorDeletionViewModel()

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.AddExpenseViewController(
                expenseId: expenseId,
                readOnly: readOnly,
                onClose: onClose
            )
        }
        .onDisappear {
            deletionViewModel.disposeController()
        }
        .toolbar {
            if let expenseId, !readOnly {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        deletionViewModel.requestDelete(
                            expenseId: expenseId,
                            onClose: onClose
                        )
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete",
            isPresented: recurringDialogBinding,
            titleVisibility: .visible
        ) {
            if let expenseId {
                Button("This instance only", role: .destructive) {
                    deletionViewModel.pendingSeriesId = nil
                    deletionViewModel.deleteExpense(
                        expenseId: expenseId,
                        onClose: onClose
                    )
                }
            }
            Button("Whole series", role: .destructive) {
                deletionViewModel.deleteWholeSeries(onClose: onClose)
            }
        }
    }

    private var recurringDialogBinding: Binding<Bool> {
        Binding(
            get: {
                deletionViewModel.pendingSeriesId != nil
            },
            set: { isPresented in
                if !isPresented {
                    deletionViewModel.pendingSeriesId = nil
                }
            }
        )
    }
}

private struct IncomeEditorRootView: View {
    let incomeId: String?
    let initialYear: Int?
    let initialMonth: Int?
    let onClose: () -> Void

    @StateObject private var deletionViewModel = IncomeEditorDeletionViewModel()

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.AddIncomeViewController(
                incomeId: incomeId,
                initialYear: initialYear.map(kotlinInt),
                initialMonth: initialMonth.map(kotlinInt),
                onClose: onClose
            )
        }
        .onDisappear {
            deletionViewModel.disposeController()
        }
        .toolbar {
            if let incomeId {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        deletionViewModel.requestDelete(
                            incomeId: incomeId,
                            onClose: onClose
                        )
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete",
            isPresented: recurringDialogBinding,
            titleVisibility: .visible
        ) {
            if let incomeId {
                Button("This instance only", role: .destructive) {
                    deletionViewModel.pendingSeriesId = nil
                    deletionViewModel.deleteIncome(
                        incomeId: incomeId,
                        onClose: onClose
                    )
                }
            }
            Button("Whole series", role: .destructive) {
                deletionViewModel.deleteWholeSeries(onClose: onClose)
            }
        }
    }

    private var recurringDialogBinding: Binding<Bool> {
        Binding(
            get: {
                deletionViewModel.pendingSeriesId != nil
            },
            set: { isPresented in
                if !isPresented {
                    deletionViewModel.pendingSeriesId = nil
                }
            }
        )
    }
}

private struct DashboardRootView: View {
    @Binding var path: NavigationPath

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.DashboardContentViewController(
                onOpenCategories: {
                    path.append(Route.categories)
                },
                onOpenAddExpense: {
                    path.append(Route.addExpense(expenseId: nil, readOnly: false))
                },
                onOpenMonthlyIncomes: { year, month in
                    path.append(Route.monthlyIncomes(year: year.intValue, month: month.intValue))
                },
                onOpenMonthlyExpenses: { year, month in
                    path.append(Route.monthlyExpenses(year: year.intValue, month: month.intValue))
                },
                onOpenSharedExpenses: { year, month in
                    path.append(Route.sharedExpenses(year: year.intValue, month: month.intValue))
                },
                onOpenExpenseDetails: { expenseId, readOnly in
                    path.append(Route.addExpense(expenseId: expenseId, readOnly: readOnly.boolValue))
                },
                onOpenCategoryExpenses: { year, month, categoryName in
                    path.append(
                        Route.categoryExpenses(
                            year: year.intValue,
                            month: month.intValue,
                            categoryName: categoryName
                        )
                    )
                }
            )
        }
    }
}

private struct MonthlyIncomesRootView: View {
    let year: Int
    let month: Int
    @Binding var path: NavigationPath

    var body: some View {
        MonthlyIncomesSectionsScreen(
            year: year,
            month: month
        ) { incomeId in
            path.append(Route.addIncome(incomeId: incomeId, year: nil, month: nil))
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    path.append(
                        Route.addIncome(
                            incomeId: nil,
                            year: year,
                            month: month
                        )
                    )
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
    }
}

private struct CategoriesRootView: View {
    @State private var addCategoryRequestKey = 0

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.CategoriesContentViewController(addCategoryRequestKey: Int32(addCategoryRequestKey))
        }
        .id(addCategoryRequestKey)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    addCategoryRequestKey += 1
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
    }
}

private func addExpenseTitle(expenseId: String?, readOnly: Bool) -> String {
    if readOnly {
        return appLocalized("Expense Details")
    }

    return expenseId == nil ? appLocalized("Add Expense") : appLocalized("Edit Expense")
}

private func kotlinInt(_ value: Int) -> KotlinInt {
    KotlinInt(int: Int32(value))
}
