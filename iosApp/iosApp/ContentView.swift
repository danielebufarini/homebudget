@preconcurrency import ComposeApp
import SwiftUI
import UIKit
import UniformTypeIdentifiers

private enum Route: Hashable {
    case categories
    case addExpense(expenseId: String?, readOnly: Bool)
    case addIncome(incomeId: String?, year: Int?, month: Int?)
    case monthlyIncomes(year: Int, month: Int)
    case monthlyExpenses(year: Int, month: Int)
    case sharedExpenses(year: Int, month: Int)
    case categoryExpenses(year: Int, month: Int, categoryName: String)
}

private enum ImportPickerKind {
    case csv
}

private struct CsvExportDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.commaSeparatedText, .plainText]

    var text: String

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

private struct KotlinViewControllerHost: UIViewControllerRepresentable {
    let makeViewController: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        SafeAreaContainerViewController(contentViewController: makeViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class SafeAreaContainerViewController: UIViewController {
    private let contentViewController: UIViewController

    init(contentViewController: UIViewController) {
        self.contentViewController = contentViewController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .clear

        addChild(contentViewController)
        contentViewController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentViewController.view)

        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            contentViewController.view.topAnchor.constraint(equalTo: guide.topAnchor),
            contentViewController.view.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
            contentViewController.view.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            contentViewController.view.bottomAnchor.constraint(equalTo: guide.bottomAnchor)
        ])

        contentViewController.didMove(toParent: self)
    }
}

struct ContentView: View {
    @State private var path = NavigationPath()
    @State private var showVoiceExpenseSheet = false
    @State private var showCsvTransferSheet = false
    @State private var showCsvExportSheet = false
    @State private var showCsvExporter = false
    @State private var activeImportPicker: ImportPickerKind?
    @StateObject private var bannerPresenter = AppGlassBannerPresenter()
    @State private var csvExportDocument = CsvExportDocument()
    @State private var csvExportFilename = "budget.csv"
    @State private var csvExportStartDate = Calendar.current.date(byAdding: .day, value: -29, to: Date()) ?? Date()
    @State private var csvExportEndDate = Date()
    @State private var csvImportController = IosCsvImportController()
    @State private var csvExportController = IosCsvExportController()

    var body: some View {
        NavigationStack(path: $path) {
            DashboardRootView(path: $path)
                .appGlassHostedScreenChrome()
                .navigationTitle(appLocalized("Dashboard"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Menu {
                            Button(appLocalized("Categories")) {
                                path.append(Route.categories)
                            }
                            Button(appLocalized("CSV Import / Export")) {
                                presentAfterMenuDismiss {
                                    showCsvTransferSheet = true
                                }
                            }
                        } label: {
                            AppGlassToolbarIcon(systemName: "line.3.horizontal")
                                .appGlassSurface(cornerRadius: 18)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showVoiceExpenseSheet = true
                        } label: {
                            Image(systemName: "waveform.badge.mic")
                                .font(.system(size: 15, weight: .semibold))
                                .frame(width: 36, height: 36)
                        }
                        .buttonStyle(.glass)
                    }
                }
                .sheet(isPresented: $showVoiceExpenseSheet) {
                    VoiceExpenseEntrySheet {
                        showVoiceExpenseSheet = false
                    }
                    .appGlassSheetPresentation()
                }
                .sheet(isPresented: $showCsvTransferSheet) {
                    CsvTransferSheet(
                        onCancel: { showCsvTransferSheet = false },
                        onExportCsv: {
                            showCsvTransferSheet = false
                            presentAfterMenuDismiss {
                                showCsvExportSheet = true
                            }
                        },
                        onImportCsv: {
                            showCsvTransferSheet = false
                            presentAfterMenuDismiss {
                                activeImportPicker = .csv
                            }
                        }
                    )
                    .appGlassSheetPresentation(detents: [.height(320)])
                }
                .sheet(isPresented: $showCsvExportSheet) {
                    CsvExportSheet(
                        startDate: $csvExportStartDate,
                        endDate: $csvExportEndDate,
                        onCancel: { showCsvExportSheet = false },
                        onExport: exportCsv
                    )
                    .appGlassSheetPresentation(detents: [.height(360)])
                }
                .fileImporter(
                    isPresented: Binding(
                        get: { activeImportPicker != nil },
                        set: { isPresented in
                            if !isPresented {
                                activeImportPicker = nil
                            }
                        }
                    ),
                    allowedContentTypes: activeImportAllowedContentTypes
                ) { result in
                    handleImportSelection(result: result)
                }
                .fileExporter(
                    isPresented: $showCsvExporter,
                    document: csvExportDocument,
                    contentType: .commaSeparatedText,
                    defaultFilename: csvExportFilename
                ) { result in
                    handleCsvExport(result: result)
                }
                .overlay(alignment: .top) {
                    AppGlassBannerOverlay(presenter: bannerPresenter)
                }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .categories:
                        CategoriesRootView()
                            .appGlassHostedScreenChrome()
                            .navigationTitle(appLocalized("Categories"))
                            .navigationBarTitleDisplayMode(.inline)
                            .navigationBarBackButtonHidden()
                            .toolbar {
                                backToolbar
                            }
                    case let .addExpense(expenseId, readOnly):
                        ExpenseEditorRootView(
                            expenseId: expenseId,
                            readOnly: readOnly
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .appGlassHostedScreenChrome()
                        .navigationTitle(addExpenseTitle(expenseId: expenseId, readOnly: readOnly))
                        .navigationBarTitleDisplayMode(.inline)
                        .navigationBarBackButtonHidden()
                        .toolbar {
                            backToolbar
                        }
                    case let .addIncome(incomeId, year, month):
                        IncomeEditorRootView(
                            incomeId: incomeId,
                            initialYear: year,
                            initialMonth: month
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .appGlassHostedScreenChrome()
                        .navigationTitle(incomeId == nil ? appLocalized("Add Income") : appLocalized("Edit Income"))
                        .navigationBarTitleDisplayMode(.inline)
                        .navigationBarBackButtonHidden()
                        .toolbar {
                            backToolbar
                        }
                    case let .monthlyIncomes(year, month):
                        MonthlyIncomesRootView(
                            year: Int(year),
                            month: Int(month),
                            path: $path
                        )
                        .navigationTitle(appMonthlyTitle(month: month, key: "Income"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .monthlyExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .monthly,
                            year: Int(year),
                            month: Int(month),
                            onAddExpense: {
                                path.append(Route.addExpense(expenseId: nil, readOnly: false))
                            }
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle(appMonthlyTitle(month: month, key: "Expenses"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .sharedExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .shared,
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle(appMonthlyTitle(month: month, key: "Shared Expenses"))
                        .navigationBarTitleDisplayMode(.inline)
                    case let .categoryExpenses(year, month, categoryName):
                        GroupedExpensesSectionsScreen(
                            kind: .category(name: categoryName),
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(monthName(month)) \(categoryName)")
                        .navigationBarTitleDisplayMode(.inline)
                    }
                }
        }
        .onDisappear {
            csvImportController.dispose()
            csvExportController.dispose()
        }
    }

    @ToolbarContentBuilder
    private var backToolbar: some ToolbarContent {
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
    }

    private func handleCsvSelection(result: Result<URL, Error>) {
        switch result {
        case let .success(url):
            let didAccessSecurityScope = url.startAccessingSecurityScopedResource()
            defer {
                if didAccessSecurityScope {
                    url.stopAccessingSecurityScopedResource()
                }
            }

            do {
                let data = try Data(contentsOf: url)
                importCsv(text: String(decoding: data, as: UTF8.self))
            } catch {
                showCsvFeedback(error.localizedDescription, style: .error)
            }
        case let .failure(error):
            showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    private func importCsv(text: String) {
        guard !text.isEmpty else {
            showCsvFeedback(appLocalized("Unable to import the CSV file"), style: .error)
            return
        }

        csvImportController.importCsv(text: text) { successMessage, errorMessage in
            Task { @MainActor in
                if let successMessage {
                    showCsvFeedback(successMessage, style: .success)
                } else if let errorMessage {
                    showCsvFeedback(errorMessage, style: .error)
                }
            }
        }
    }

    private func exportCsv(startDate: Date, endDate: Date) {
        let normalizedStartDate = Calendar.current.startOfDay(for: startDate)
        let normalizedEndDate = Calendar.current.startOfDay(for: endDate)

        guard normalizedStartDate <= normalizedEndDate else {
            showCsvFeedback(appLocalized("Start date must be on or before end date"), style: .error)
            return
        }

        csvExportController.exportCsv(
            startDateMillis: Int64(normalizedStartDate.timeIntervalSince1970 * 1000),
            endDateMillis: Int64(normalizedEndDate.timeIntervalSince1970 * 1000)
        ) { fileName, content, errorMessage in
            Task { @MainActor in
                if let fileName, let content {
                    csvExportFilename = fileName
                    csvExportDocument = CsvExportDocument(text: content)
                    showCsvExportSheet = false
                    showCsvExporter = true
                } else {
                    showCsvFeedback(errorMessage ?? appLocalized("Unable to export the CSV file"), style: .error)
                }
            }
        }
    }

    private func handleCsvExport(result: Result<URL, Error>) {
        switch result {
        case .success:
            showCsvFeedback(appLocalized("CSV file exported"), style: .success)
        case let .failure(error):
            showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    @MainActor
    private func showCsvFeedback(_ message: String, style: AppGlassBannerStyle) {
        bannerPresenter.show(message, style: style)
    }

    private var activeImportAllowedContentTypes: [UTType] {
        [.commaSeparatedText, .plainText, .text]
    }

    private func handleImportSelection(result: Result<URL, Error>) {
        activeImportPicker = nil
        handleCsvSelection(result: result)
    }

    private func presentAfterMenuDismiss(_ action: @escaping @MainActor () -> Void) {
        DispatchQueue.main.async {
            Task { @MainActor in
                action()
            }
        }
    }
}

private struct CsvTransferSheet: View {
    let onCancel: () -> Void
    let onExportCsv: () -> Void
    let onImportCsv: () -> Void

    var body: some View {
        AppActionSheet(
            title: appLocalized("CSV Import & Export"),
            description: appLocalized("Move selected data using CSV files stored on this phone."),
            note: appLocalized("Not suitable for full app restore."),
            primaryLabel: appLocalized("Export CSV…"),
            primaryAction: onExportCsv,
            secondaryLabel: appLocalized("Import CSV"),
            secondaryAction: onImportCsv,
            onCancel: onCancel
        )
    }
}

private struct CsvExportSheet: View {
    @Binding var startDate: Date
    @Binding var endDate: Date
    let onCancel: () -> Void
    let onExport: (Date, Date) -> Void

    var body: some View {
        AppActionSheet(
            primaryLabel: appLocalized("Export CSV"),
            primaryAction: { onExport(startDate, endDate) },
            secondaryLabel: appLocalized("Cancel"),
            secondaryAction: onCancel,
            showCancelButton: false,
            content: {
                VStack(spacing: 16) {
                    AppGlassSheetSection(title: appLocalized("From")) {
                        DatePicker(
                            "",
                            selection: $startDate,
                            displayedComponents: .date
                        )
                        .labelsHidden()
                        .datePickerStyle(.compact)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    AppGlassSheetSection(title: appLocalized("To")) {
                        DatePicker(
                            "",
                            selection: $endDate,
                            displayedComponents: .date
                        )
                        .labelsHidden()
                        .datePickerStyle(.compact)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        )
    }
}

private struct AppActionSheet<Content: View>: View {
    let title: String?
    let description: String?
    let note: String?
    let primaryLabel: String
    let primaryAction: () -> Void
    let secondaryLabel: String?
    let secondaryAction: (() -> Void)?
    let showCancelButton: Bool
    let onCancel: (() -> Void)?
    @ViewBuilder let content: Content

    init(
        title: String? = nil,
        description: String? = nil,
        note: String? = nil,
        primaryLabel: String,
        primaryAction: @escaping () -> Void,
        secondaryLabel: String? = nil,
        secondaryAction: (() -> Void)? = nil,
        showCancelButton: Bool = true,
        onCancel: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content = { EmptyView() }
    ) {
        self.title = title
        self.description = description
        self.note = note
        self.primaryLabel = primaryLabel
        self.primaryAction = primaryAction
        self.secondaryLabel = secondaryLabel
        self.secondaryAction = secondaryAction
        self.showCancelButton = showCancelButton
        self.onCancel = onCancel
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if title != nil || description != nil || note != nil {
                    VStack(alignment: .leading, spacing: 8) {
                        if let title {
                            Text(title)
                                .font(.headline)
                                .foregroundStyle(.primary)
                        }

                        if let description {
                            Text(description)
                                .font(.body)
                                .foregroundStyle(.secondary)
                        }

                        if let note {
                            Text(note)
                                .font(.body)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                content

                Button(primaryLabel, action: primaryAction)
                    .buttonStyle(.glassProminent)
                    .frame(maxWidth: .infinity)

                if let secondaryLabel, let secondaryAction {
                    Button(secondaryLabel, action: secondaryAction)
                        .buttonStyle(.glass)
                        .frame(maxWidth: .infinity)
                }

                if showCancelButton, let onCancel {
                    Button(appLocalized("Cancel"), action: onCancel)
                        .buttonStyle(.glass)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 20)
        }
        .appGlassSheetChrome()
    }
}

@MainActor
private final class ExpenseEditorDeletionViewModel: ObservableObject {
    @Published var pendingSeriesId: String?

    private let controller = IosEditItemDeletionController()

    func disposeController() {
        controller.dispose()
    }

    func requestDelete(
        expenseId: String,
        onClose: @escaping () -> Void
    ) {
        controller.loadExpenseMetadata(id: expenseId) { [weak self] metadata in
            guard let self, let metadata else {
                return
            }

            Task { @MainActor in
                if let seriesId = metadata.recurringSeriesId, !seriesId.isEmpty {
                    self.pendingSeriesId = seriesId
                } else {
                    self.deleteExpense(expenseId: metadata.id, onClose: onClose)
                }
            }
        }
    }

    func deleteExpense(
        expenseId: String,
        onClose: @escaping () -> Void
    ) {
        controller.deleteExpense(id: expenseId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                onClose()
            }
        }
    }

    func deleteWholeSeries(onClose: @escaping () -> Void) {
        guard let pendingSeriesId else {
            return
        }

        controller.deleteRecurringExpenseSeries(seriesId: pendingSeriesId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                self.pendingSeriesId = nil
                onClose()
            }
        }
    }
}

@MainActor
private final class IncomeEditorDeletionViewModel: ObservableObject {
    @Published var pendingSeriesId: String?

    private let controller = IosEditItemDeletionController()

    func disposeController() {
        controller.dispose()
    }

    func requestDelete(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        controller.loadIncomeMetadata(id: incomeId) { [weak self] metadata in
            guard let self, let metadata else {
                return
            }

            Task { @MainActor in
                if let seriesId = metadata.recurringSeriesId, !seriesId.isEmpty {
                    self.pendingSeriesId = seriesId
                } else {
                    self.deleteIncome(incomeId: metadata.id, onClose: onClose)
                }
            }
        }
    }

    func deleteIncome(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        controller.deleteIncome(id: incomeId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                onClose()
            }
        }
    }

    func deleteWholeSeries(onClose: @escaping () -> Void) {
        guard let pendingSeriesId else {
            return
        }

        controller.deleteRecurringIncomeSeries(seriesId: pendingSeriesId) { success in
            guard success.boolValue else {
                return
            }

            Task { @MainActor in
                self.pendingSeriesId = nil
                onClose()
            }
        }
    }
}

private struct ExpenseEditorRootView: View {
    let expenseId: String?
    let readOnly: Bool
    let onClose: () -> Void

    @StateObject private var deletionViewModel = ExpenseEditorDeletionViewModel()

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.AddExpenseViewController(
                expenseId: expenseId,
                readOnly: readOnly,
                onClose: onClose
            )
        }
        .appGlassHostedScreenChrome()
        .onDisappear {
            deletionViewModel.disposeController()
        }
        .toolbar {
            if let expenseId, !readOnly {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        deletionViewModel.requestDelete(
                            expenseId: expenseId,
                            onClose: onClose
                        )
                    } label: {
                        AppGlassToolbarIcon(systemName: "trash")
                    }
                    .buttonStyle(.glass)
                }
            }
        }
        .overlay {
            if deletionViewModel.pendingSeriesId != nil, let expenseId {
                AppGlassDialogOverlay {
                    AppGlassRecurringDeleteConfirmationDialog(
                        message: appLocalized("Choose whether to delete only this instance or the whole series."),
                        onDeleteInstance: {
                            deletionViewModel.pendingSeriesId = nil
                            deletionViewModel.deleteExpense(
                                expenseId: expenseId,
                                onClose: onClose
                            )
                        },
                        onDeleteSeries: {
                            deletionViewModel.deleteWholeSeries(onClose: onClose)
                        },
                        onCancel: {
                            deletionViewModel.pendingSeriesId = nil
                        }
                    )
                }
            }
        }
    }

}

private struct IncomeEditorRootView: View {
    let incomeId: String?
    let initialYear: Int?
    let initialMonth: Int?
    let onClose: () -> Void

    @StateObject private var deletionViewModel = IncomeEditorDeletionViewModel()

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.AddIncomeViewController(
                incomeId: incomeId,
                initialYear: initialYear.map(kotlinInt),
                initialMonth: initialMonth.map(kotlinInt),
                onClose: onClose
            )
        }
        .appGlassHostedScreenChrome()
        .onDisappear {
            deletionViewModel.disposeController()
        }
        .toolbar {
            if let incomeId {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        deletionViewModel.requestDelete(
                            incomeId: incomeId,
                            onClose: onClose
                        )
                    } label: {
                        AppGlassToolbarIcon(systemName: "trash")
                    }
                    .buttonStyle(.glass)
                }
            }
        }
        .overlay {
            if deletionViewModel.pendingSeriesId != nil, let incomeId {
                AppGlassDialogOverlay {
                    AppGlassRecurringDeleteConfirmationDialog(
                        message: appLocalized("Choose whether to delete only this instance or the whole series."),
                        onDeleteInstance: {
                            deletionViewModel.pendingSeriesId = nil
                            deletionViewModel.deleteIncome(
                                incomeId: incomeId,
                                onClose: onClose
                            )
                        },
                        onDeleteSeries: {
                            deletionViewModel.deleteWholeSeries(onClose: onClose)
                        },
                        onCancel: {
                            deletionViewModel.pendingSeriesId = nil
                        }
                    )
                }
            }
        }
    }

}

private struct DashboardRootView: View {
    @Binding var path: NavigationPath

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.DashboardContentViewController(
                onOpenCategories: {
                    path.append(Route.categories)
                },
                onOpenAddExpense: {
                    path.append(Route.addExpense(expenseId: nil, readOnly: false))
                },
                onOpenMonthlyIncomes: { year, month in
                    path.append(Route.monthlyIncomes(year: year.intValue, month: month.intValue))
                },
                onOpenMonthlyExpenses: { year, month in
                    path.append(Route.monthlyExpenses(year: year.intValue, month: month.intValue))
                },
                onOpenSharedExpenses: { year, month in
                    path.append(Route.sharedExpenses(year: year.intValue, month: month.intValue))
                },
                onOpenExpenseDetails: { expenseId, readOnly in
                    path.append(Route.addExpense(expenseId: expenseId, readOnly: readOnly.boolValue))
                },
                onOpenCategoryExpenses: { year, month, categoryName in
                    path.append(
                        Route.categoryExpenses(
                            year: year.intValue,
                            month: month.intValue,
                            categoryName: categoryName
                        )
                    )
                }
            )
        }
        .appGlassHostedScreenChrome()
    }
}

private struct MonthlyIncomesRootView: View {
    let year: Int
    let month: Int
    @Binding var path: NavigationPath

    var body: some View {
        MonthlyIncomesSectionsScreen(
            year: year,
            month: month
        ) { incomeId in
            path.append(Route.addIncome(incomeId: incomeId, year: nil, month: nil))
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    path.append(
                        Route.addIncome(
                            incomeId: nil,
                            year: year,
                            month: month
                        )
                    )
                } label: {
                    AppGlassToolbarIcon(systemName: "plus")
                }
                .buttonStyle(.glass)
            }
        }
    }
}

private struct CategoriesRootView: View {
    var body: some View {
        NativeCategoriesScreen()
    }
}

private func addExpenseTitle(expenseId: String?, readOnly: Bool) -> String {
    if readOnly {
        return appLocalized("Expense Details")
    }

    return expenseId == nil ? appLocalized("Add Expense") : appLocalized("Edit Expense")
}

private func kotlinInt(_ value: Int) -> KotlinInt {
    KotlinInt(int: Int32(value))
}
