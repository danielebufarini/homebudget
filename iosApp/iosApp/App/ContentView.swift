@preconcurrency import ComposeApp
import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var path = NavigationPath()
    @State private var showVoiceExpenseSheet = false
    @State private var voiceExpenseAutoStartRequest = 0
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
                    topQuickActionsToolbar(initialKind: .expense, year: nil, month: nil)
                }
                .sheet(isPresented: $showVoiceExpenseSheet) {
                    VoiceExpenseEntrySheet(autoStartRequest: voiceExpenseAutoStartRequest) {
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
                    .appGlassSheetPresentation(detents: [.large])
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
                        CategoriesRootView {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .appGlassHostedScreenChrome()
                        .toolbar(.hidden, for: .navigationBar)
                    case let .addTransaction(initialKind, year, month):
                        TransactionEditorRootView(
                            initialKind: initialKind,
                            initialYear: year,
                            initialMonth: month
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .appGlassHostedScreenChrome()
                        .navigationTitle(initialKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense"))
                        .navigationBarTitleDisplayMode(.inline)
                        .navigationBarBackButtonHidden()
                        .toolbar {
                            backToolbar
                        }
                    case let .addExpense(expenseId, readOnly):
                        if let expenseId, expenseId.isEmpty == false {
                            NativeExpenseEditorScreen(
                                expenseId: expenseId,
                                readOnly: readOnly
                            ) {
                                if !path.isEmpty {
                                    path.removeLast()
                                }
                            }
                            .appGlassHostedScreenChrome()
                            .toolbar(.hidden, for: .navigationBar)
                        } else {
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
                    case let .dayExpenses(year, month, day):
                        GroupedExpensesSectionsScreen(
                            kind: .day(day: day),
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(day) \(monthName(month))")
                        .navigationBarTitleDisplayMode(.inline)
                    case let .monthlyIncomes(year, month):
                        MonthlyIncomesRootView(
                            year: Int(year),
                            month: Int(month),
                            path: $path
                        )
                        .appGlassHostedScreenChrome()
                        .navigationTitle(appLocalized("Income"))
                        .navigationBarTitleDisplayMode(.inline)
                        .navigationBarBackButtonHidden()
                        .toolbar {
                            backToolbar
                            topQuickActionsToolbar(
                                initialKind: .income,
                                year: Int(year),
                                month: Int(month)
                            )
                        }
                    case let .monthlyExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .monthly,
                            year: Int(year),
                            month: Int(month),
                            onAddExpense: {
                                path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
                            }
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .appGlassHostedScreenChrome()
                        .navigationTitle(appLocalized("Expenses"))
                        .navigationBarTitleDisplayMode(.inline)
                        .navigationBarBackButtonHidden()
                        .toolbar {
                            backToolbar
                            topQuickActionsToolbar(initialKind: .expense, year: nil, month: nil)
                        }
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
                .onOpenURL { url in
                    handleIncomingURL(url)
                }
                .task {
                    HomeBudgetWidgetSummaryRefresher.shared.refresh()
                }
        }
        .appGlassHostedScreenChrome()
        .restoresInteractivePopGesture()
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

    @ToolbarContentBuilder
    private func topQuickActionsToolbar(
        initialKind: AddTransactionKind,
        year: Int?,
        month: Int?
    ) -> some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            topQuickActionsBar(initialKind: initialKind, year: year, month: month)
        }
    }

    @ToolbarContentBuilder
    private func leadingBackAndQuickActionsToolbar(
        initialKind: AddTransactionKind,
        year: Int?,
        month: Int?
    ) -> some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            HStack(spacing: 8) {
                Button {
                    if !path.isEmpty {
                        path.removeLast()
                    }
                } label: {
                    AppGlassBackButton()
                }
                .buttonStyle(.glass)

                topQuickActionsBar(initialKind: initialKind, year: year, month: month)
            }
        }
    }

    private func topQuickActionsBar(
        initialKind: AddTransactionKind,
        year: Int?,
        month: Int?
    ) -> some View {
        AppGlassBottomQuickActionsBar(
            addAccessibilityLabel: initialKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense"),
            voiceAccessibilityLabel: appLocalized("Voice Expense"),
            onAdd: {
                path.append(
                    Route.addTransaction(
                        initialKind: initialKind,
                        year: year,
                        month: month
                    )
                )
            },
            onVoice: {
                voiceExpenseAutoStartRequest += 1
                showVoiceExpenseSheet = true
            }
        )
    }

    private func handleIncomingURL(_ url: URL) {
        guard url.scheme == "homebudget" else {
            return
        }

        switch url.host {
        case "add-expense":
            path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
        case "voice-expense":
            voiceExpenseAutoStartRequest += 1
            showVoiceExpenseSheet = true
        default:
            break
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
                    HomeBudgetWidgetSummaryRefresher.shared.refresh()
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
