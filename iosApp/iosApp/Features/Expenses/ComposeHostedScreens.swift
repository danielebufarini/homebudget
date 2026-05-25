@preconcurrency import ComposeApp
import SwiftUI

@MainActor
final class ExpenseEditorDeletionViewModel: ObservableObject {
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
final class IncomeEditorDeletionViewModel: ObservableObject {
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

private enum ComposeHostedPalette {
    static var background: Color {
        Color(uiColor: UIColor { traits in
            switch traits.userInterfaceStyle {
            case .dark:
                UIColor(red: 16.0 / 255.0, green: 24.0 / 255.0, blue: 32.0 / 255.0, alpha: 1.0)
            default:
                UIColor(red: 247.0 / 255.0, green: 250.0 / 255.0, blue: 255.0 / 255.0, alpha: 1.0)
            }
        })
    }
}

struct TransactionEditorRootView: View {
    let initialYear: Int?
    let initialMonth: Int?
    let onClose: () -> Void

    @State private var selectedKind: AddTransactionKind

    init(
        initialKind: AddTransactionKind,
        initialYear: Int?,
        initialMonth: Int?,
        onClose: @escaping () -> Void
    ) {
        self.initialYear = initialYear
        self.initialMonth = initialMonth
        self.onClose = onClose
        _selectedKind = State(initialValue: initialKind)
    }

    var body: some View {
        ZStack {
            editorHost
                .id(editorHostID)

            VStack(spacing: 0) {
                topChrome
                Spacer(minLength: 0)
                bottomChrome
            }
        }
        .background(ComposeHostedPalette.background.ignoresSafeArea())
        .appGlassHostedScreenChrome()
        .iosNativeDatePickerHost()
        .onDisappear {
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
        }
    }

    private var editorHost: some View {
        KotlinViewControllerHost(constrainToSafeArea: false) {
            switch selectedKind {
            case .expense:
                MainViewControllerKt.AddExpenseViewController(
                    expenseId: nil,
                    readOnly: false,
                    useHostedFloatingChrome: true,
                    onClose: onClose
                )
            case .income:
                MainViewControllerKt.AddIncomeViewController(
                    incomeId: nil,
                    initialYear: initialYear.map(kotlinInt),
                    initialMonth: initialMonth.map(kotlinInt),
                    useHostedFloatingChrome: true,
                    onClose: onClose
                )
            }
        }
    }

    private var editorHostID: String {
        "\(selectedKind)-\(initialYear ?? -1)-\(initialMonth ?? -1)"
    }

    private var topChrome: some View {
        VStack(spacing: TransactionEditorChromeLayout.selectorTopSpacing) {
            ExpenseEditorGlassHeader(
                title: title,
                showsDeleteAction: false,
                onBack: onClose,
                onDelete: {}
            )

            MonthlyTransactionKindGlassControl(selection: $selectedKind)
                .padding(.horizontal, TransactionEditorChromeLayout.selectorHorizontalPadding)
        }
        .padding(.horizontal, TransactionEditorChromeLayout.horizontalPadding)
        .padding(.top, TransactionEditorChromeLayout.topPadding)
    }

    private var bottomChrome: some View {
        ExpenseEditorGlassFooter(
            onCancel: onClose,
            onConfirm: {
                IosExpenseEditorBridgeKt.performIosExpenseEditorSave()
            },
            confirmLabel: confirmLabel,
            showsSecondaryAction: true
        )
    }

    private var title: String {
        selectedKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense")
    }

    private var confirmLabel: String {
        selectedKind == .income ? appLocalized("Save") : appLocalized("Save Expense")
    }
}

struct ExpenseEditorRootView: View {
    let expenseId: String?
    let readOnly: Bool
    let onClose: () -> Void

    @StateObject private var deletionViewModel = ExpenseEditorDeletionViewModel()

    private var usesHostedGlassChrome: Bool {
        expenseId != nil || readOnly
    }

    private var title: String {
        if readOnly {
            return appLocalized("Expense Details")
        }
        return expenseId == nil ? appLocalized("Add Expense") : appLocalized("Edit Expense")
    }

    var body: some View {
        KotlinViewControllerHost(constrainToSafeArea: !usesHostedGlassChrome) {
            MainViewControllerKt.AddExpenseViewController(
                expenseId: expenseId,
                readOnly: readOnly,
                useHostedFloatingChrome: false,
                onClose: onClose
            )
        }
        .appGlassHostedScreenChrome()
        .iosNativeDatePickerHost()
        .safeAreaInset(edge: .top, spacing: 0) {
            if usesHostedGlassChrome {
                Color.clear.frame(height: ExpenseEditorChromeLayout.reservedTopInset)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if usesHostedGlassChrome {
                Color.clear.frame(height: ExpenseEditorChromeLayout.reservedBottomInset)
            }
        }
        .overlay(alignment: .top) {
            if usesHostedGlassChrome {
                ExpenseEditorGlassHeader(
                    title: title,
                    showsDeleteAction: !readOnly && expenseId != nil,
                    onBack: onClose,
                    onDelete: {
                        guard let expenseId else {
                            return
                        }
                        deletionViewModel.requestDelete(
                            expenseId: expenseId,
                            onClose: onClose
                        )
                    }
                )
                .padding(.horizontal, ExpenseEditorChromeLayout.horizontalPadding)
                .padding(.top, ExpenseEditorChromeLayout.topPadding)
            }
        }
        .overlay(alignment: .bottom) {
            if usesHostedGlassChrome {
                if readOnly {
                    ExpenseEditorGlassFooter(
                        onCancel: onClose,
                        onConfirm: onClose,
                        confirmLabel: appLocalized("Close"),
                        showsSecondaryAction: false
                    )
                } else {
                    ExpenseEditorGlassFooter(
                        onCancel: onClose,
                        onConfirm: {
                            IosExpenseEditorBridgeKt.performIosExpenseEditorSave()
                        },
                        confirmLabel: expenseId == nil ? appLocalized("Save Expense") : appLocalized("Update Expense"),
                        showsSecondaryAction: true
                    )
                }
            }
        }
        .onDisappear {
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
            deletionViewModel.disposeController()
        }
        .overlay {
            if deletionViewModel.pendingSeriesId != nil, let expenseId {
                AppGlassDialogOverlay {
                    AppGlassRecurringDeleteConfirmationDialog(
                        message: appLocalized("Choose whether to delete only this instance or the whole series."),
                        onDeleteInstance: {
                            deletionViewModel.pendingSeriesId = nil
                            deletionViewModel.deleteExpense(
                                expenseId: expenseId,
                                onClose: onClose
                            )
                        },
                        onDeleteSeries: {
                            deletionViewModel.deleteWholeSeries(onClose: onClose)
                        },
                        onCancel: {
                            deletionViewModel.pendingSeriesId = nil
                        }
                    )
                }
            }
        }
    }
}

enum ExpenseEditorChromeLayout {
    static let horizontalPadding: CGFloat = 16
    static let topPadding: CGFloat = 12
    static let bottomPadding: CGFloat = 12
    static let headerHeight: CGFloat = 56
    static let footerHeight: CGFloat = 86
    static let interSectionSpacing: CGFloat = 16
    static var reservedTopInset: CGFloat { topPadding + headerHeight + interSectionSpacing }
    static var reservedBottomInset: CGFloat { bottomPadding + footerHeight + interSectionSpacing }
}

private enum TransactionEditorChromeLayout {
    static let horizontalPadding = ExpenseEditorChromeLayout.horizontalPadding
    static let selectorHorizontalPadding: CGFloat = 6
    static let topPadding = ExpenseEditorChromeLayout.topPadding
    static let selectorTopSpacing = MonthlyTransactionsHeaderLayout.selectorTopSpacing
}

struct ExpenseEditorGlassHeader: View {
    let title: String
    let showsDeleteAction: Bool
    let onBack: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onBack) {
                AppGlassToolbarIcon(systemName: "chevron.left")
            }
            .buttonStyle(.glass)

            AppGlassToolbarTitle(text: title)
                .frame(maxWidth: .infinity)

            if showsDeleteAction {
                Button(action: onDelete) {
                    AppGlassToolbarIcon(systemName: "trash")
                }
                .buttonStyle(.glass)
            } else {
                Color.clear
                    .frame(width: 36, height: 36)
                    .padding(9)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct ExpenseEditorGlassFooter: View {
    let onCancel: () -> Void
    let onConfirm: () -> Void
    let confirmLabel: String
    let showsSecondaryAction: Bool

    var body: some View {
        AppGlassSheetActionBar {
            if showsSecondaryAction {
                Button(appLocalized("Cancel"), action: onCancel)
                    .buttonStyle(.glass)
            }

            Button(confirmLabel, action: onConfirm)
                .buttonStyle(.glassProminent)
        }
        .padding(.bottom, ExpenseEditorChromeLayout.bottomPadding)
    }
}

struct IncomeEditorRootView: View {
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
                useHostedFloatingChrome: false,
                onClose: onClose
            )
        }
        .appGlassHostedScreenChrome()
        .iosNativeDatePickerHost()
        .onDisappear {
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
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
                        AppGlassToolbarIcon(systemName: "trash")
                    }
                    .buttonStyle(.glass)
                }
            }
        }
        .overlay {
            if deletionViewModel.pendingSeriesId != nil, let incomeId {
                AppGlassDialogOverlay {
                    AppGlassRecurringDeleteConfirmationDialog(
                        message: appLocalized("Choose whether to delete only this instance or the whole series."),
                        onDeleteInstance: {
                            deletionViewModel.pendingSeriesId = nil
                            deletionViewModel.deleteIncome(
                                incomeId: incomeId,
                                onClose: onClose
                            )
                        },
                        onDeleteSeries: {
                            deletionViewModel.deleteWholeSeries(onClose: onClose)
                        },
                        onCancel: {
                            deletionViewModel.pendingSeriesId = nil
                        }
                    )
                }
            }
        }
    }
}

struct DashboardRootView: View {
    @Binding var path: NavigationPath
    let onStartVoiceExpense: () -> Void
    let onOpenCsvTransfer: () -> Void

    var body: some View {
        KotlinViewControllerHost(constrainToSafeArea: false) {
            MainViewControllerKt.DashboardContentViewController(
                onOpenCategories: {
                    path.append(Route.categories)
                },
                onOpenAddExpense: {
                    path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
                },
                onOpenVoiceExpense: onStartVoiceExpense,
                onOpenCsvTransfer: onOpenCsvTransfer,
                onOpenDayExpenses: { year, month, day in
                    path.append(
                        Route.dayExpenses(
                            year: year.intValue,
                            month: month.intValue,
                            day: day.intValue
                        )
                    )
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
                },
                onOpenTransactionSearch: { year, month, query in
                    path.append(
                        Route.transactionSearch(
                            year: year.intValue,
                            month: month.intValue,
                            query: query
                        )
                    )
                }
            )
        }
        .appGlassHostedScreenChrome()
        .ignoresSafeArea()
    }
}

struct CategoriesRootView: View {
    let onClose: () -> Void

    var body: some View {
        ZStack {
            AppGlassBackdrop()

            KotlinViewControllerHost(constrainToSafeArea: false) {
                MainViewControllerKt.CategoriesViewController(onClose: onClose)
            }
            .ignoresSafeArea()
        }
        .overlay(alignment: .top) {
            CategoriesGlassHeader(
                title: appLocalized("Categories"),
                onBack: onClose,
                onAdd: {
                    IosCategoriesManagementBridgeKt.performIosCategoriesManagementAdd()
                }
            )
            .padding(.horizontal, CategoriesChromeLayout.horizontalPadding)
            .padding(.top, CategoriesChromeLayout.topPadding)
        }
        .ignoresSafeArea(edges: .bottom)
        .toolbarBackground(.hidden, for: .navigationBar)
        .scrollEdgeEffectStyle(.soft, for: .top)
    }
}

private enum CategoriesChromeLayout {
    static let horizontalPadding: CGFloat = 16
    static let topPadding: CGFloat = 12
}

private struct CategoriesGlassHeader: View {
    let title: String
    let onBack: () -> Void
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onBack) {
                AppGlassToolbarIcon(systemName: "chevron.left")
            }
            .buttonStyle(.glass)

            AppGlassToolbarTitle(text: title)
                .frame(maxWidth: .infinity)

            Button(action: onAdd) {
                AppGlassToolbarIcon(systemName: "plus")
            }
            .buttonStyle(.glass)
        }
        .frame(maxWidth: .infinity)
    }
}

struct MonthlyIncomesRootView: View {
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
    }
}

struct TransactionSearchRootView: View {
    let year: Int
    let month: Int
    let query: String
    @Binding var path: NavigationPath

    var body: some View {
        TransactionSearchSectionsRootView(
            query: query,
            onClose: {
                if !path.isEmpty {
                    path.removeLast()
                }
            },
            onOpenExpense: { expenseId in
                path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
            },
            onOpenIncome: { incomeId in
                path.append(Route.addIncome(incomeId: incomeId, year: nil, month: nil))
            }
        )
    }
}
