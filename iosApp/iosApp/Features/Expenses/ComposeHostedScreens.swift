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

struct TransactionEditorRootView: View {
    let initialKind: AddTransactionKind
    let initialYear: Int?
    let initialMonth: Int?
    let onClose: () -> Void

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.AddTransactionViewController(
                initialIncomeSelected: initialKind == .income,
                initialIncomeYear: initialYear.map(kotlinInt),
                initialIncomeMonth: initialMonth.map(kotlinInt),
                onClose: onClose
            )
        }
        .appGlassHostedScreenChrome()
        .iosNativeDatePickerHost()
        .onDisappear {
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
        }
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

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.DashboardContentViewController(
                onOpenCategories: {
                    path.append(Route.categories)
                },
                onOpenAddExpense: {
                    path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
                },
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
                }
            )
        }
        .appGlassHostedScreenChrome()
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
        }
        .ignoresSafeArea()
        .toolbarBackground(.hidden, for: .navigationBar)
        .scrollEdgeEffectStyle(.soft, for: .top)
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
