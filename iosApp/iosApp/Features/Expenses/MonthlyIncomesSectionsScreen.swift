import SwiftUI

struct MonthlyIncomesSectionsScreen: View {
    let onOpenIncome: (String) -> Void

    @State private var selectedMonth: MonthCursor
    @State private var groupingMode: ExpenseGroupingMode = .byCategory

    init(
        year: Int,
        month: Int,
        onOpenIncome: @escaping (String) -> Void
    ) {
        self.onOpenIncome = onOpenIncome
        _selectedMonth = State(initialValue: MonthCursor(year: year, month: month))
    }

    var body: some View {
        MonthlyIncomesSectionsContent(
            selectedMonth: selectedMonth,
            onPreviousMonth: previousMonth,
            onNextMonth: nextMonth,
            onOpenIncome: onOpenIncome,
            groupingMode: $groupingMode
        )
        .id(selectedMonth.id)
    }

    private func previousMonth() {
        selectedMonth = selectedMonth.previous()
    }

    private func nextMonth() {
        selectedMonth = selectedMonth.next()
    }
}

struct MonthlyIncomesSectionsContent: View {
    let selectedMonth: MonthCursor
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void
    let onOpenIncome: (String) -> Void
    let headerAmountDescriptor: String?
    let topReservedInset: CGFloat?
    let headerAccessory: (() -> AnyView)?
    @Binding private var groupingMode: ExpenseGroupingMode

    @StateObject private var viewModel: MonthlyIncomesSectionsViewModel
    @State private var pendingIncomeDeleteID: String?

    init(
        selectedMonth: MonthCursor,
        onPreviousMonth: @escaping () -> Void,
        onNextMonth: @escaping () -> Void,
        onOpenIncome: @escaping (String) -> Void,
        headerAmountDescriptor: String? = nil,
        topReservedInset: CGFloat? = nil,
        headerAccessory: (() -> AnyView)? = nil,
        groupingMode: Binding<ExpenseGroupingMode>
    ) {
        self.selectedMonth = selectedMonth
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenIncome = onOpenIncome
        self.headerAmountDescriptor = headerAmountDescriptor
        self.topReservedInset = topReservedInset
        self.headerAccessory = headerAccessory
        _groupingMode = groupingMode
        _viewModel = StateObject(
            wrappedValue: MonthlyIncomesSectionsViewModel(
                year: selectedMonth.year,
                month: selectedMonth.month,
                groupingMode: groupingMode.wrappedValue
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
        .monthSwipeNavigationGesture(
            onPreviousMonth: onPreviousMonth,
            onNextMonth: onNextMonth
        )
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
        .safeAreaInset(edge: .bottom, spacing: 0) {
            groupingControlBar
        }
        .overlay {
            if let pendingIncomeDeleteRow {
                AppGlassDialogOverlay {
                    incomeDeleteDialog(for: pendingIncomeDeleteRow)
                }
            }
        }
    }

    private var sectionList: some View {
        List {
            if !viewModel.hasLoadedSnapshot {
                loadingSection
            } else if viewModel.sections.isEmpty {
                emptySection
            } else {
                incomeSections
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(.compact)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) {
            Color.clear.frame(height: topReservedInset ?? MonthNavigationHeaderLayout.reservedTopInset)
        }
    }

    private var loadingSection: some View {
        Section {
            AppGlassListCard {
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
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

    private var incomeSections: some View {
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
        VStack(spacing: MonthlyTransactionsHeaderLayout.selectorTopSpacing) {
            monthHeader
            if let headerAccessory {
                headerAccessory()
            }
        }
    }

    private var monthHeader: some View {
        DashboardStyleMonthNavigationHeader(
            selectedMonth: selectedMonth,
            amountText: monthlyHeaderAmountText(
                descriptor: headerAmountDescriptor,
                amountText: viewModel.totalAmountText
            ),
            onPreviousMonth: onPreviousMonth,
            onNextMonth: onNextMonth
        )
        .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding)
        .padding(.top, MonthNavigationHeaderLayout.topPadding)
    }

    private var groupingControlBar: some View {
        HStack {
            Spacer(minLength: 0)
            ExpenseGroupingGlassControl(selection: $groupingMode)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    @ViewBuilder
    private func incomeDeleteDialog(for row: GroupedExpenseRowModel) -> some View {
        TransactionDeleteConfirmationDialog(
            row: row,
            deleteItem: viewModel.deleteIncome,
            deleteSeries: viewModel.deleteRecurringIncomeSeries,
            clearSelection: { pendingIncomeDeleteID = nil }
        )
    }

    private func rowView(for row: GroupedExpenseRowModel) -> some View {
        Button {
            onOpenIncome(row.id)
        } label: {
            GroupedExpenseRowView(row: row)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                pendingIncomeDeleteID = row.id
            } label: {
                Label(appLocalized("Delete"), systemImage: "trash")
            }
            .tint(.red)
        }
    }

    private var pendingIncomeDeleteRow: GroupedExpenseRowModel? {
        viewModel.sections.row(withID: pendingIncomeDeleteID)
    }
}
