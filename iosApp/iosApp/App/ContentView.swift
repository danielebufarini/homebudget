@preconcurrency import ComposeApp
import SwiftUI
import UniformTypeIdentifiers

private enum CsvImportReadError: LocalizedError {
    case fileTooLarge

    var errorDescription: String? {
        switch self {
        case .fileTooLarge:
            return appLocalized("Unable to import the CSV file")
        }
    }
}

struct ContentView: View {
    nonisolated private static let maxCsvImportBytes = 5 * 1024 * 1024

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
            DashboardRootView(
                path: $path,
                onStartVoiceExpense: startVoiceExpense,
                onOpenCsvTransfer: {
                    presentAfterMenuDismiss {
                        showCsvTransferSheet = true
                    }
                }
            )
                .appGlassHostedScreenChrome()
                .toolbar(.hidden, for: .navigationBar)
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
                        NativeTransactionEditorScreen(
                            initialKind: initialKind,
                            initialYear: year,
                            initialMonth: month
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .appGlassHostedScreenChrome()
                        .toolbar(.hidden, for: .navigationBar)
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
                            month: Int(month),
                            headerTitle: "\(day) \(monthName(month))",
                            headerAmountDescriptor: appLocalized("Highest Day")
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .appGlassHostedScreenChrome()
                        .toolbar(.hidden, for: .navigationBar)
                        .overlay(alignment: .top) {
                            leadingBackChrome
                                .padding(.horizontal, 16)
                                .padding(.top, MonthNavigationHeaderLayout.topPadding)
                        }
                    case let .monthlyIncomes(year, month):
                        MonthlyTransactionsRootView(
                            year: Int(year),
                            month: Int(month),
                            initialKind: .income,
                            path: $path,
                            onStartVoiceExpense: startVoiceExpense
                        )
                    case let .monthlyExpenses(year, month):
                        MonthlyTransactionsRootView(
                            year: Int(year),
                            month: Int(month),
                            initialKind: .expense,
                            path: $path,
                            onStartVoiceExpense: startVoiceExpense
                        )
                    case let .sharedExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .shared,
                            year: Int(year),
                            month: Int(month),
                            headerAmountDescriptor: appLocalized("Shared Expenses")
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .appGlassHostedScreenChrome()
                        .toolbar(.hidden, for: .navigationBar)
                        .overlay(alignment: .top) {
                            leadingBackChrome
                                .padding(.horizontal, 16)
                                .padding(.top, MonthNavigationHeaderLayout.topPadding)
                        }
                    case let .categoryExpenses(year, month, categoryName):
                        GroupedExpensesSectionsScreen(
                            kind: .category(name: categoryName),
                            year: Int(year),
                            month: Int(month),
                            headerTitle: MonthCursor(year: Int(year), month: Int(month)).label,
                            headerAmountDescriptor: categoryName
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .appGlassHostedScreenChrome()
                        .toolbar(.hidden, for: .navigationBar)
                        .overlay(alignment: .top) {
                            leadingBackChrome
                                .padding(.horizontal, 16)
                                .padding(.top, MonthNavigationHeaderLayout.topPadding)
                        }
                    case let .transactionSearch(year, month, query):
                        TransactionSearchRootView(
                            year: year,
                            month: month,
                            query: query,
                            path: $path
                        )
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
        .dismissesKeyboardOnTap()
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

    private func startVoiceExpense() {
        voiceExpenseAutoStartRequest += 1
        showVoiceExpenseSheet = true
    }

    private var leadingBackChrome: some View {
        HStack {
            Button {
                if !path.isEmpty {
                    path.removeLast()
                }
            } label: {
                AppGlassBackButton()
            }
            .buttonStyle(.plain)

            Spacer(minLength: 0)
        }
        .frame(height: MonthNavigationHeaderLayout.minHeight)
        .frame(maxWidth: .infinity)
    }

    private func handleIncomingURL(_ url: URL) {
        guard url.scheme == "homebudget" else {
            return
        }

        switch url.host {
        case "add-expense":
            path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
        case "voice-expense":
            startVoiceExpense()
        default:
            break
        }
    }

    private func handleCsvSelection(result: Result<URL, Error>) {
        switch result {
        case let .success(url):
            Task {
                do {
                    let text = try await readCsvText(from: url)
                    await MainActor.run {
                        importCsv(text: text)
                    }
                } catch {
                    await MainActor.run {
                        showCsvFeedback(error.localizedDescription, style: .error)
                    }
                }
            }
        case let .failure(error):
            showCsvFeedback(error.localizedDescription, style: .error)
        }
    }

    private func readCsvText(from url: URL) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            let didAccessSecurityScope = url.startAccessingSecurityScopedResource()
            defer {
                if didAccessSecurityScope {
                    url.stopAccessingSecurityScopedResource()
                }
            }

            if let fileSize = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize,
               fileSize > Self.maxCsvImportBytes {
                throw CsvImportReadError.fileTooLarge
            }

            let data = try Data(contentsOf: url, options: [.mappedIfSafe])
            guard data.count <= Self.maxCsvImportBytes else {
                throw CsvImportReadError.fileTooLarge
            }

            return String(decoding: data, as: UTF8.self)
        }.value
    }

    @MainActor
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
