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
    let categoryColorKey: String?
    let categoryIconKey: String?
    let recurringSeriesId: String?

    var isRecurring: Bool {
        if let recurringSeriesId {
            !recurringSeriesId.isEmpty
        } else {
            false
        }
    }
}

private struct GroupedExpenseSectionModel: Identifiable {
    let id: String
    let title: String
    let categoryColorKey: String?
    let categoryIconKey: String?
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
    private var knownSectionIDs = Set<String>()
    private var isObserving = false

    init(year: Int, month: Int, kind: GroupedExpensesKind, groupingMode: ExpenseGroupingMode) {
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
        sections = snapshot.sections.map { section in
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

@MainActor
private final class MonthlyIncomesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = appAmountLabel("0.00")
    @Published var emptyStateText = appLocalized("No income for this month")
    @Published var sections: [GroupedExpenseSectionModel] = []
    @Published var expandedSectionIDs = Set<String>()

    private let observer: IosMonthlyIncomesObserver
    private var hasLoadedInitialExpansionState = false
    private var knownSectionIDs = Set<String>()
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
                categoryColorKey: nil,
                categoryIconKey: nil,
                totalAmountText: section.totalAmountText,
                rows: section.rows.map { row in
                    GroupedExpenseRowModel(
                        id: row.id,
                        title: row.title,
                        subtitleText: row.subtitleText,
                        amountText: row.amountText,
                        categoryColorKey: nil,
                        categoryIconKey: nil,
                        recurringSeriesId: row.recurringSeriesId
                    )
                }
            )
        }

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

private struct ExpenseGroupingGlassControl: View {
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
    @State private var pendingIncomeDeleteID: String?

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
        .toolbarBackground(.hidden, for: .navigationBar)
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
        .overlay {
            if let pendingIncomeDeleteRow {
                AppGlassDialogOverlay {
                    incomeDeleteDialog(for: pendingIncomeDeleteRow)
                }
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

    @Binding private var groupingMode: ExpenseGroupingMode
    @StateObject private var viewModel: GroupedExpensesSectionsViewModel
    @State private var pendingExpenseDeleteID: String?

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        selectedMonth: MonthCursor,
        groupingMode: Binding<ExpenseGroupingMode>,
        onAddExpense: (() -> Void)?,
        onPreviousMonth: (() -> Void)?,
        onNextMonth: (() -> Void)?,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.kind = kind
        self.year = year
        self.month = month
        self.selectedMonth = selectedMonth
        self.onAddExpense = onAddExpense
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
        self.onOpenExpense = onOpenExpense
        _groupingMode = groupingMode
        _viewModel = StateObject(
            wrappedValue: GroupedExpensesSectionsViewModel(
                year: year,
                month: month,
                kind: kind,
                groupingMode: groupingMode.wrappedValue
            )
        )
    }

    var body: some View {
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
        .toolbarBackground(.hidden, for: .navigationBar)
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
                        AppGlassToolbarIcon(systemName: "plus")
                    }
                    .buttonStyle(.glass)
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
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if showsGroupingControl {
                ExpenseGroupingGlassControl(selection: $groupingMode)
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

private struct GroupedExpenseSectionHeaderView: View {
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

private struct MonthNavigationToolbarTitle: View {
    let selectedMonth: MonthCursor
    let subtitle: String
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void

    var body: some View {
        VStack(spacing: 3) {
            AppGlassToolbarCluster {
                Button(action: onPreviousMonth) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.glass)

                Text(selectedMonth.label)
                    .font(.headline)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .appGlassSurface(cornerRadius: 18)

                Button(action: onNextMonth) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.glass)
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
