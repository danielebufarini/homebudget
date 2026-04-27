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

private struct GroupedExpenseRowModel: Identifiable {
    let id: String
    let title: String
    let subtitleText: String
    let amountText: String
}

private struct GroupedExpenseSectionModel: Identifiable {
    let id: String
    let title: String
    let totalAmountText: String
    let rows: [GroupedExpenseRowModel]
}

@MainActor
private final class GroupedExpensesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = "€ 0.00"
    @Published var emptyStateText = "No expenses for this month"
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
                        amountText: row.amountText
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
    let year: Int
    let month: Int
    let onOpenExpense: (String) -> Void

    init(kind: GroupedExpensesKind, year: Int, month: Int, onOpenExpense: @escaping (String) -> Void) {
        self.kind = kind
        self.year = year
        self.month = month
        self.onOpenExpense = onOpenExpense
    }

    @State private var groupingMode: ExpenseGroupingMode = .byCategory

    var body: some View {
        GroupedExpensesSectionsList(
            kind: kind,
            year: year,
            month: month,
            groupingMode: groupingMode,
            onOpenExpense: onOpenExpense
        )
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 12) {
                groupingButton("By Category", mode: .byCategory)
                groupingButton("By Date", mode: .byDate)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .background(.thinMaterial)
        }
    }

    @ViewBuilder
    private func groupingButton(_ title: String, mode: ExpenseGroupingMode) -> some View {
        if mode == groupingMode {
            Button(title) {
                groupingMode = mode
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
        } else {
            Button(title) {
                groupingMode = mode
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
    }
}

private struct GroupedExpensesSectionsList: View {
    let kind: GroupedExpensesKind
    let year: Int
    let month: Int
    let groupingMode: ExpenseGroupingMode
    let onOpenExpense: (String) -> Void

    @StateObject private var viewModel: GroupedExpensesSectionsViewModel

    init(
        kind: GroupedExpensesKind,
        year: Int,
        month: Int,
        groupingMode: ExpenseGroupingMode,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.kind = kind
        self.year = year
        self.month = month
        self.groupingMode = groupingMode
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
                VStack(spacing: 1) {
                    Text(screenTitle)
                        .font(.headline)
                    Text(viewModel.totalAmountText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
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
            return "\(monthName(month)) Expenses"
        case .shared:
            return "\(monthName(month)) Shared Expenses"
        case let .category(name):
            return "\(monthName(month)) \(name)"
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
            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                Button(role: .destructive) {
                    viewModel.deleteExpense(row.id)
                } label: {
                    Label("Delete", systemImage: "trash")
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

private func monthName(_ month: Int) -> String {
    let names = [
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    ]

    let index = month - 1
    guard names.indices.contains(index) else {
        return ""
    }

    return names[index]
}
