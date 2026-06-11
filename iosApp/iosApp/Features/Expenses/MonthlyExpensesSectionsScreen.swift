import SwiftUI

struct GroupedExpensesSectionsScreen: View {
    let kind: GroupedExpensesKind
    let headerTitle: String?
    let headerAmountDescriptor: String?
    let onOpenExpense: (String) -> Void

    @State private var selectedMonth: MonthCursor
    @State private var groupingMode: ExpenseGroupingMode = .byCategory

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        headerTitle: String? = nil,
        headerAmountDescriptor: String? = nil,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.kind = kind
        self.headerTitle = headerTitle
        self.headerAmountDescriptor = headerAmountDescriptor
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
            onOpenExpense: onOpenExpense,
            headerTitle: headerTitle,
            headerAmountDescriptor: headerAmountDescriptor
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
    @State private var showPaymentScreenshotImport = false

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
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .top) {
            transactionsChromeActions
                .padding(.horizontal, 16)
                .padding(.top, MonthNavigationHeaderLayout.topPadding)
        }
        .sheet(isPresented: $showPaymentScreenshotImport) {
            PaymentScreenshotImportSheet(
                onCandidates: { prefills in
                    showPaymentScreenshotImport = false
                    guard let first = prefills.first else {
                        return
                    }
                    let remaining = Array(prefills.dropFirst())
                    DispatchQueue.main.async {
                        path.append(Route.addPrefilledExpense(first, remaining: remaining))
                    }
                },
                onClose: {
                    showPaymentScreenshotImport = false
                }
            )
            .appGlassSheetPresentation(detents: [.height(420)])
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

    private var transactionsChromeActions: some View {
        HStack {
            Button {
                if !path.isEmpty {
                    path.removeLast()
                }
            } label: {
                AppGlassBackButton()
            }
            .buttonStyle(.glass)
            .accessibilityLabel(appLocalized("Back"))

            Spacer(minLength: 0)

            AppGlassBottomQuickActionsBar(
                addAccessibilityLabel: selectedKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense"),
                voiceAccessibilityLabel: appLocalized("Voice Expense"),
                screenshotAccessibilityLabel: selectedKind == .expense ? appLocalized("Import Payment Screenshot") : nil,
                onAdd: addTransaction,
                onVoice: onStartVoiceExpense,
                onScreenshot: selectedKind == .expense ? { showPaymentScreenshotImport = true } : nil
            )
        }
        .frame(height: MonthNavigationHeaderLayout.minHeight)
        .frame(maxWidth: .infinity)
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
                    groupingMode: $groupingMode
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
