@preconcurrency import ComposeApp
import SwiftUI

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
