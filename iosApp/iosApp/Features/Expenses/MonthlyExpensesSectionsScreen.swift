@preconcurrency import ComposeApp
import SwiftUI

enum GroupedExpensesKind: Hashable {
    case monthly
    case shared
    case category(name: String)
    case day(day: Int)

    var screenType: String {
        switch self {
        case .monthly:
            return "monthly"
        case .shared:
            return "shared"
        case .category:
            return "category"
        case .day:
            return "day"
        }
    }

    var categoryName: String? {
        switch self {
        case let .category(name):
            return name
        case .monthly, .shared, .day:
            return nil
        }
    }

    var dayOfMonth: KotlinInt? {
        switch self {
        case let .day(day):
            return KotlinInt(int: Int32(day))
        case .monthly, .shared, .category:
            return nil
        }
    }

    var allowsDelete: Bool {
        true
    }
}

enum ExpenseGroupingMode: String, Hashable {
    case byCategory
    case byDate

    var bridgeValue: String {
        switch self {
        case .byCategory:
            return "category"
        case .byDate:
            return "date"
        }
    }
}

private struct MonthCursor: Hashable {
    let year: Int
    let month: Int

    func previous() -> MonthCursor {
        month == 1 ? MonthCursor(year: year - 1, month: 12) : MonthCursor(year: year, month: month - 1)
    }

    func next() -> MonthCursor {
        month == 12 ? MonthCursor(year: year + 1, month: 1) : MonthCursor(year: year, month: month + 1)
    }

    var label: String {
        "\(monthName(month)) \(year)"
    }

    var id: String {
        "\(year)-\(month)"
    }
}

private enum MonthSwipeNavigationGesture {
    static let minimumDistance: CGFloat = 32
    static let triggerDistance: CGFloat = 120
    static let axisDominance: CGFloat = 1.35
}

private struct MonthSwipeNavigationModifier: ViewModifier {
    let onPreviousMonth: (() -> Void)?
    let onNextMonth: (() -> Void)?

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(
                minimumDistance: MonthSwipeNavigationGesture.minimumDistance,
                coordinateSpace: .local
            )
            .onEnded { value in
                let horizontalDistance = abs(value.translation.width)
                let verticalDistance = abs(value.translation.height)

                guard
                    horizontalDistance >= MonthSwipeNavigationGesture.triggerDistance,
                    horizontalDistance > verticalDistance * MonthSwipeNavigationGesture.axisDominance
                else {
                    return
                }

                if value.translation.width < 0 {
                    onNextMonth?()
                } else {
                    onPreviousMonth?()
                }
            }
        )
    }
}

private extension View {
    func monthSwipeNavigationGesture(
        onPreviousMonth: (() -> Void)?,
        onNextMonth: (() -> Void)?
    ) -> some View {
        modifier(
            MonthSwipeNavigationModifier(
                onPreviousMonth: onPreviousMonth,
                onNextMonth: onNextMonth
            )
        )
    }
}

@MainActor
private final class GroupedExpensesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = appAmountLabel("0.00")
    @Published var emptyStateText = appLocalized("No expenses for this month")
    @Published var sections: [GroupedExpenseSectionModel] = []
    @Published var expandedSectionIDs = Set<String>()

    private let observer: IosGroupedExpensesObserver
    private let expandsSectionsInitially: Bool
    private var hasLoadedInitialExpansionState = false
    private var knownSectionIDs = Set<String>()
    private var isObserving = false

    init(
        year: Int,
        month: Int,
        kind: GroupedExpensesKind,
        groupingMode: ExpenseGroupingMode,
        expandsSectionsInitially: Bool = true
    ) {
        self.expandsSectionsInitially = expandsSectionsInitially
        observer = IosGroupedExpensesObserver(
            year: Int32(year),
            month: Int32(month),
            screenType: kind.screenType,
            categoryName: kind.categoryName,
            dayOfMonth: kind.dayOfMonth,
            initialGroupingMode: groupingMode.bridgeValue
        )
    }

    deinit {
        observer.dispose()
    }

    func start() {
        guard !isObserving else {
            return
        }

        isObserving = true
        observer.start { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        guard isObserving else {
            return
        }

        observer.stop()
        isObserving = false
    }

    func deleteExpense(_ expenseID: String) {
        observer.deleteExpense(id: expenseID)
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        observer.deleteRecurringExpenseSeries(seriesId: seriesID)
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        observer.setGroupingMode(groupingMode: groupingMode.bridgeValue)
    }

    private func apply(snapshot: IosGroupedExpensesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        emptyStateText = snapshot.emptyStateText
        sections = snapshot.sections.map(GroupedExpenseSectionModel.init)

        let incomingIDs = Set(sections.lazy.map(\.id))
        if hasLoadedInitialExpansionState {
            let newSectionIDs = incomingIDs.subtracting(knownSectionIDs)
            expandedSectionIDs.formIntersection(incomingIDs)
            if expandsSectionsInitially {
                expandedSectionIDs.formUnion(newSectionIDs)
            }
        } else {
            expandedSectionIDs = expandsSectionsInitially ? incomingIDs : []
            hasLoadedInitialExpansionState = true
        }
        knownSectionIDs = incomingIDs
    }
}

@MainActor
private final class MonthlyIncomesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = appAmountLabel("0.00")
    @Published var emptyStateText = appLocalized("No income for this month")
    @Published var sections: [GroupedExpenseSectionModel] = []
    @Published var hasLoadedSnapshot = false
    @Published var expandedSectionIDs = Set<String>()

    private let observer: IosMonthlyIncomesObserver
    private var hasLoadedInitialExpansionState = false
    private var knownSectionIDs = Set<String>()
    private var isObserving = false

    init(
        year: Int,
        month: Int,
        groupingMode: ExpenseGroupingMode = .byDate
    ) {
        observer = IosMonthlyIncomesObserver(
            year: Int32(year),
            month: Int32(month),
            initialGroupingMode: groupingMode.bridgeValue
        )
    }

    deinit {
        observer.dispose()
    }

    func start() {
        guard !isObserving else {
            return
        }

        isObserving = true
        observer.start { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        guard isObserving else {
            return
        }

        observer.stop()
        isObserving = false
    }

    func deleteIncome(_ incomeID: String) {
        observer.deleteIncome(id: incomeID)
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        observer.deleteRecurringIncomeSeries(seriesId: seriesID)
    }

    private func apply(snapshot: IosMonthlyIncomesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        emptyStateText = snapshot.emptyStateText
        sections = snapshot.sections.map {
            GroupedExpenseSectionModel($0, showsCategoryMetadata: false)
        }

        hasLoadedSnapshot = true

        let incomingIDs = Set(sections.lazy.map(\.id))
        if hasLoadedInitialExpansionState {
            let newSectionIDs = incomingIDs.subtracting(knownSectionIDs)
            expandedSectionIDs.formIntersection(incomingIDs)
            expandedSectionIDs.formUnion(newSectionIDs)
        } else {
            expandedSectionIDs = incomingIDs
            hasLoadedInitialExpansionState = true
        }
        knownSectionIDs = incomingIDs
    }
}

struct GroupedExpensesSectionsScreen: View {
    let kind: GroupedExpensesKind
    let onAddExpense: (() -> Void)?
    let onOpenExpense: (String) -> Void
    @State private var selectedMonth: MonthCursor
    @State private var groupingMode: ExpenseGroupingMode = .byCategory

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        onAddExpense: (() -> Void)? = nil,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.kind = kind
        self.onAddExpense = onAddExpense
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
            onAddExpense: onAddExpense,
            onPreviousMonth: supportsMonthNavigation ? { selectedMonth = selectedMonth.previous() } : nil,
            onNextMonth: supportsMonthNavigation ? { selectedMonth = selectedMonth.next() } : nil,
            onOpenExpense: onOpenExpense
        )
        .id("\(kind.screenType)-\(selectedMonth.id)")
        .toolbarBackground(.hidden, for: .navigationBar)
    }

    private var supportsMonthNavigation: Bool {
        switch kind {
        case .monthly, .shared:
            return true
        case .category, .day:
            return false
        }
    }
}

struct ExpenseGroupingGlassControl: View {
    @Binding var selection: ExpenseGroupingMode

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                ExpenseGroupingGlassButton(
                    title: appLocalized("By Category"),
                    isSelected: selection == .byCategory
                ) {
                    selection = .byCategory
                }

                ExpenseGroupingGlassButton(
                    title: appLocalized("By Date"),
                    isSelected: selection == .byDate
                ) {
                    selection = .byDate
                }
            }
        }
    }
}

private struct ExpenseGroupingGlassButton: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Group {
            if isSelected {
                Button(title, action: action)
                    .buttonStyle(.glassProminent)
            } else {
                Button(title, action: action)
                    .buttonStyle(.glass)
            }
        }
        .font(.subheadline.weight(.semibold))
    }
}

enum MonthlyTransactionsHeaderLayout {
    static let selectorTopSpacing: CGFloat = 14
    static let selectorHeight: CGFloat = 54
    static let bottomSpacing: CGFloat = 20
    static var reservedTopInset: CGFloat {
        MonthNavigationHeaderLayout.topPadding +
            MonthNavigationHeaderLayout.minHeight +
            selectorTopSpacing +
            selectorHeight +
            bottomSpacing
    }
}

struct MonthlyTransactionsRootView: View {
    let initialKind: AddTransactionKind
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
        self.initialKind = initialKind
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
            onOpenExpense: { expenseId in
                path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
            },
            onOpenIncome: { incomeId in
                path.append(Route.addIncome(incomeId: incomeId, year: nil, month: nil))
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

@MainActor
private final class TransactionSearchSectionsViewModel: ObservableObject {
    @Published var expenseTotalAmountText = appAmountLabel("0.00")
    @Published var incomeTotalAmountText = appAmountLabel("0.00")
    @Published var expenseEmptyStateText = appLocalized("No matching transactions")
    @Published var incomeEmptyStateText = appLocalized("No matching transactions")
    @Published var expenseSections: [GroupedExpenseSectionModel] = []
    @Published var incomeSections: [GroupedExpenseSectionModel] = []
    @Published var hasLoadedSnapshot = false
    @Published var expandedExpenseSectionIDs = Set<String>()
    @Published var expandedIncomeSectionIDs = Set<String>()

    private let observer: IosTransactionSearchObserver
    private var knownExpenseSectionIDs = Set<String>()
    private var knownIncomeSectionIDs = Set<String>()
    private var hasLoadedInitialExpansionState = false
    private var isObserving = false

    init(query: String, groupingMode: ExpenseGroupingMode) {
        observer = IosTransactionSearchObserver(
            query: query,
            initialGroupingMode: groupingMode.bridgeValue
        )
    }

    deinit {
        observer.dispose()
    }

    func start() {
        guard !isObserving else {
            return
        }

        isObserving = true
        observer.start { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        guard isObserving else {
            return
        }

        observer.stop()
        isObserving = false
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        observer.setGroupingMode(groupingMode: groupingMode.bridgeValue)
    }

    func deleteExpense(_ expenseID: String) {
        observer.deleteExpense(id: expenseID)
    }

    func deleteIncome(_ incomeID: String) {
        observer.deleteIncome(id: incomeID)
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        observer.deleteRecurringExpenseSeries(seriesId: seriesID)
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        observer.deleteRecurringIncomeSeries(seriesId: seriesID)
    }

    private func apply(snapshot: IosTransactionSearchSnapshot) {
        expenseTotalAmountText = snapshot.expenseSnapshot.totalAmountText
        incomeTotalAmountText = snapshot.incomeSnapshot.totalAmountText
        expenseEmptyStateText = snapshot.expenseSnapshot.emptyStateText
        incomeEmptyStateText = snapshot.incomeSnapshot.emptyStateText
        expenseSections = snapshot.expenseSnapshot.sections.map(groupedExpenseSectionModel)
        incomeSections = snapshot.incomeSnapshot.sections.map { section in
            GroupedExpenseSectionModel(
                id: section.id,
                title: section.title,
                categoryColorKey: section.categoryColorKey,
                categoryIconKey: section.categoryIconKey,
                totalAmountText: section.totalAmountText,
                rows: section.rows.map { row in
                    GroupedExpenseRowModel(
                        id: row.id,
                        title: row.title,
                        subtitleText: row.subtitleText,
                        amountText: row.amountText,
                        categoryColorKey: row.categoryColorKey,
                        categoryIconKey: row.categoryIconKey,
                        recurringSeriesId: row.recurringSeriesId
                    )
                }
            )
        }

        hasLoadedSnapshot = true
        updateExpandedSections()
    }

    private func updateExpandedSections() {
        let incomingExpenseIDs = Set(expenseSections.lazy.map(\.id))
        let incomingIncomeIDs = Set(incomeSections.lazy.map(\.id))

        if hasLoadedInitialExpansionState {
            expandedExpenseSectionIDs.formIntersection(incomingExpenseIDs)
            expandedExpenseSectionIDs.formUnion(incomingExpenseIDs.subtracting(knownExpenseSectionIDs))
            expandedIncomeSectionIDs.formIntersection(incomingIncomeIDs)
            expandedIncomeSectionIDs.formUnion(incomingIncomeIDs.subtracting(knownIncomeSectionIDs))
        } else {
            expandedExpenseSectionIDs = incomingExpenseIDs
            expandedIncomeSectionIDs = incomingIncomeIDs
            hasLoadedInitialExpansionState = true
        }

        knownExpenseSectionIDs = incomingExpenseIDs
        knownIncomeSectionIDs = incomingIncomeIDs
    }

    private func groupedExpenseSectionModel(_ section: IosGroupedExpenseSection) -> GroupedExpenseSectionModel {
        GroupedExpenseSectionModel(
            id: section.id,
            title: section.title,
            categoryColorKey: section.categoryColorKey,
            categoryIconKey: section.categoryIconKey,
            totalAmountText: section.totalAmountText,
            rows: section.rows.map { row in
                GroupedExpenseRowModel(
                    id: row.id,
                    title: row.title,
                    subtitleText: row.subtitleText,
                    amountText: row.amountText,
                    categoryColorKey: row.categoryColorKey,
                    categoryIconKey: row.categoryIconKey,
                    recurringSeriesId: row.recurringSeriesId
                )
            }
        )
    }
}

struct TransactionSearchSectionsRootView: View {
    let query: String
    let onClose: () -> Void
    let onOpenExpense: (String) -> Void
    let onOpenIncome: (String) -> Void

    @State private var selectedKind: AddTransactionKind = .expense
    @State private var groupingMode: ExpenseGroupingMode = .byCategory
    @StateObject private var viewModel: TransactionSearchSectionsViewModel
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
        _viewModel = StateObject(
            wrappedValue: TransactionSearchSectionsViewModel(
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
            } else if currentSections.isEmpty {
                Section {
                    AppGlassListCard {
                        Text(currentEmptyStateText)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            } else {
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
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(.compact)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) {
            Color.clear.frame(height: TransactionSearchHeaderLayout.reservedTopInset)
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

    private var currentExpandedSectionIDs: Set<String> {
        selectedKind == .income ? viewModel.expandedIncomeSectionIDs : viewModel.expandedExpenseSectionIDs
    }

    private func toggleExpandedSection(_ sectionID: String) {
        switch selectedKind {
        case .expense:
            if viewModel.expandedExpenseSectionIDs.contains(sectionID) {
                viewModel.expandedExpenseSectionIDs.remove(sectionID)
            } else {
                viewModel.expandedExpenseSectionIDs.insert(sectionID)
            }
        case .income:
            if viewModel.expandedIncomeSectionIDs.contains(sectionID) {
                viewModel.expandedIncomeSectionIDs.remove(sectionID)
            } else {
                viewModel.expandedIncomeSectionIDs.insert(sectionID)
            }
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
        if row.isRecurring {
            AppGlassRecurringDeleteConfirmationDialog(
                message: appLocalized("Choose whether to delete only this instance of \"%@\" or the whole series.", row.title),
                onDeleteInstance: {
                    viewModel.deleteExpense(row.id)
                    pendingExpenseDeleteID = nil
                },
                onDeleteSeries: {
                    if let seriesID = row.recurringSeriesId {
                        viewModel.deleteRecurringExpenseSeries(seriesID)
                    }
                    pendingExpenseDeleteID = nil
                },
                onCancel: {
                    pendingExpenseDeleteID = nil
                }
            )
        } else {
            AppGlassDeleteConfirmationDialog(
                message: appLocalized("\"%@\" will be permanently deleted.", row.title),
                onDelete: {
                    viewModel.deleteExpense(row.id)
                    pendingExpenseDeleteID = nil
                },
                onCancel: {
                    pendingExpenseDeleteID = nil
                }
            )
        }
    }

    @ViewBuilder
    private func incomeDeleteDialog(for row: GroupedExpenseRowModel) -> some View {
        if row.isRecurring {
            AppGlassRecurringDeleteConfirmationDialog(
                message: appLocalized("Choose whether to delete only this instance of \"%@\" or the whole series.", row.title),
                onDeleteInstance: {
                    viewModel.deleteIncome(row.id)
                    pendingIncomeDeleteID = nil
                },
                onDeleteSeries: {
                    if let seriesID = row.recurringSeriesId {
                        viewModel.deleteRecurringIncomeSeries(seriesID)
                    }
                    pendingIncomeDeleteID = nil
                },
                onCancel: {
                    pendingIncomeDeleteID = nil
                }
            )
        } else {
            AppGlassDeleteConfirmationDialog(
                message: appLocalized("\"%@\" will be permanently deleted.", row.title),
                onDelete: {
                    viewModel.deleteIncome(row.id)
                    pendingIncomeDeleteID = nil
                },
                onCancel: {
                    pendingIncomeDeleteID = nil
                }
            )
        }
    }

    private var pendingExpenseDeleteRow: GroupedExpenseRowModel? {
        row(withID: pendingExpenseDeleteID, in: viewModel.expenseSections)
    }

    private var pendingIncomeDeleteRow: GroupedExpenseRowModel? {
        row(withID: pendingIncomeDeleteID, in: viewModel.incomeSections)
    }

    private func row(
        withID rowID: String?,
        in sections: [GroupedExpenseSectionModel]
    ) -> GroupedExpenseRowModel? {
        guard let rowID else {
            return nil
        }

        for section in sections {
            if let row = section.rows.first(where: { $0.id == rowID }) {
                return row
            }
        }

        return nil
    }
}

private enum TransactionSearchHeaderLayout {
    static var reservedTopInset: CGFloat {
        MonthNavigationHeaderLayout.topPadding +
            MonthNavigationHeaderLayout.minHeight +
            MonthlyTransactionsHeaderLayout.selectorTopSpacing +
            MonthlyTransactionsHeaderLayout.selectorHeight +
            MonthlyTransactionsHeaderLayout.bottomSpacing
    }
}

private struct TransactionSearchGlassHeader: View {
    let query: String
    let amountText: String

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 4) {
            Text(appLocalized("Search Results"))
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(.primary)
                .lineLimit(1)

            Text(query)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Text(amountText)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: MonthNavigationHeaderLayout.minHeight)
        .padding(.horizontal, 16)
        .appGlassSurface(cornerRadius: 20)
        .shadow(
            color: Color.black.opacity(colorScheme == .dark ? 0.26 : 0.10),
            radius: 18,
            x: 0,
            y: 10
        )
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
                    onAddExpense: nil,
                    onPreviousMonth: { selectedMonth = selectedMonth.previous() },
                    onNextMonth: { selectedMonth = selectedMonth.next() },
                    onOpenExpense: onOpenExpense,
                    headerAmountDescriptor: appLocalized("Expenses"),
                    topReservedInset: MonthlyTransactionsHeaderLayout.reservedTopInset,
                    headerAccessory: transactionKindSelector,
                    expandsSectionsInitially: false
                )
            case .income:
                MonthlyIncomesSectionsContent(
                    selectedMonth: selectedMonth,
                    onPreviousMonth: { selectedMonth = selectedMonth.previous() },
                    onNextMonth: { selectedMonth = selectedMonth.next() },
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

    private func transactionKindSelector() -> AnyView {
        AnyView(
            MonthlyTransactionKindGlassControl(selection: $selectedKind)
                .padding(.horizontal, MonthNavigationHeaderLayout.horizontalPadding)
        )
    }
}

struct MonthlyTransactionKindGlassControl: View {
    @Binding var selection: AddTransactionKind

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                MonthlyTransactionKindGlassButton(
                    title: appLocalized("Expenses"),
                    systemImage: "cart.fill",
                    isSelected: selection == .expense
                ) {
                    selection = .expense
                }

                MonthlyTransactionKindGlassButton(
                    title: appLocalized("Income"),
                    systemImage: "banknote.fill",
                    isSelected: selection == .income
                ) {
                    selection = .income
                }
            }
        }
        .frame(height: MonthlyTransactionsHeaderLayout.selectorHeight)
    }
}

private struct MonthlyTransactionKindGlassButton: View {
    let title: String
    let systemImage: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Group {
            if isSelected {
                Button(action: action) {
                    label
                }
                .buttonStyle(.glassProminent)
            } else {
                Button(action: action) {
                    label
                }
                .buttonStyle(.glass)
            }
        }
        .font(.subheadline.weight(.semibold))
    }

    private var label: some View {
        HStack(spacing: 8) {
            Image(systemName: systemImage)
            Text(title)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 40)
        .contentShape(Rectangle())
    }
}

func monthlyHeaderAmountText(descriptor: String?, amountText: String) -> String {
    guard let descriptor, !descriptor.isEmpty else {
        return amountText
    }

    return "\(descriptor) • \(amountText)"
}

struct MonthlyIncomesSectionsScreen: View {
    let onOpenIncome: (String) -> Void
    @State private var selectedMonth: MonthCursor

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
            onPreviousMonth: { selectedMonth = selectedMonth.previous() },
            onNextMonth: { selectedMonth = selectedMonth.next() },
            onOpenIncome: onOpenIncome
        )
        .id(selectedMonth.id)
    }
}

private struct MonthlyIncomesSectionsContent: View {
    let selectedMonth: MonthCursor
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void
    let onOpenIncome: (String) -> Void
    let headerAmountDescriptor: String?
    let topReservedInset: CGFloat?
    let headerAccessory: (() -> AnyView)?

    @StateObject private var viewModel: MonthlyIncomesSectionsViewModel
    @State private var pendingIncomeDeleteID: String?

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

    init(
        selectedMonth: MonthCursor,
        onPreviousMonth: @escaping () -> Void,
        onNextMonth: @escaping () -> Void,
        onOpenIncome: @escaping (String) -> Void,
        headerAmountDescriptor: String? = nil,
        topReservedInset: CGFloat? = nil,
        headerAccessory: (() -> AnyView)? = nil,
        groupingMode: ExpenseGroupingMode = .byDate
    ) {
        self.selectedMonth = selectedMonth
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenIncome = onOpenIncome
        self.headerAmountDescriptor = headerAmountDescriptor
        self.topReservedInset = topReservedInset
        self.headerAccessory = headerAccessory
        _viewModel = StateObject(
            wrappedValue: MonthlyIncomesSectionsViewModel(
                year: selectedMonth.year,
                month: selectedMonth.month,
                groupingMode: groupingMode
            )
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            List {
                if !viewModel.hasLoadedSnapshot {
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
            } else if viewModel.sections.isEmpty {
                Section {
                    AppGlassListCard {
                        Text(viewModel.emptyStateText)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            } else {
                ForEach(viewModel.sections) { section in
                    Section {
                        if viewModel.expandedSectionIDs.contains(section.id) {
                            ForEach(section.rows) { row in
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
                        }
                    } header: {
                        Button {
                            toggleExpandedSection(section.id)
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
            }
            .listStyle(.insetGrouped)
            .listSectionSpacing(.compact)
            .scrollContentBackground(.hidden)
            .safeAreaInset(edge: .top, spacing: 0) {
                Color.clear.frame(height: topReservedInset ?? MonthNavigationHeaderLayout.reservedTopInset)
            }

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
            viewModel.start()
        }
        .onDisappear {
            viewModel.stop()
        }
        .overlay {
            if let pendingIncomeDeleteRow {
                AppGlassDialogOverlay {
                    incomeDeleteDialog(for: pendingIncomeDeleteRow)
                }
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

    private func toggleExpandedSection(_ sectionID: String) {
        if viewModel.expandedSectionIDs.contains(sectionID) {
            viewModel.expandedSectionIDs.remove(sectionID)
        } else {
            viewModel.expandedSectionIDs.insert(sectionID)
        }
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

    private var pendingIncomeDeleteRow: GroupedExpenseRowModel? {
        guard let pendingIncomeDeleteID else {
            return nil
        }

        for section in viewModel.sections {
            if let row = section.rows.first(where: { $0.id == pendingIncomeDeleteID }) {
                return row
            }
        }

        return nil
    }

}

private struct GroupedExpensesSectionsList: View {
    let kind: GroupedExpensesKind
    let year: Int
    let month: Int
    let selectedMonth: MonthCursor
    let onAddExpense: (() -> Void)?
    let onPreviousMonth: (() -> Void)?
    let onNextMonth: (() -> Void)?
    let onOpenExpense: (String) -> Void
    let headerAmountDescriptor: String?
    let topReservedInset: CGFloat?
    let headerAccessory: (() -> AnyView)?
    let expandsSectionsInitially: Bool

    @Binding private var groupingMode: ExpenseGroupingMode
    @StateObject private var viewModel: GroupedExpensesSectionsViewModel
    @State private var pendingExpenseDeleteID: String?

    @ViewBuilder
    private var monthHeader: some View {
        if let onPreviousMonth, let onNextMonth {
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
    }

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        selectedMonth: MonthCursor,
        groupingMode: Binding<ExpenseGroupingMode>,
        onAddExpense: (() -> Void)?,
        onPreviousMonth: (() -> Void)?,
        onNextMonth: (() -> Void)?,
        onOpenExpense: @escaping (String) -> Void,
        headerAmountDescriptor: String? = nil,
        topReservedInset: CGFloat? = nil,
        headerAccessory: (() -> AnyView)? = nil,
        expandsSectionsInitially: Bool = true
    ) {
        self.kind = kind
        self.year = year
        self.month = month
        self.selectedMonth = selectedMonth
        self.onAddExpense = onAddExpense
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenExpense = onOpenExpense
        self.headerAmountDescriptor = headerAmountDescriptor
        self.topReservedInset = topReservedInset
        self.headerAccessory = headerAccessory
        self.expandsSectionsInitially = expandsSectionsInitially
        _groupingMode = groupingMode
        _viewModel = StateObject(
            wrappedValue: GroupedExpensesSectionsViewModel(
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
            List {
                if viewModel.sections.isEmpty {
                Section {
                    AppGlassListCard {
                        Text(viewModel.emptyStateText)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            } else {
                ForEach(viewModel.sections) { section in
                    Section {
                        if viewModel.expandedSectionIDs.contains(section.id) {
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
                                isExpanded: viewModel.expandedSectionIDs.contains(section.id)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            }
            .listStyle(.insetGrouped)
            .listSectionSpacing(.compact)
            .scrollContentBackground(.hidden)
            .safeAreaInset(edge: .top, spacing: 0) {
                if onPreviousMonth != nil, onNextMonth != nil {
                    Color.clear.frame(height: topReservedInset ?? MonthNavigationHeaderLayout.reservedTopInset)
                }
            }

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
            if showsGroupingControl {
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
        }
        .overlay {
            if let pendingExpenseDeleteRow {
                AppGlassDialogOverlay {
                    expenseDeleteDialog(for: pendingExpenseDeleteRow)
                }
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

    private func toggleExpandedSection(_ sectionID: String) {
        if viewModel.expandedSectionIDs.contains(sectionID) {
            viewModel.expandedSectionIDs.remove(sectionID)
        } else {
            viewModel.expandedSectionIDs.insert(sectionID)
        }
    }

    private var screenTitle: String {
        switch kind {
        case .monthly:
            return appMonthlyTitle(month: selectedMonth.month, key: "Expenses")
        case .shared:
            return appMonthlyTitle(month: selectedMonth.month, key: "Shared Expenses")
        case let .category(name):
            return "\(monthName(selectedMonth.month)) \(name)"
        case let .day(day):
            return "\(day) \(monthName(selectedMonth.month))"
        }
    }

    private var screenDescriptor: String {
        switch kind {
        case .monthly:
            return appLocalized("Expenses")
        case .shared:
            return appLocalized("Shared Expenses")
        case let .category(name):
            return name
        case let .day(day):
            return "\(day) \(monthName(selectedMonth.month))"
        }
    }

    private var canAddExpense: Bool {
        switch kind {
        case .monthly:
            return true
        case .shared, .category, .day:
            return false
        }
    }

    private var showsGroupingControl: Bool {
        switch kind {
        case .day:
            return false
        case .monthly, .shared, .category:
            return true
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
    private func rowView(for row: GroupedExpenseRowModel) -> some View {
        if kind.allowsDelete {
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
        } else {
            Button {
                onOpenExpense(row.id)
            } label: {
                GroupedExpenseRowView(row: row)
            }
            .buttonStyle(.plain)
        }
    }

    private var pendingExpenseDeleteRow: GroupedExpenseRowModel? {
        guard let pendingExpenseDeleteID else {
            return nil
        }

        for section in viewModel.sections {
            if let row = section.rows.first(where: { $0.id == pendingExpenseDeleteID }) {
                return row
            }
        }

        return nil
    }

}

struct GroupedExpenseSectionHeaderView: View {
    let section: GroupedExpenseSectionModel
    let isExpanded: Bool

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .rotationEffect(.degrees(isExpanded ? 90 : 0))
                .frame(width: 12)

            CategoryIconLabelView(
                colorKey: section.categoryColorKey,
                iconKey: section.categoryIconKey,
                text: section.title,
                showIcon: section.categoryIconKey != nil
            )

            Spacer()

            Text(section.totalAmountText)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(.top, 4)
        .textCase(nil)
    }
}

enum MonthNavigationHeaderLayout {
    static let horizontalPadding: CGFloat = 22
    static let topPadding: CGFloat = 16
    static let bottomSpacing: CGFloat = 22
    static let minHeight: CGFloat = 76
    static var reservedTopInset: CGFloat { topPadding + minHeight + bottomSpacing }
}

private struct DashboardStyleMonthNavigationHeader: View {
    let selectedMonth: MonthCursor
    let amountText: String
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 2) {
                Button(action: onPreviousMonth) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 24, height: 24)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
                .accessibilityLabel(appLocalized("Previous month"))

                Text(selectedMonth.label)
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Button(action: onNextMonth) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 24, height: 24)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
                .accessibilityLabel(appLocalized("Next month"))
            }

            Text(amountText)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: MonthNavigationHeaderLayout.minHeight)
        .padding(.horizontal, 16)
        .appGlassSurface(cornerRadius: 20)
        .shadow(
            color: Color.black.opacity(colorScheme == .dark ? 0.26 : 0.10),
            radius: 18,
            x: 0,
            y: 10
        )
    }
}


struct GroupedExpenseRowView: View {
    let row: GroupedExpenseRowModel

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center, spacing: 8) {
                    if row.categoryIconKey != nil {
                        CategoryIconView(colorKey: row.categoryColorKey, iconKey: row.categoryIconKey)
                    }
                    if row.isRecurring {
                        RecurringBadgeView()
                    }

                    Text(row.title)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                Text(row.subtitleText)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(row.amountText)
                .foregroundStyle(.primary)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

private struct CategoryIconLabelView: View {
    let colorKey: String?
    let iconKey: String?
    let text: String
    var showIcon: Bool = true

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            if showIcon {
                CategoryIconView(colorKey: colorKey, iconKey: iconKey)
            }
            Text(text)
                .lineLimit(1)
        }
    }
}

private struct CategoryIconView: View {
    let colorKey: String?
    let iconKey: String?

    var body: some View {
        Image(systemName: categorySystemImageName(iconKey))
            .foregroundStyle(categoryIconColor(colorKey))
            .frame(width: 18, height: 18)
    }
}

private func categorySystemImageName(_ iconKey: String?) -> String {
    switch normalizedCategoryIconKey(iconKey) {
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
    case "receipt":
        return "receipt.fill"
    case "person":
        return "person.fill"
    case "work":
        return "briefcase.fill"
    case "school":
        return "graduationcap.fill"
    case "pets":
        return "pawprint.fill"
    case "fitness_center":
        return "figure.strengthtraining.traditional"
    case "spa":
        return "leaf.fill"
    default:
        return "square.grid.2x2.fill"
    }
}

private func normalizedCategoryIconKey(_ iconKey: String?) -> String {
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

private struct RecurringBadgeView: View {
    var body: some View {
        Text("R")
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(.red, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            .fixedSize()
    }
}
