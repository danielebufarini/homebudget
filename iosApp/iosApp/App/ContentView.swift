@preconcurrency import ComposeApp
import SwiftUI
import UniformTypeIdentifiers


struct ContentView: View {
    nonisolated static let maxCsvImportBytes = 5 * 1024 * 1024

    @State var path = NavigationPath()
    @State var showVoiceExpenseSheet = false
    @State var voiceExpenseAutoStartRequest = 0
    @State var showPaymentScreenshotImport = false
    @State var showCsvTransferSheet = false
    @State var showCsvExportSheet = false
    @State var showCsvExporter = false
    @State var activeImportPicker: ImportPickerKind?
    @State var bannerPresenter = AppGlassBannerPresenter()
    @State var csvExportDocument = CsvExportDocument()
    @State var csvExportFilename = "budget.csv"
    @State var csvExportStartDate = Calendar.current.date(byAdding: .day, value: -29, to: Date()) ?? Date()
    @State var csvExportEndDate = Date()
    @State var csvImportController = IosCsvImportController()
    @State var csvExportController = IosCsvExportController()
    @State var documentPaymentScreenshotImporter = PaymentScreenshotImportViewModel()
    @State var isProcessingDocumentPaymentScreenshot = false
    @State private var dashboardNavigationDrawerOpen = false
    @Binding var pendingOpenURL: PendingOpenURL?

    init(pendingOpenURL: Binding<PendingOpenURL?> = .constant(nil)) {
        _pendingOpenURL = pendingOpenURL
    }

    var body: some View {
        NavigationStack(path: $path) {
            DashboardRootView(
                path: $path,
                onStartVoiceExpense: startVoiceExpense,
                onOpenCsvTransfer: {
                    presentAfterMenuDismiss {
                        showCsvTransferSheet = true
                    }
                },
                onNavigationDrawerVisibilityChange: { isOpen in
                    dashboardNavigationDrawerOpen = isOpen
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
                .sheet(isPresented: $showPaymentScreenshotImport) {
                    PaymentScreenshotImportSheet(
                        onCandidates: handlePaymentScreenshotCandidates,
                        onClose: {
                            showPaymentScreenshotImport = false
                        }
                    )
                    .appGlassSheetPresentation(detents: [.height(420)])
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
                    case let .addPrefilledExpense(prefill, remaining):
                        NativeTransactionEditorScreen(prefilledExpense: prefill) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                            if let next = remaining.first {
                                let nextRemaining = Array(remaining.dropFirst())
                                DispatchQueue.main.async {
                                    path.append(Route.addPrefilledExpense(next, remaining: nextRemaining))
                                }
                            }
                        }
                        .id(prefill.id)
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
                        if let incomeId, !incomeId.isEmpty {
                            NativeTransactionEditorScreen(incomeId: incomeId) {
                                if !path.isEmpty {
                                    path.removeLast()
                                }
                            }
                            .appGlassHostedScreenChrome()
                            .toolbar(.hidden, for: .navigationBar)
                        } else {
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
                            .navigationTitle(appLocalized("Add Income"))
                            .navigationBarTitleDisplayMode(.inline)
                            .navigationBarBackButtonHidden()
                            .toolbar {
                                backToolbar
                            }
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
                .task {
                    SpesifyWidgetSummaryRefresher.shared.refresh()
                    consumePendingOpenURLIfNeeded()
                }
                .onChange(of: pendingOpenURL) { _, _ in
                    consumePendingOpenURLIfNeeded()
                }
        }
        .overlay {
            if isProcessingDocumentPaymentScreenshot {
                documentPaymentScreenshotProgressOverlay
            }
        }
        .overlay(alignment: .bottom) {
            dashboardFloatingInputDock
                .ignoresSafeArea(edges: .bottom)
                .offset(y: TransactionInputDockLayout.dashboardVerticalOffset)
        }
        .appGlassHostedScreenChrome()
        .dismissesKeyboardOnTap()
        .restoresInteractivePopGesture()
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

    func startVoiceExpense() {
        voiceExpenseAutoStartRequest += 1
        showVoiceExpenseSheet = true
    }

    func handlePaymentScreenshotCandidates(_ prefills: [NativeExpenseEditorPrefill]) {
        showPaymentScreenshotImport = false
        guard let first = prefills.first else {
            return
        }

        let remaining = Array(prefills.dropFirst())
        DispatchQueue.main.async {
            path.append(Route.addPrefilledExpense(first, remaining: remaining))
        }
    }

    @ViewBuilder
    private var dashboardFloatingInputDock: some View {
        if path.isEmpty && !dashboardNavigationDrawerOpen {
            TransactionInputDock(
                onManualAdd: {
                    path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
                },
                onVoiceInput: startVoiceExpense,
                onImportScreenshot: {
                    showPaymentScreenshotImport = true
                },
                bottomPadding: TransactionInputDockLayout.dashboardBottomPadding,
                manualAddFrameWidth: 58,
                manualAddActionHeight: 52,
                manualAddIconSize: 22
            )
        }
    }

    private var documentPaymentScreenshotProgressOverlay: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(appLocalized("Reading screenshot..."))
                .font(.subheadline.weight(.medium))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .appGlassSurface(cornerRadius: 24)
        .padding(.horizontal, 24)
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

}
