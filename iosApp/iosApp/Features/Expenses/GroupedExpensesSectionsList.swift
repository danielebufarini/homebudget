import SwiftUI

struct GroupedExpensesSectionsList: View {
    let kind: GroupedExpensesKind
    let selectedMonth: MonthCursor
    let onPreviousMonth: (() -> Void)?
    let onNextMonth: (() -> Void)?
    let onOpenExpense: (String) -> Void
    let headerTitle: String?
    let headerAmountDescriptor: String?
    let topReservedInset: CGFloat?
    let bottomScrollClearance: CGFloat
    let headerAccessory: (() -> AnyView)?

    @Binding private var groupingMode: ExpenseGroupingMode
    @State private var viewModel: GroupedExpensesSectionsViewModel
    @State private var pendingExpenseDeleteID: String?

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        selectedMonth: MonthCursor,
        groupingMode: Binding<ExpenseGroupingMode>,
        onPreviousMonth: (() -> Void)?,
        onNextMonth: (() -> Void)?,
        onOpenExpense: @escaping (String) -> Void,
        headerTitle: String? = nil,
        headerAmountDescriptor: String? = nil,
        topReservedInset: CGFloat? = nil,
        bottomScrollClearance: CGFloat = 0,
        headerAccessory: (() -> AnyView)? = nil,
        expandsSectionsInitially: Bool = true
    ) {
        self.kind = kind
        self.selectedMonth = selectedMonth
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenExpense = onOpenExpense
        self.headerTitle = headerTitle
        self.headerAmountDescriptor = headerAmountDescriptor
        self.topReservedInset = topReservedInset
        self.bottomScrollClearance = bottomScrollClearance
        self.headerAccessory = headerAccessory
        _groupingMode = groupingMode
        _viewModel = State(
            initialValue: GroupedExpensesSectionsViewModel(
                year: year,
                month: month,
                kind: kind,
                groupingMode: groupingMode.wrappedValue,
                expandsSectionsInitially: expandsSectionsInitially
            )
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            sectionList

            headerStack
                .zIndex(1)
        }
        .background(AppGlassBackdrop().ignoresSafeArea())
        .toolbarBackground(.hidden, for: .navigationBar)
        .onAppear {
            viewModel.updateGroupingMode(groupingMode)
            viewModel.start()
        }
        .onChange(of: groupingMode) { _, updatedMode in
            viewModel.updateGroupingMode(updatedMode)
        }
        .onDisappear {
            viewModel.stop()
        }
        .overlay {
            if let pendingExpenseDeleteRow {
                AppGlassDialogOverlay {
                    expenseDeleteDialog(for: pendingExpenseDeleteRow)
                }
            }
        }
    }

    private var sectionList: some View {
        List {
            if viewModel.sections.isEmpty {
                emptySection
            } else {
                expenseSections
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(.compact)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) {
            if showsHeader {
                Color.clear.frame(height: resolvedTopReservedInset)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if bottomScrollClearance > 0 {
                Color.clear.frame(height: bottomScrollClearance)
            }
        }
    }

    private var emptySection: some View {
        Section {
            AppGlassListCard {
                Text(viewModel.emptyStateText)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    private var expenseSections: some View {
        ForEach(viewModel.sections) { section in
            Section {
                if viewModel.expandedSectionIDs.contains(section.id) {
                    ForEach(section.rows) { row in
                        rowView(for: row)
                    }
                }
            } header: {
                Button {
                    viewModel.expandedSectionIDs.toggleMembership(of: section.id)
                } label: {
                    GroupedExpenseSectionHeaderView(
                        section: section,
                        isExpanded: viewModel.expandedSectionIDs.contains(section.id)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var headerStack: some View {
        VStack(spacing: 0) {
            monthHeader
            if let headerAccessory {
                Color.clear
                    .frame(height: MonthlyTransactionsHeaderLayout.selectorTopSpacing)
                headerAccessory()
            }
            if kind.showsGroupingControl {
                Color.clear
                    .frame(height: MonthlyTransactionsHeaderLayout.groupingTopSpacing)
                ExpenseGroupingMenuControl(selection: $groupingMode)
                    .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding + 16)
            }
        }
        .monthSwipeNavigationGesture(
            onPreviousMonth: onPreviousMonth,
            onNextMonth: onNextMonth
        )
    }

    @ViewBuilder
    private var monthHeader: some View {
        if showsHeader {
            DashboardStyleMonthNavigationHeader(
                selectedMonth: selectedMonth,
                titleText: headerTitle,
                amountText: monthlyHeaderAmountText(
                    descriptor: headerAmountDescriptor,
                    amountText: viewModel.totalAmountText
                ),
                onPreviousMonth: onPreviousMonth,
                onNextMonth: onNextMonth
            )
            .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding + MonthNavigationHeaderLayout.sideChromeReservedWidth)
            .padding(.top, MonthNavigationHeaderLayout.topPadding)
        }
    }

    private var showsHeader: Bool {
        headerTitle != nil || (onPreviousMonth != nil && onNextMonth != nil)
    }

    private var resolvedTopReservedInset: CGFloat {
        if let topReservedInset {
            return topReservedInset
        }

        if kind.showsGroupingControl {
            return MonthlyTransactionsHeaderLayout.groupingOnlyReservedTopInset
        }

        return MonthNavigationHeaderLayout.reservedTopInset
    }

    @ViewBuilder
    private func expenseDeleteDialog(for row: GroupedExpenseRowModel) -> some View {
        TransactionDeleteConfirmationDialog(
            row: row,
            deleteItem: viewModel.deleteExpense,
            deleteSeries: viewModel.deleteRecurringExpenseSeries,
            clearSelection: { pendingExpenseDeleteID = nil }
        )
    }

    private func rowView(for row: GroupedExpenseRowModel) -> some View {
        Button {
            onOpenExpense(row.id)
        } label: {
            GroupedExpenseRowView(row: row)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                pendingExpenseDeleteID = row.id
            } label: {
                Label(appLocalized("Delete"), systemImage: "trash")
            }
            .tint(.red)
        }
    }

    private var pendingExpenseDeleteRow: GroupedExpenseRowModel? {
        viewModel.sections.row(withID: pendingExpenseDeleteID)
    }
}
