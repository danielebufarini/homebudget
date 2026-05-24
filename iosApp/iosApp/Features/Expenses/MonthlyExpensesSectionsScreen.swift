import SwiftUI

struct GroupedExpensesSectionsScreen: View {
    let kind: GroupedExpensesKind
    let onOpenExpense: (String) -> Void

    @State private var selectedMonth: MonthCursor
    @State private var groupingMode: ExpenseGroupingMode = .byCategory

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.kind = kind
        self.onOpenExpense = onOpenExpense
        _selectedMonth = State(initialValue: MonthCursor(year: year, month: month))
    }

    var body: some View {
        GroupedExpensesSectionsList(
            kind: kind,
            year: selectedMonth.year,
            month: selectedMonth.month,
            selectedMonth: selectedMonth,
            groupingMode: $groupingMode,
            onPreviousMonth: previousMonthAction,
            onNextMonth: nextMonthAction,
            onOpenExpense: onOpenExpense
        )
        .id("\(kind.screenType)-\(selectedMonth.id)")
        .toolbarBackground(.hidden, for: .navigationBar)
    }

    private var previousMonthAction: (() -> Void)? {
        guard kind.supportsMonthNavigation else {
            return nil
        }

        return previousMonth
    }

    private var nextMonthAction: (() -> Void)? {
        guard kind.supportsMonthNavigation else {
            return nil
        }

        return nextMonth
    }

    private func previousMonth() {
        selectedMonth = selectedMonth.previous()
    }

    private func nextMonth() {
        selectedMonth = selectedMonth.next()
    }
}

struct MonthlyTransactionsRootView: View {
    @Binding var path: NavigationPath
    let onStartVoiceExpense: () -> Void

    @State private var selectedMonth: MonthCursor
    @State private var selectedKind: AddTransactionKind
    @State private var groupingMode: ExpenseGroupingMode = .byCategory

    init(
        year: Int,
        month: Int,
        initialKind: AddTransactionKind,
        path: Binding<NavigationPath>,
        onStartVoiceExpense: @escaping () -> Void
    ) {
        _path = path
        self.onStartVoiceExpense = onStartVoiceExpense
        _selectedMonth = State(initialValue: MonthCursor(year: year, month: month))
        _selectedKind = State(initialValue: initialKind)
    }

    var body: some View {
        MonthlyTransactionsSectionsScreen(
            selectedMonth: $selectedMonth,
            selectedKind: $selectedKind,
            groupingMode: $groupingMode,
            onOpenExpense: { expenseID in
                path.append(Route.addExpense(expenseId: expenseID, readOnly: false))
            },
            onOpenIncome: { incomeID in
                path.append(Route.addIncome(incomeId: incomeID, year: nil, month: nil))
            }
        )
        .appGlassHostedScreenChrome()
        .navigationTitle(selectedKind == .income ? appLocalized("Income") : appLocalized("Expenses"))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    if !path.isEmpty {
                        path.removeLast()
                    }
                } label: {
                    AppGlassBackButton()
                }
                .buttonStyle(.glass)
            }

            ToolbarItem(placement: .topBarTrailing) {
                AppGlassBottomQuickActionsBar(
                    addAccessibilityLabel: selectedKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense"),
                    voiceAccessibilityLabel: appLocalized("Voice Expense"),
                    onAdd: addTransaction,
                    onVoice: onStartVoiceExpense
                )
            }
        }
    }

    private func addTransaction() {
        switch selectedKind {
        case .expense:
            path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
        case .income:
            path.append(
                Route.addTransaction(
                    initialKind: .income,
                    year: selectedMonth.year,
                    month: selectedMonth.month
                )
            )
        }
    }
}

private struct MonthlyTransactionsSectionsScreen: View {
    @Binding var selectedMonth: MonthCursor
    @Binding var selectedKind: AddTransactionKind
    @Binding var groupingMode: ExpenseGroupingMode
    let onOpenExpense: (String) -> Void
    let onOpenIncome: (String) -> Void

    var body: some View {
        Group {
            switch selectedKind {
            case .expense:
                GroupedExpensesSectionsList(
                    kind: .monthly,
                    year: selectedMonth.year,
                    month: selectedMonth.month,
                    selectedMonth: selectedMonth,
                    groupingMode: $groupingMode,
                    onPreviousMonth: previousMonth,
                    onNextMonth: nextMonth,
                    onOpenExpense: onOpenExpense,
                    headerAmountDescriptor: appLocalized("Expenses"),
                    topReservedInset: MonthlyTransactionsHeaderLayout.reservedTopInset,
                    headerAccessory: transactionKindSelector,
                    expandsSectionsInitially: false
                )
            case .income:
                MonthlyIncomesSectionsContent(
                    selectedMonth: selectedMonth,
                    onPreviousMonth: previousMonth,
                    onNextMonth: nextMonth,
                    onOpenIncome: onOpenIncome,
                    headerAmountDescriptor: appLocalized("Income"),
                    topReservedInset: MonthlyTransactionsHeaderLayout.reservedTopInset,
                    headerAccessory: transactionKindSelector,
                    groupingMode: groupingMode
                )
            }
        }
        .id("\(selectedKind)-\(selectedMonth.id)")
    }

    private func previousMonth() {
        selectedMonth = selectedMonth.previous()
    }

    private func nextMonth() {
        selectedMonth = selectedMonth.next()
    }

    private func transactionKindSelector() -> AnyView {
        AnyView(
            MonthlyTransactionKindGlassControl(selection: $selectedKind)
                .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding)
        )
    }
}
