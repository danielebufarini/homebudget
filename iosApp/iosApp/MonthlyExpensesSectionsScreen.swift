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

private struct GroupedExpenseRowModel: Identifiable {
    let id: String
    let title: String
    let dateText: String
    let amountText: String
}

private struct GroupedExpenseSectionModel: Identifiable {
    let id: String
    let title: String
    let countLabel: String
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

    init(year: Int, month: Int, kind: GroupedExpensesKind) {
        observer = IosGroupedExpensesObserver(
            year: Int32(year),
            month: Int32(month),
            screenType: kind.screenType,
            categoryName: kind.categoryName
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

    private func apply(snapshot: IosGroupedExpensesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        emptyStateText = snapshot.emptyStateText
        sections = snapshot.sections.map { section in
            GroupedExpenseSectionModel(
                id: section.id,
                title: section.title,
                countLabel: section.countLabel,
                totalAmountText: section.totalAmountText,
                rows: section.rows.map { row in
                    GroupedExpenseRowModel(
                        id: row.id,
                        title: row.title,
                        dateText: row.dateText,
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
    let onOpenExpense: (String) -> Void

    @StateObject private var viewModel: GroupedExpensesSectionsViewModel

    init(kind: GroupedExpensesKind, year: Int, month: Int, onOpenExpense: @escaping (String) -> Void) {
        self.kind = kind
        self.onOpenExpense = onOpenExpense
        _viewModel = StateObject(
            wrappedValue: GroupedExpensesSectionsViewModel(year: year, month: month, kind: kind)
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
                Section {
                    LabeledContent("Total", value: viewModel.totalAmountText)
                        .font(.headline)
                }

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
            VStack(alignment: .leading, spacing: 2) {
                Text(section.title)
                Text(section.countLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

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
                Text(row.dateText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(row.amountText)
                .foregroundStyle(.primary)
        }
        .contentShape(Rectangle())
    }
}
