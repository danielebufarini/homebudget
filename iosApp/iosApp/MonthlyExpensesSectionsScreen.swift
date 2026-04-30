@preconcurrency import ComposeApp
import SwiftUI

enum GroupedExpensesKind: Hashable {
    case monthly
    case shared
    case category(name: String)

    var screenType: String {
        switch self {
        case .monthly:
            return "monthly"
        case .shared:
            return "shared"
        case .category:
            return "category"
        }
    }

    var categoryName: String? {
        switch self {
        case let .category(name):
            return name
        case .monthly, .shared:
            return nil
        }
    }

    var allowsDelete: Bool {
        true
    }
}

private enum ExpenseGroupingMode: String, Hashable {
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

private struct GroupedExpenseRowModel: Identifiable {
    let id: String
    let title: String
    let subtitleText: String
    let amountText: String
    let recurringSeriesId: String?

    var isRecurring: Bool {
        recurringSeriesId != nil
    }
}

private struct GroupedExpenseSectionModel: Identifiable {
    let id: String
    let title: String
    let totalAmountText: String
    let rows: [GroupedExpenseRowModel]
}

@MainActor
private final class GroupedExpensesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = appAmountLabel("0.00")
    @Published var emptyStateText = appLocalized("No expenses for this month")
    @Published var sections: [GroupedExpenseSectionModel] = []
    @Published var expandedSectionIDs = Set<String>()

    private let observer: IosGroupedExpensesObserver
    private var hasLoadedInitialExpansionState = false
    private var isObserving = false

    init(year: Int, month: Int, kind: GroupedExpensesKind, groupingMode: ExpenseGroupingMode) {
        observer = IosGroupedExpensesObserver(
            year: Int32(year),
            month: Int32(month),
            screenType: kind.screenType,
            categoryName: kind.categoryName,
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
        sections = snapshot.sections.map { section in
            GroupedExpenseSectionModel(
                id: section.id,
                title: section.title,
                totalAmountText: section.totalAmountText,
                rows: section.rows.map { row in
                    GroupedExpenseRowModel(
                        id: row.id,
                        title: row.title,
                        subtitleText: row.subtitleText,
                        amountText: row.amountText,
                        recurringSeriesId: row.recurringSeriesId
                    )
                }
            )
        }

        let incomingIDs = Set(sections.map(\.id))
        if hasLoadedInitialExpansionState {
            expandedSectionIDs.formUnion(incomingIDs)
        } else {
            expandedSectionIDs = incomingIDs
            hasLoadedInitialExpansionState = true
        }
    }
}

@MainActor
private final class MonthlyIncomesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = appAmountLabel("0.00")
    @Published var emptyStateText = appLocalized("No income for this month")
    @Published var sections: [GroupedExpenseSectionModel] = []
    @Published var expandedSectionIDs = Set<String>()

    private let observer: IosMonthlyIncomesObserver
    private var hasLoadedInitialExpansionState = false
    private var isObserving = false

    init(year: Int, month: Int) {
        observer = IosMonthlyIncomesObserver(
            year: Int32(year),
            month: Int32(month)
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
        sections = snapshot.sections.map { section in
            GroupedExpenseSectionModel(
                id: section.id,
                title: section.title,
                totalAmountText: section.totalAmountText,
                rows: section.rows.map { row in
                    GroupedExpenseRowModel(
                        id: row.id,
                        title: row.title,
                        subtitleText: row.subtitleText,
                        amountText: row.amountText,
                        recurringSeriesId: row.recurringSeriesId
                    )
                }
            )
        }

        let incomingIDs = Set(sections.map(\.id))
        if hasLoadedInitialExpansionState {
            expandedSectionIDs.formUnion(incomingIDs)
        } else {
            expandedSectionIDs = incomingIDs
            hasLoadedInitialExpansionState = true
        }
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
            groupingMode: groupingMode,
            onAddExpense: onAddExpense,
            onPreviousMonth: supportsMonthNavigation ? { selectedMonth = selectedMonth.previous() } : nil,
            onNextMonth: supportsMonthNavigation ? { selectedMonth = selectedMonth.next() } : nil,
            onOpenExpense: onOpenExpense
        )
        .id("\(kind.screenType)-\(selectedMonth.id)")
        .safeAreaInset(edge: .bottom) {
            Color.clear
                .frame(height: 64)
                .allowsHitTesting(false)
        }
        .overlay(alignment: .bottom) {
            Picker("Expense Grouping", selection: $groupingMode) {
                Text("By Category").tag(ExpenseGroupingMode.byCategory)
                Text("By Date").tag(ExpenseGroupingMode.byDate)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .padding(.horizontal, 16)
            .padding(.bottom, 10)
        }
    }

    private var supportsMonthNavigation: Bool {
        switch kind {
        case .monthly, .shared:
            return true
        case .category:
            return false
        }
    }
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

    @StateObject private var viewModel: MonthlyIncomesSectionsViewModel
    @State private var recurringIncomeToDelete: GroupedExpenseRowModel?

    init(
        selectedMonth: MonthCursor,
        onPreviousMonth: @escaping () -> Void,
        onNextMonth: @escaping () -> Void,
        onOpenIncome: @escaping (String) -> Void
    ) {
        self.selectedMonth = selectedMonth
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenIncome = onOpenIncome
        _viewModel = StateObject(
            wrappedValue: MonthlyIncomesSectionsViewModel(
                year: selectedMonth.year,
                month: selectedMonth.month
            )
        )
    }

    var body: some View {
        List {
            if viewModel.sections.isEmpty {
                Section {
                    Text(viewModel.emptyStateText)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                ForEach(viewModel.sections) { section in
                    Section(isExpanded: expansionBinding(for: section.id)) {
                        ForEach(section.rows) { row in
                            Button {
                                onOpenIncome(row.id)
                            } label: {
                                GroupedExpenseRowView(row: row)
                            }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .trailing, allowsFullSwipe: !row.isRecurring) {
                                Button {
                                    if row.isRecurring {
                                        recurringIncomeToDelete = row
                                    } else {
                                        viewModel.deleteIncome(row.id)
                                    }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                                .tint(.red)
                            }
                            .confirmationDialog(
                                "Delete",
                                isPresented: recurringIncomeDialogBinding(for: row),
                                titleVisibility: .visible
                            ) {
                                Button("This instance only", role: .destructive) {
                                    viewModel.deleteIncome(row.id)
                                    recurringIncomeToDelete = nil
                                }
                                Button("Whole series", role: .destructive) {
                                    if let seriesID = row.recurringSeriesId {
                                        viewModel.deleteRecurringIncomeSeries(seriesID)
                                    }
                                    recurringIncomeToDelete = nil
                                }
                            }
                        }
                    } header: {
                        GroupedExpenseSectionHeaderView(section: section)
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                MonthNavigationToolbarTitle(
                    selectedMonth: selectedMonth,
                    subtitle: "\(appLocalized("Income")) • \(viewModel.totalAmountText)",
                    onPreviousMonth: onPreviousMonth,
                    onNextMonth: onNextMonth
                )
            }
        }
        .onAppear {
            viewModel.start()
        }
        .onDisappear {
            viewModel.stop()
        }
    }

    private func expansionBinding(for sectionID: String) -> Binding<Bool> {
        Binding(
            get: {
                viewModel.expandedSectionIDs.contains(sectionID)
            },
            set: { isExpanded in
                var updated = viewModel.expandedSectionIDs
                if isExpanded {
                    updated.insert(sectionID)
                } else {
                    updated.remove(sectionID)
                }
                viewModel.expandedSectionIDs = updated
            }
        )
    }

    private func recurringIncomeDialogBinding(for row: GroupedExpenseRowModel) -> Binding<Bool> {
        Binding(
            get: {
                recurringIncomeToDelete?.id == row.id
            },
            set: { isPresented in
                if !isPresented {
                    recurringIncomeToDelete = nil
                }
            }
        )
    }
}

private struct GroupedExpensesSectionsList: View {
    let kind: GroupedExpensesKind
    let year: Int
    let month: Int
    let selectedMonth: MonthCursor
    let groupingMode: ExpenseGroupingMode
    let onAddExpense: (() -> Void)?
    let onPreviousMonth: (() -> Void)?
    let onNextMonth: (() -> Void)?
    let onOpenExpense: (String) -> Void

    @StateObject private var viewModel: GroupedExpensesSectionsViewModel
    @State private var recurringExpenseToDelete: GroupedExpenseRowModel?

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        selectedMonth: MonthCursor,
        groupingMode: ExpenseGroupingMode,
        onAddExpense: (() -> Void)?,
        onPreviousMonth: (() -> Void)?,
        onNextMonth: (() -> Void)?,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.kind = kind
        self.year = year
        self.month = month
        self.selectedMonth = selectedMonth
        self.groupingMode = groupingMode
        self.onAddExpense = onAddExpense
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenExpense = onOpenExpense
        _viewModel = StateObject(
            wrappedValue: GroupedExpensesSectionsViewModel(
                year: year,
                month: month,
                kind: kind,
                groupingMode: groupingMode
            )
        )
    }

    var body: some View {
        List {
            if viewModel.sections.isEmpty {
                Section {
                    Text(viewModel.emptyStateText)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                ForEach(viewModel.sections) { section in
                    Section(isExpanded: expansionBinding(for: section.id)) {
                        ForEach(section.rows) { row in
                            rowView(for: row)
                        }
                    } header: {
                        GroupedExpenseSectionHeaderView(section: section)
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                if let onPreviousMonth, let onNextMonth {
                    MonthNavigationToolbarTitle(
                        selectedMonth: selectedMonth,
                        subtitle: "\(screenDescriptor) • \(viewModel.totalAmountText)",
                        onPreviousMonth: onPreviousMonth,
                        onNextMonth: onNextMonth
                    )
                } else {
                    VStack(spacing: 1) {
                        Text(screenTitle)
                            .font(.headline)
                        Text(viewModel.totalAmountText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            if let onAddExpense, canAddExpense {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onAddExpense) {
                        Image(systemName: "plus")
                    }
                }
            }
        }
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
    }

    private var screenTitle: String {
        switch kind {
        case .monthly:
            return appMonthlyTitle(month: selectedMonth.month, key: "Expenses")
        case .shared:
            return appMonthlyTitle(month: selectedMonth.month, key: "Shared Expenses")
        case let .category(name):
            return "\(monthName(selectedMonth.month)) \(name)"
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
        }
    }

    private var canAddExpense: Bool {
        switch kind {
        case .monthly:
            return true
        case .shared, .category:
            return false
        }
    }

    private func expansionBinding(for sectionID: String) -> Binding<Bool> {
        Binding(
            get: {
                viewModel.expandedSectionIDs.contains(sectionID)
            },
            set: { isExpanded in
                var updated = viewModel.expandedSectionIDs
                if isExpanded {
                    updated.insert(sectionID)
                } else {
                    updated.remove(sectionID)
                }
                viewModel.expandedSectionIDs = updated
            }
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
            .swipeActions(edge: .trailing, allowsFullSwipe: !row.isRecurring) {
                Button {
                    if row.isRecurring {
                        recurringExpenseToDelete = row
                    } else {
                        viewModel.deleteExpense(row.id)
                    }
                } label: {
                    Label("Delete", systemImage: "trash")
                }
                .tint(.red)
            }
            .confirmationDialog(
                "Delete",
                isPresented: recurringExpenseDialogBinding(for: row),
                titleVisibility: .visible
            ) {
                Button("This instance only", role: .destructive) {
                    viewModel.deleteExpense(row.id)
                    recurringExpenseToDelete = nil
                }
                Button("Whole series", role: .destructive) {
                    if let seriesID = row.recurringSeriesId {
                        viewModel.deleteRecurringExpenseSeries(seriesID)
                    }
                    recurringExpenseToDelete = nil
                }
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

    private func recurringExpenseDialogBinding(for row: GroupedExpenseRowModel) -> Binding<Bool> {
        Binding(
            get: {
                recurringExpenseToDelete?.id == row.id
            },
            set: { isPresented in
                if !isPresented {
                    recurringExpenseToDelete = nil
                }
            }
        )
    }

}

private struct GroupedExpenseSectionHeaderView: View {
    let section: GroupedExpenseSectionModel

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(section.title)

            Spacer()

            Text(section.totalAmountText)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
        }
    }
}

private struct MonthNavigationToolbarTitle: View {
    let selectedMonth: MonthCursor
    let subtitle: String
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void

    var body: some View {
        VStack(spacing: 1) {
            HStack(spacing: 4) {
                Button(action: onPreviousMonth) {
                    Image(systemName: "chevron.left")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.plain)

                Text(selectedMonth.label)
                    .font(.headline)

                Button(action: onNextMonth) {
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.plain)
            }

            Text(subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

private struct GroupedExpenseRowView: View {
    let row: GroupedExpenseRowModel

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(row.title)
                    .foregroundStyle(.primary)
                Text(row.subtitleText)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(row.amountText)
                .foregroundStyle(.primary)
        }
        .contentShape(Rectangle())
    }
}
