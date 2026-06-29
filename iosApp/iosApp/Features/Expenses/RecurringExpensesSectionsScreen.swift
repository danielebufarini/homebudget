@preconcurrency import ComposeApp
import SwiftUI
import Observation

struct RecurringExpensesSectionsScreen: View {
    let year: Int
    let month: Int
    let onClose: () -> Void
    let onOpenExpense: (String) -> Void

    @State private var viewModel: RecurringExpensesSectionsViewModel

    init(
        year: Int,
        month: Int,
        onClose: @escaping () -> Void,
        onOpenExpense: @escaping (String) -> Void
    ) {
        self.year = year
        self.month = month
        self.onClose = onClose
        self.onOpenExpense = onOpenExpense
        _viewModel = State(
            initialValue: RecurringExpensesSectionsViewModel(
                year: year,
                month: month
            )
        )
    }

    var body: some View {
        ZStack(alignment: .top) {
            recurringList

            headerStack
                .zIndex(1)
        }
        .background(AppGlassBackdrop().ignoresSafeArea())
        .appGlassHostedScreenChrome()
        .toolbar(.hidden, for: .navigationBar)
        .task {
            await viewModel.observeSnapshots()
        }
    }

    private var recurringList: some View {
        List {
            if !viewModel.hasLoadedSnapshot {
                loadingSection
            } else if viewModel.rows.isEmpty {
                emptySection
            } else {
                rowsSection
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(.compact)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) {
            Color.clear.frame(height: RecurringExpensesChromeLayout.reservedTopInset)
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
                VStack(alignment: .leading, spacing: 6) {
                    Text(viewModel.emptyStateTitle)
                        .font(.headline)
                    Text(viewModel.emptyStateDescription)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    private var rowsSection: some View {
        Section {
            ForEach(viewModel.rows) { row in
                Button {
                    onOpenExpense(row.id)
                } label: {
                    GroupedExpenseRowView(row: row)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var headerStack: some View {
        VStack(spacing: 0) {
            ExpenseEditorGlassHeader(
                title: appLocalized("Recurring"),
                showsDeleteAction: false,
                onBack: onClose,
                onDelete: {}
            )
            .padding(.horizontal, ExpenseEditorChromeLayout.horizontalPadding)
            .padding(.top, ExpenseEditorChromeLayout.topPadding)

            Color.clear
                .frame(height: RecurringExpensesChromeLayout.summaryTopSpacing)

            RecurringExpensesSummaryCard(
                title: viewModel.totalRecurringText,
                subtitle: viewModel.recurringExpensesText,
                amountText: viewModel.totalAmountText
            )
            .padding(.horizontal, RecurringExpensesChromeLayout.horizontalPadding)
        }
    }
}

@MainActor
@Observable
final class RecurringExpensesSectionsViewModel {
    var totalAmountText = "\(appAmountLabel("0.00")) / \(appLocalized("month"))"
    var totalRecurringText = appLocalized("Total recurring")
    var recurringExpensesText = appLocalized("Recurring expenses")
    var emptyStateTitle = appLocalized("No recurring expenses")
    var emptyStateDescription = appLocalized("Recurring expenses will appear here.")
    var rows: [GroupedExpenseRowModel] = []
    var hasLoadedSnapshot = false

    private let observer: IosRecurringExpensesObserver

    init(year: Int, month: Int) {
        observer = IosRecurringExpensesObserver(
            year: Int32(year),
            month: Int32(month)
        )
    }

    func observeSnapshots() async {
        for await snapshot in observer.snapshots {
            apply(snapshot: snapshot)
        }
    }

    private func apply(snapshot: IosRecurringExpensesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        totalRecurringText = snapshot.totalRecurringText
        recurringExpensesText = snapshot.recurringExpensesText
        emptyStateTitle = snapshot.emptyStateTitle
        emptyStateDescription = snapshot.emptyStateDescription
        rows = snapshot.rows.map(GroupedExpenseRowModel.init)
        hasLoadedSnapshot = true
    }
}

private struct RecurringExpensesSummaryCard: View {
    let title: String
    let subtitle: String
    let amountText: String

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 12)

            Text(amountText)
                .font(.title2.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 22)
        .frame(maxWidth: .infinity)
        .frame(height: RecurringExpensesChromeLayout.summaryHeight)
        .appGlassSurface(cornerRadius: 22)
    }
}

private enum RecurringExpensesChromeLayout {
    static let horizontalPadding: CGFloat = 16
    static let summaryTopSpacing: CGFloat = 16
    static let summaryHeight: CGFloat = 108
    static let bottomSpacing: CGFloat = 20
    static var reservedTopInset: CGFloat {
        ExpenseEditorChromeLayout.topPadding +
            ExpenseEditorChromeLayout.headerHeight +
            summaryTopSpacing +
            summaryHeight +
            bottomSpacing
    }
}
