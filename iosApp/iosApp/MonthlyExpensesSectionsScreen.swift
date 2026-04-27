@preconcurrency import ComposeApp
import SwiftUI

private struct MonthlyExpenseRowModel: Identifiable {
    let id: String
    let title: String
    let dateText: String
    let amountText: String
}

private struct MonthlyExpenseSectionModel: Identifiable {
    let id: String
    let title: String
    let countLabel: String
    let totalAmountText: String
    let rows: [MonthlyExpenseRowModel]
}

@MainActor
private final class MonthlyExpensesSectionsViewModel: ObservableObject {
    @Published var totalAmountText = "€ 0.00"
    @Published var emptyStateText = "No expenses for this month"
    @Published var sections: [MonthlyExpenseSectionModel] = []
    @Published var expandedSectionIDs = Set<String>()

    private let observer: IosMonthlyExpensesObserver
    private var hasLoadedInitialExpansionState = false
    private var isObserving = false

    init(year: Int, month: Int) {
        observer = IosMonthlyExpensesObserver(year: Int32(year), month: Int32(month))
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

    private func apply(snapshot: IosMonthlyExpensesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        emptyStateText = snapshot.emptyStateText
        sections = snapshot.sections.map { section in
            MonthlyExpenseSectionModel(
                id: section.id,
                title: section.title,
                countLabel: section.countLabel,
                totalAmountText: section.totalAmountText,
                rows: section.rows.map { row in
                    MonthlyExpenseRowModel(
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

struct MonthlyExpensesSectionsScreen: View {
    let year: Int
    let month: Int
    let onOpenExpense: (String) -> Void

    @StateObject private var viewModel: MonthlyExpensesSectionsViewModel

    init(year: Int, month: Int, onOpenExpense: @escaping (String) -> Void) {
        self.year = year
        self.month = month
        self.onOpenExpense = onOpenExpense
        _viewModel = StateObject(wrappedValue: MonthlyExpensesSectionsViewModel(year: year, month: month))
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
                            Button {
                                onOpenExpense(row.id)
                            } label: {
                                MonthlyExpenseRowView(row: row)
                            }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    viewModel.deleteExpense(row.id)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                    } header: {
                        MonthlyExpenseSectionHeaderView(section: section)
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
}

private struct MonthlyExpenseSectionHeaderView: View {
    let section: MonthlyExpenseSectionModel

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

private struct MonthlyExpenseRowView: View {
    let row: MonthlyExpenseRowModel

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
