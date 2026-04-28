import ComposeApp
import AVFoundation
import FoundationModels
import Speech
import SwiftUI
import UIKit

private enum Route: Hashable {
    case categories
    case addExpense(expenseId: String?, readOnly: Bool)
    case addIncome(incomeId: String?, year: Int?, month: Int?)
    case monthlyIncomes(year: Int, month: Int)
    case monthlyExpenses(year: Int, month: Int)
    case sharedExpenses(year: Int, month: Int)
    case categoryExpenses(year: Int, month: Int, categoryName: String)
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
    @State private var systemLanguageModel = SystemLanguageModel.default

    var body: some View {
        NavigationStack(path: $path) {
            DashboardRootView(path: $path)
                .navigationTitle("Dashboard")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Categories") {
                            path.append(Route.categories)
                        }
                    }
                    if foundationModelsAvailable {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button {
                                showVoiceExpenseSheet = true
                            } label: {
                                Image(systemName: "waveform.badge.mic")
                            }
                        }
                    }
                }
                .sheet(isPresented: $showVoiceExpenseSheet) {
                    VoiceExpenseEntrySheet {
                        showVoiceExpenseSheet = false
                    }
                }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .categories:
                        CategoriesRootView()
                            .navigationTitle("Categories")
                            .navigationBarTitleDisplayMode(.inline)
                    case let .addExpense(expenseId, readOnly):
                        ExpenseEditorRootView(
                            expenseId: expenseId,
                            readOnly: readOnly
                        ) {
                            if !path.isEmpty {
                                path.removeLast()
                            }
                        }
                        .navigationTitle(addExpenseTitle(expenseId: expenseId, readOnly: readOnly))
                        .navigationBarTitleDisplayMode(.inline)
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
                        .navigationTitle(incomeId == nil ? "Add Income" : "Edit Income")
                        .navigationBarTitleDisplayMode(.inline)
                    case let .monthlyIncomes(year, month):
                        MonthlyIncomesRootView(
                            year: Int(year),
                            month: Int(month),
                            path: $path
                        )
                        .navigationTitle("\(monthName(month)) Income")
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
                        .navigationTitle("\(monthName(month)) Expenses")
                        .navigationBarTitleDisplayMode(.inline)
                    case let .sharedExpenses(year, month):
                        GroupedExpensesSectionsScreen(
                            kind: .shared,
                            year: Int(year),
                            month: Int(month)
                        ) { expenseId in
                            path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
                        }
                        .navigationTitle("\(monthName(month)) Shared Expenses")
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
    }

    private var foundationModelsAvailable: Bool {
        if case .available = systemLanguageModel.availability {
            return true
        }
        return false
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
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete",
            isPresented: recurringDialogBinding,
            titleVisibility: .visible
        ) {
            if let expenseId {
                Button("This instance only", role: .destructive) {
                    deletionViewModel.pendingSeriesId = nil
                    deletionViewModel.deleteExpense(
                        expenseId: expenseId,
                        onClose: onClose
                    )
                }
            }
            Button("Whole series", role: .destructive) {
                deletionViewModel.deleteWholeSeries(onClose: onClose)
            }
        }
    }

    private var recurringDialogBinding: Binding<Bool> {
        Binding(
            get: {
                deletionViewModel.pendingSeriesId != nil
            },
            set: { isPresented in
                if !isPresented {
                    deletionViewModel.pendingSeriesId = nil
                }
            }
        )
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
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete",
            isPresented: recurringDialogBinding,
            titleVisibility: .visible
        ) {
            if let incomeId {
                Button("This instance only", role: .destructive) {
                    deletionViewModel.pendingSeriesId = nil
                    deletionViewModel.deleteIncome(
                        incomeId: incomeId,
                        onClose: onClose
                    )
                }
            }
            Button("Whole series", role: .destructive) {
                deletionViewModel.deleteWholeSeries(onClose: onClose)
            }
        }
    }

    private var recurringDialogBinding: Binding<Bool> {
        Binding(
            get: {
                deletionViewModel.pendingSeriesId != nil
            },
            set: { isPresented in
                if !isPresented {
                    deletionViewModel.pendingSeriesId = nil
                }
            }
        )
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
                    Image(systemName: "plus")
                }
            }
        }
    }
}

private struct CategoriesRootView: View {
    @State private var addCategoryRequestKey = 0

    var body: some View {
        KotlinViewControllerHost {
            MainViewControllerKt.CategoriesContentViewController(addCategoryRequestKey: Int32(addCategoryRequestKey))
        }
        .id(addCategoryRequestKey)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    addCategoryRequestKey += 1
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
    }
}

private func addExpenseTitle(expenseId: String?, readOnly: Bool) -> String {
    if readOnly {
        return "Expense Details"
    }

    return expenseId == nil ? "Add Expense" : "Edit Expense"
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

    let index = Int(month) - 1
    guard names.indices.contains(index) else {
        return ""
    }

    return names[index]
}

private func kotlinInt(_ value: Int) -> KotlinInt {
    KotlinInt(int: Int32(value))
}

private struct VoiceExpenseEntrySheet: View {
    let onClose: () -> Void

    @StateObject private var viewModel = VoiceExpenseEntryViewModel()

    var body: some View {
        NavigationStack {
            Form {
                if let statusMessage = viewModel.statusMessage {
                    Section {
                        Text(statusMessage)
                            .font(.footnote)
                    }
                }

                Section {
                    Button {
                        viewModel.toggleRecording()
                    } label: {
                        HStack {
                            Image(systemName: viewModel.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                            Text(viewModel.isRecording ? "Stop Recording" : "Start Recording")
                        }
                    }
                    .disabled(viewModel.isBusy || !viewModel.canStartCapture)

                    if viewModel.isBusy {
                        ProgressView(viewModel.busyLabel)
                    }
                }

                Section("Transcript") {
                    Text(viewModel.transcript.isEmpty ? "Speak an expense command." : viewModel.transcript)
                        .foregroundStyle(viewModel.transcript.isEmpty ? .secondary : .primary)
                }

                if let draft = viewModel.draft {
                    Section("Parsed Action") {
                        Text(draft.summary)
                        LabeledContent("Action", value: draft.actionLabel)
                        if let amountLabel = draft.amountLabel {
                            LabeledContent("Amount", value: amountLabel)
                        }
                        LabeledContent("Category", value: draft.categoryName)
                        if let dateLabel = draft.dateLabel {
                            LabeledContent("Date", value: dateLabel)
                        }
                        if let description = draft.description, !description.isEmpty {
                            LabeledContent("Description", value: description)
                        }
                        LabeledContent("Shared", value: draft.isShared ? "Yes" : "No")
                    }

                    Section {
                        Button(viewModel.commitButtonTitle) {
                            viewModel.commit {
                                onClose()
                            }
                        }
                        .disabled(!viewModel.canCommit)
                    }
                }
            }
            .navigationTitle("Voice Expense")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") {
                        onClose()
                    }
                }
            }
        }
        .onDisappear {
            viewModel.dispose()
        }
    }
}

@MainActor
private final class VoiceExpenseEntryViewModel: ObservableObject {
    @Published var transcript = ""
    @Published var statusMessage: String?
    @Published var draft: VoiceExpenseDraft?
    @Published var isRecording = false
    @Published var isBusy = false
    @Published var busyLabel = ""

    private let controller = IosVoiceExpenseController()
    private let recorder = VoiceExpenseRecorder()
    private var categoriesById: [String: VoiceExpenseCategory] = [:]
    private var expensesById: [String: VoiceExpenseCandidate] = [:]
    private var snapshotLoaded = false

    init() {
        loadSnapshot()
    }

    var canStartCapture: Bool {
        snapshotLoaded
    }

    var canCommit: Bool {
        draft != nil && !isBusy
    }

    var commitButtonTitle: String {
        draft?.actionButtonTitle ?? "Save"
    }

    func toggleRecording() {
        if isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }

    func commit(onSuccess: @escaping () -> Void) {
        guard let draft else {
            return
        }

        isBusy = true
        busyLabel = draft.intent == .create ? "Saving expense..." : "Updating expense..."
        statusMessage = nil

        Task {
            let result = await persist(draft: draft)
            await MainActor.run {
                isBusy = false
                busyLabel = ""
                if result.success {
                    onSuccess()
                } else {
                    statusMessage = result.message ?? "Unable to save expense."
                }
            }
        }
    }

    func dispose() {
        recorder.stop()
        controller.dispose()
    }

    private func loadSnapshot() {
        statusMessage = "Loading budget data..."
        controller.loadSnapshot { [weak self] snapshot in
            Task { @MainActor in
                guard let self else {
                    return
                }

                guard let snapshot else {
                    self.statusMessage = "Unable to load expenses and categories."
                    return
                }

                self.categoriesById = Dictionary(
                    uniqueKeysWithValues: snapshot.categories.map { category in
                        let item = VoiceExpenseCategory(
                            id: category.id,
                            name: category.name
                        )
                        return (item.id, item)
                    }
                )
                self.expensesById = Dictionary(
                    uniqueKeysWithValues: snapshot.recentExpenses.map { expense in
                        let item = VoiceExpenseCandidate(
                            id: expense.id,
                            amountInput: expense.amountInput,
                            categoryId: expense.categoryId,
                            categoryName: expense.categoryName,
                            description: expense.description,
                            date: Date(timeIntervalSince1970: TimeInterval(expense.date) / 1000.0),
                            isShared: expense.isShared
                        )
                        return (item.id, item)
                    }
                )
                self.snapshotLoaded = true
                self.statusMessage = nil
            }
        }
    }

    private func startRecording() {
        statusMessage = nil
        draft = nil
        transcript = ""
        isBusy = true
        busyLabel = "Starting microphone..."

        Task { @MainActor in
            do {
                try await recorder.start { [weak self] updatedTranscript in
                    self?.transcript = updatedTranscript
                }
                isRecording = true
                isBusy = false
                busyLabel = ""
            } catch {
                isBusy = false
                busyLabel = ""
                statusMessage = error.localizedDescription
            }
        }
    }

    private func stopRecording() {
        let capturedTranscript = recorder.stop().trimmingCharacters(in: .whitespacesAndNewlines)
        isRecording = false

        guard !capturedTranscript.isEmpty else {
            statusMessage = "No speech was captured."
            return
        }

        transcript = capturedTranscript
        interpretTranscript(capturedTranscript)
    }

    private func interpretTranscript(_ text: String) {
        guard snapshotLoaded else {
            statusMessage = "Budget data is still loading."
            return
        }

        let model = SystemLanguageModel.default
        guard model.isAvailable else {
            statusMessage = availabilityMessage(for: model.availability)
            return
        }

        isBusy = true
        busyLabel = "Understanding expense..."
        statusMessage = nil
        draft = nil

        Task {
            do {
                let interpretation = try await parseExpenseIntent(
                    transcript: text,
                    categories: Array(categoriesById.values).sorted { $0.name < $1.name },
                    expenses: Array(expensesById.values)
                        .sorted { $0.date > $1.date }
                        .prefix(80)
                        .map { $0 }
                )

                let nextDraft = buildDraft(from: interpretation)
                await MainActor.run {
                    draft = nextDraft
                    statusMessage = nextDraft == nil ? interpretation.summary : nil
                    isBusy = false
                    busyLabel = ""
                }
            } catch {
                await MainActor.run {
                    isBusy = false
                    busyLabel = ""
                    statusMessage = error.localizedDescription
                }
            }
        }
    }

    private func buildDraft(from interpretation: VoiceExpenseInterpretation) -> VoiceExpenseDraft? {
        switch interpretation.intent {
        case .create:
            guard
                let amountInput = normalizeAmountInput(interpretation.amount),
                let categoryId = interpretation.categoryId,
                let category = categoriesById[categoryId]
            else {
                return nil
            }

            let resolvedDate = parseISODate(interpretation.date) ?? Calendar.current.startOfDay(for: Date())

            return VoiceExpenseDraft(
                intent: .create,
                expenseId: nil,
                amountInput: amountInput,
                categoryId: categoryId,
                categoryName: category.name,
                description: interpretation.description.trimmedNilIfBlank,
                date: resolvedDate,
                isShared: interpretation.isShared ?? false,
                summary: interpretation.summary
            )

        case .update:
            guard
                let expenseId = interpretation.expenseId,
                let existingExpense = expensesById[expenseId]
            else {
                return nil
            }

            let resolvedCategoryId = interpretation.categoryId ?? existingExpense.categoryId
            guard let category = categoriesById[resolvedCategoryId] else {
                return nil
            }

            return VoiceExpenseDraft(
                intent: .update,
                expenseId: expenseId,
                amountInput: normalizeAmountInput(interpretation.amount) ?? existingExpense.amountInput,
                categoryId: resolvedCategoryId,
                categoryName: category.name,
                description: interpretation.description ?? existingExpense.description,
                date: parseISODate(interpretation.date) ?? existingExpense.date,
                isShared: interpretation.isShared ?? existingExpense.isShared,
                summary: interpretation.summary
            )

        case .needClarification:
            return nil
        }
    }

    private func persist(draft: VoiceExpenseDraft) async -> (success: Bool, message: String?) {
        await withCheckedContinuation { continuation in
            let completion: (KotlinBoolean?, String?) -> Void = { success, message in
                continuation.resume(returning: (success?.boolValue == true, message))
            }

            switch draft.intent {
            case .create:
                controller.createExpense(
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared,
                    onComplete: completion
                )
            case .update:
                guard let expenseId = draft.expenseId else {
                    continuation.resume(returning: (false, "Expense not found."))
                    return
                }

                controller.updateExpense(
                    expenseId: expenseId,
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared,
                    onComplete: completion
                )
            case .needClarification:
                continuation.resume(returning: (false, "The spoken command still needs clarification."))
            }
        }
    }
}

private struct VoiceExpenseCategory: Identifiable {
    let id: String
    let name: String
}

private struct VoiceExpenseCandidate: Identifiable {
    let id: String
    let amountInput: String
    let categoryId: String
    let categoryName: String
    let description: String?
    let date: Date
    let isShared: Bool
}

private struct VoiceExpenseDraft {
    let intent: VoiceExpenseInterpretation.Intent
    let expenseId: String?
    let amountInput: String
    let categoryId: String
    let categoryName: String
    let description: String?
    let date: Date
    let isShared: Bool
    let summary: String

    var actionLabel: String {
        intent == .create ? "Create new expense" : "Update existing expense"
    }

    var actionButtonTitle: String {
        intent == .create ? "Save Expense" : "Update Expense"
    }

    var amountLabel: String? {
        "€ \(amountInput)"
    }

    var dateLabel: String? {
        voiceExpenseDisplayDateFormatter.string(from: date)
    }
}

@Generable(description: "A parsed expense action extracted from a spoken user command.")
private struct VoiceExpenseInterpretation {
    @Generable
    enum Intent {
        case create
        case update
        case needClarification
    }

    @Guide(description: "Whether the user wants to create a new expense, update an existing expense, or the command needs clarification.")
    var intent: Intent

    @Guide(description: "Existing expense id to update. Use null for new expenses or when clarification is needed.")
    var expenseId: String?

    @Guide(description: "Expense amount in euros with two decimal digits, for example 12.50. Use null only when clarification is needed or the amount is unchanged in an update.")
    var amount: String?

    @Guide(description: "Category id from the provided categories. Use null only when clarification is needed or the category is unchanged in an update.")
    var categoryId: String?

    @Guide(description: "Short expense description. Use null when omitted.")
    var description: String?

    @Guide(description: "Date in yyyy-MM-dd format. For new expenses with no spoken date, use today's date.")
    var date: String?

    @Guide(description: "Whether the expense is shared. Use null when not mentioned for updates.")
    var isShared: Bool?

    @Guide(description: "Short user-facing summary of the parsed action or of the clarification needed.")
    var summary: String
}

private final class VoiceExpenseRecorder: @unchecked Sendable {
    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let recognizer = SFSpeechRecognizer(locale: .current)
    private var currentTranscript = ""

    func start(onTranscript: @escaping @MainActor @Sendable (String) -> Void) async throws {
        try await requestPermissions()

        guard let recognizer else {
            throw VoiceExpenseError.transcriptionUnavailable
        }

        guard recognizer.isAvailable else {
            throw VoiceExpenseError.transcriptionUnavailable
        }

        stop()

        currentTranscript = ""
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition {
            request.requiresOnDeviceRecognition = true
        }
        recognitionRequest = request

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.inputFormat(forBus: 0)
        let outputFormat = inputNode.outputFormat(forBus: 0)
        let format = validRecordingFormat(primary: inputFormat, secondary: outputFormat)
        guard let format else {
            throw VoiceExpenseError.microphoneUnavailable
        }
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        try audioEngine.start()

        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else {
                return
            }

            if let result {
                let transcript = result.bestTranscription.formattedString.trimmingCharacters(in: .whitespacesAndNewlines)
                Task { @MainActor in
                    self.currentTranscript = transcript
                    onTranscript(transcript)
                }
            }

            if error != nil {
                Task { @MainActor in
                    self.stop()
                }
            }
        }
    }

    @discardableResult
    func stop() -> String {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionRequest = nil
        recognitionTask = nil

        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            // Keep cleanup best-effort.
        }

        return currentTranscript
    }

    private nonisolated func requestPermissions() async throws {
        let speechAuthorized = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        guard speechAuthorized else {
            throw VoiceExpenseError.speechPermissionDenied
        }

        let micAuthorized = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard micAuthorized else {
            throw VoiceExpenseError.microphonePermissionDenied
        }
    }

    private func validRecordingFormat(
        primary: AVAudioFormat,
        secondary: AVAudioFormat
    ) -> AVAudioFormat? {
        if primary.sampleRate > 0, primary.channelCount > 0 {
            return primary
        }
        if secondary.sampleRate > 0, secondary.channelCount > 0 {
            return secondary
        }
        return nil
    }
}

private enum VoiceExpenseError: LocalizedError {
    case speechPermissionDenied
    case microphonePermissionDenied
    case microphoneUnavailable
    case transcriptionUnavailable

    var errorDescription: String? {
        switch self {
        case .speechPermissionDenied:
            return "Speech recognition permission is required."
        case .microphonePermissionDenied:
            return "Microphone permission is required."
        case .microphoneUnavailable:
            return "No usable microphone input is available."
        case .transcriptionUnavailable:
            return "Speech transcription is unavailable on this device."
        }
    }
}

private func parseExpenseIntent(
    transcript: String,
    categories: [VoiceExpenseCategory],
    expenses: [VoiceExpenseCandidate]
) async throws -> VoiceExpenseInterpretation {
    let today = voiceExpenseISODateFormatter.string(from: Date())
    let categoriesText = categories
        .map { "- id=\($0.id), name=\($0.name)" }
        .joined(separator: "\n")
    let expensesText = expenses
        .map { expense in
            let dateText = voiceExpenseISODateFormatter.string(from: expense.date)
            let description = expense.description.ifEmptyNil
            let shared = expense.isShared ? "yes" : "no"
            return "- id=\(expense.id), amount=\(expense.amountInput), date=\(dateText), categoryId=\(expense.categoryId), categoryName=\(expense.categoryName), shared=\(shared), description=\(description)"
        }
        .joined(separator: "\n")

    let session = LanguageModelSession(
        model: SystemLanguageModel.default,
        instructions: """
        You convert a spoken budget command into one structured expense action.
        Prefer create when the user is adding a new expense.
        Prefer update only when one listed expense is a clear match.
        If the command is ambiguous, return needClarification.
        Use only category ids from the provided category list.
        For update, keep omitted fields as null so the app can preserve the existing value.
        For create, if no date is spoken, use today's date.
        Return short summaries.
        """
    )

    let response = try await session.respond(
        generating: VoiceExpenseInterpretation.self
    ) {
        """
        Today's date: \(today)

        Transcript:
        \(transcript)

        Categories:
        \(categoriesText)

        Existing expenses available for updates:
        \(expensesText)
        """
    }

    return response.content
}

private func normalizeAmountInput(_ amount: String?) -> String? {
    guard let amount else {
        return nil
    }

    let trimmed = amount.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
        return nil
    }

    let normalized = trimmed.replacingOccurrences(of: ",", with: ".")
    let parts = normalized.split(separator: ".", omittingEmptySubsequences: false)
    guard parts.count <= 2 else {
        return nil
    }

    let whole = parts.first.map(String.init) ?? "0"
    guard whole.allSatisfy(\.isNumber) else {
        return nil
    }

    let decimals = parts.count == 2 ? String(parts[1]) : ""
    guard decimals.allSatisfy(\.isNumber), decimals.count <= 2 else {
        return nil
    }

    return "\(whole).\(decimals.padding(toLength: 2, withPad: "0", startingAt: 0))"
}

private func parseISODate(_ value: String?) -> Date? {
    guard let value else {
        return nil
    }
    return voiceExpenseISODateFormatter.date(from: value)
}

private func availabilityMessage(for availability: SystemLanguageModel.Availability) -> String {
    switch availability {
    case .available:
        return ""
    case let .unavailable(reason):
        switch reason {
        case .deviceNotEligible:
            return "Foundation Models are unavailable on this device."
        case .appleIntelligenceNotEnabled:
            return "Apple Intelligence must be enabled to use voice expense parsing."
        case .modelNotReady:
            return "The on-device model is still preparing. Try again in a moment."
        @unknown default:
            return "Foundation Models are currently unavailable."
        }
    }
}

private let voiceExpenseISODateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone.current
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter
}()

private let voiceExpenseDisplayDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    return formatter
}()

private extension Optional where Wrapped == String {
    var ifEmptyNil: String {
        switch self?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case .some(let value) where !value.isEmpty:
            return value
        default:
            return "-"
        }
    }

    var trimmedNilIfBlank: String? {
        guard let value = self?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        return value
    }
}
