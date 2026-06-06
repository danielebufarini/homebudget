@preconcurrency import ComposeApp
import SwiftUI
import Observation


struct TransactionSearchSectionsRootView: View {
    let query: String
    let onClose: () -> Void
    let onOpenExpense: (String) -> Void
    let onOpenIncome: (String) -> Void

    @State private var selectedKind: AddTransactionKind = .expense
    @State private var groupingMode: ExpenseGroupingMode = .byCategory
    @State private var viewModel: TransactionSearchSectionsViewModel
    @State private var pendingExpenseDeleteID: String?
    @State private var pendingIncomeDeleteID: String?

    init(
        query: String,
        onClose: @escaping () -> Void,
        onOpenExpense: @escaping (String) -> Void,
        onOpenIncome: @escaping (String) -> Void
    ) {
        self.query = query
        self.onClose = onClose
        self.onOpenExpense = onOpenExpense
        self.onOpenIncome = onOpenIncome
        _viewModel = State(
            initialValue: TransactionSearchSectionsViewModel(
                query: query,
                groupingMode: .byCategory
            )
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            searchList

            headerStack
                .zIndex(1)
        }
        .background(AppGlassBackdrop().ignoresSafeArea())
        .appGlassHostedScreenChrome()
        .navigationTitle(appLocalized("Search Results"))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onClose) {
                    AppGlassBackButton()
                }
                .buttonStyle(.glass)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
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
        .onAppear {
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

            if let pendingIncomeDeleteRow {
                AppGlassDialogOverlay {
                    incomeDeleteDialog(for: pendingIncomeDeleteRow)
                }
            }
        }
    }

    private var searchList: some View {
        List {
            if !viewModel.hasLoadedSnapshot {
                loadingSection
            } else if currentSections.isEmpty {
                emptySection
            } else {
                resultSections
                if currentCanLoadMoreResults {
                    loadMoreSection
                }
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(.compact)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) {
            Color.clear.frame(height: TransactionSearchHeaderLayout.reservedTopInset)
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
                Text(currentEmptyStateText)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    private var loadMoreSection: some View {
        Section {
            AppGlassListCard {
                Button(appLocalized("Load more results")) {
                    viewModel.loadMoreResults()
                }
                .frame(maxWidth: .infinity)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    private var resultSections: some View {
        ForEach(currentSections) { section in
            Section {
                if currentExpandedSectionIDs.contains(section.id) {
                    ForEach(section.rows) { row in
                        rowView(for: row)
                    }
                }
            } header: {
                Button {
                    toggleExpandedSection(section.id)
                } label: {
                    GroupedExpenseSectionHeaderView(
                        section: section,
                        isExpanded: currentExpandedSectionIDs.contains(section.id)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var headerStack: some View {
        VStack(spacing: MonthlyTransactionsHeaderLayout.selectorTopSpacing) {
            TransactionSearchGlassHeader(
                query: query,
                amountText: monthlyHeaderAmountText(
                    descriptor: selectedKind == .income ? appLocalized("Income") : appLocalized("Expenses"),
                    amountText: selectedKind == .income ? viewModel.incomeTotalAmountText : viewModel.expenseTotalAmountText
                )
            )
            .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding)
            .padding(.top, MonthNavigationHeaderLayout.topPadding)

            MonthlyTransactionKindGlassControl(selection: $selectedKind)
                .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding)
        }
    }

    private var currentSections: [GroupedExpenseSectionModel] {
        selectedKind == .income ? viewModel.incomeSections : viewModel.expenseSections
    }

    private var currentEmptyStateText: String {
        selectedKind == .income ? viewModel.incomeEmptyStateText : viewModel.expenseEmptyStateText
    }

    private var currentCanLoadMoreResults: Bool {
        selectedKind == .income ? viewModel.canLoadMoreIncomeResults : viewModel.canLoadMoreExpenseResults
    }

    private var currentExpandedSectionIDs: Set<String> {
        selectedKind == .income ? viewModel.expandedIncomeSectionIDs : viewModel.expandedExpenseSectionIDs
    }

    private func toggleExpandedSection(_ sectionID: String) {
        switch selectedKind {
        case .expense:
            viewModel.expandedExpenseSectionIDs.toggleMembership(of: sectionID)
        case .income:
            viewModel.expandedIncomeSectionIDs.toggleMembership(of: sectionID)
        }
    }

    @ViewBuilder
    private func rowView(for row: GroupedExpenseRowModel) -> some View {
        Button {
            if selectedKind == .income {
                onOpenIncome(row.id)
            } else {
                onOpenExpense(row.id)
            }
        } label: {
            GroupedExpenseRowView(row: row)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                if selectedKind == .income {
                    pendingIncomeDeleteID = row.id
                } else {
                    pendingExpenseDeleteID = row.id
                }
            } label: {
                Label(appLocalized("Delete"), systemImage: "trash")
            }
            .tint(.red)
        }
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

    @ViewBuilder
    private func incomeDeleteDialog(for row: GroupedExpenseRowModel) -> some View {
        TransactionDeleteConfirmationDialog(
            row: row,
            deleteItem: viewModel.deleteIncome,
            deleteSeries: viewModel.deleteRecurringIncomeSeries,
            clearSelection: { pendingIncomeDeleteID = nil }
        )
    }

    private var pendingExpenseDeleteRow: GroupedExpenseRowModel? {
        viewModel.expenseSections.row(withID: pendingExpenseDeleteID)
    }

    private var pendingIncomeDeleteRow: GroupedExpenseRowModel? {
        viewModel.incomeSections.row(withID: pendingIncomeDeleteID)
    }
}
