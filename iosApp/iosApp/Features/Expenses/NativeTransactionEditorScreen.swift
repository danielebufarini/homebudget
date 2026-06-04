@preconcurrency import ComposeApp
import SwiftUI
import Observation

@MainActor
@Observable
final class NativeTransactionEditorViewModel {
    var selectedKind: AddTransactionKind {
        didSet {
            guard oldValue != selectedKind, hasStarted else {
                return
            }

            selectedCategoryId = ""
            isShared = false
            installmentCount = 1
            reloadCategories()
        }
    }

    var amount = ""
    var selectedDate: Date
    var selectedCategoryId = ""
    var description = ""
    var isShared = false
    var isRecurringMonthly = false
    var installmentCount = 1
    var categories: [NativeExpenseCategory] = []
    var isSaving = false

    private let editorController = IosNativeTransactionEditorController()
    private let categoriesController = IosCategoriesController()
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private var categoriesTask: Task<Void, Never>?

    init(initialKind: AddTransactionKind, initialYear: Int?, initialMonth: Int?) {
        selectedKind = initialKind
        selectedDate = Self.initialDate(year: initialYear, month: initialMonth)
    }

    deinit {
        categoriesTask?.cancel()
    }

    func start() {
        if hasStarted {
            if categoriesTask == nil {
                reloadCategories()
            }
            return
        }

        hasStarted = true
        reloadCategories()
    }

    func stop() {
        categoriesTask?.cancel()
        categoriesTask = nil
    }

    var selectedCategory: NativeExpenseCategory? {
        categories.first { $0.id == selectedCategoryId }
    }

    var title: String {
        selectedKind == .income ? appLocalized("Add Income") : appLocalized("Add Expense")
    }

    var categoryValue: String {
        selectedCategory?.name ?? appLocalized("Select Category")
    }

    var isIncome: Bool {
        selectedKind == .income
    }

    var recurringMonthlyYears: Int {
        Int(ExpenseInstallmentsKt.RECURRING_MONTHLY_OCCURRENCES / 12)
    }

    func setRecurringMonthly(_ enabled: Bool) {
        isRecurringMonthly = enabled
        if enabled {
            installmentCount = 1
        }
    }

    func setInstallmentCount(_ count: Int) {
        let normalizedCount = min(max(count, 1), 30)
        installmentCount = normalizedCount
        if normalizedCount > 1 {
            setRecurringMonthly(false)
        }
    }

    func save(onComplete: @escaping (String?) -> Void) {
        isSaving = true
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)

        switch selectedKind {
        case .expense:
            Task { [weak self, editorController] in
                guard let self else {
                    return
                }

                do {
                    let result = try await editorController.saveExpense(
                        amountInput: amount,
                        dateMillis: dateMillis,
                        categoryId: selectedCategoryId,
                        description: description,
                        isShared: isShared,
                        isRecurringMonthly: isRecurringMonthly,
                        installmentCount: Int32(installmentCount)
                    )
                    completeSave(result: result, onComplete: onComplete)
                } catch {
                    isSaving = false
                    onComplete(error.localizedDescription)
                }
            }
        case .income:
            Task { [weak self, editorController] in
                guard let self else {
                    return
                }

                do {
                    let result = try await editorController.saveIncome(
                        amountInput: amount,
                        dateMillis: dateMillis,
                        categoryId: selectedCategoryId.isEmpty ? nil : selectedCategoryId,
                        description: description,
                        isRecurringMonthly: isRecurringMonthly
                    )
                    completeSave(result: result, onComplete: onComplete)
                } catch {
                    isSaving = false
                    onComplete(error.localizedDescription)
                }
            }
        }
    }

    func insertCategory(name: String, iconKey: String, onComplete: @escaping (String?) -> Void) {
        let categoryType = selectedKind.categoryType
        Task { [weak self, categoriesController] in
            let categoryId: String?
            do {
                categoryId = try await categoriesController.insertCategoryAndReturnIdForCategoryType(
                    name: name,
                    iconKey: iconKey,
                    categoryType: categoryType
                )
            } catch {
                categoryId = nil
            }
            guard let self else {
                return
            }

            if let categoryId {
                selectedCategoryId = categoryId
            }
            onComplete(categoryId)
        }
    }

    private func reloadCategories() {
        categoriesTask?.cancel()
        let categoryType = selectedKind.categoryType
        categoriesTask = Task { [weak self, categoriesController] in
            for await snapshot in categoriesController.snapshotsForCategoryType(categoryType: categoryType) {
                guard let self else {
                    return
                }
                categories = snapshot.categories.map {
                    NativeExpenseCategory(id: $0.id, name: $0.name, iconKey: $0.iconKey)
                }
            }
        }
    }

    private func completeSave(
        result: IosNativeTransactionEditorResult,
        onComplete: @escaping (String?) -> Void
    ) {
        isSaving = false
        onComplete(result.isSuccess ? nil : result.errorKey)
    }

    private static func initialDate(year: Int?, month: Int?) -> Date {
        let calendar = Calendar(identifier: .gregorian)
        let now = Date()

        guard let year, let month else {
            return now
        }

        var components = calendar.dateComponents([.day], from: now)
        components.year = year
        components.month = month

        let firstDay = calendar.date(from: DateComponents(year: year, month: month, day: 1)) ?? now
        let maximumDay = calendar.range(of: .day, in: .month, for: firstDay)?.count ?? 28
        components.day = min(components.day ?? 1, maximumDay)

        return calendar.date(from: components) ?? now
    }
}

struct NativeTransactionEditorScreen: View {
    let onClose: () -> Void

    @State private var viewModel: NativeTransactionEditorViewModel
    @State private var bannerPresenter = AppGlassBannerPresenter()
    @State private var showDatePicker = false
    @State private var showCategoryPicker = false
    @State private var showAddCategorySheet = false

    init(
        initialKind: AddTransactionKind,
        initialYear: Int?,
        initialMonth: Int?,
        onClose: @escaping () -> Void
    ) {
        self.onClose = onClose
        _viewModel = State(
            initialValue: NativeTransactionEditorViewModel(
                initialKind: initialKind,
                initialYear: initialYear,
                initialMonth: initialMonth
            )
        )
    }

    var body: some View {
        ZStack {
            ScrollView {
                content
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 24)
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                Color.clear.frame(height: NativeTransactionEditorChromeLayout.reservedTopInset)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Color.clear.frame(height: ExpenseEditorChromeLayout.reservedBottomInset)
            }

            chrome
        }
        .background(AppGlassBackdrop().ignoresSafeArea())
        .sheet(isPresented: $showDatePicker) {
            LiquidGlassDatePickerSheet(
                initialDate: viewModel.selectedDate,
                onCancel: { showDatePicker = false },
                onConfirm: { selectedDate in
                    viewModel.selectedDate = selectedDate
                    showDatePicker = false
                }
            )
            .appGlassSheetPresentation(detents: [.height(610)])
        }
        .sheet(isPresented: $showCategoryPicker) {
            NativeExpenseCategoryPickerSheet(
                categories: viewModel.categories,
                selectedCategoryId: viewModel.selectedCategoryId,
                onAddCategory: {
                    showCategoryPicker = false
                    DispatchQueue.main.async {
                        showAddCategorySheet = true
                    }
                },
                onSelectCategory: { categoryId in
                    viewModel.selectedCategoryId = categoryId
                    showCategoryPicker = false
                }
            )
            .appGlassSheetPresentation(detents: [.large])
        }
        .sheet(isPresented: $showAddCategorySheet) {
            NativeAddCategorySheet(
                onCancel: { showAddCategorySheet = false },
                onConfirm: { name, iconKey in
                    viewModel.insertCategory(name: name, iconKey: iconKey) { categoryId in
                        if categoryId == nil {
                            bannerPresenter.show(appLocalized("Unable to save category"), style: .error)
                        }
                        showAddCategorySheet = false
                    }
                }
            )
            .appGlassSheetPresentation(detents: [.height(630)])
        }
        .overlay(alignment: .top) {
            AppGlassBannerOverlay(presenter: bannerPresenter)
        }
        .onAppear {
            viewModel.start()
        }
        .onDisappear {
            viewModel.stop()
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
        }
        .dismissesKeyboardOnTap()
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 18) {
            NativeTransactionAmountCard(
                kind: viewModel.selectedKind,
                amount: $viewModel.amount
            )

            AppGlassSheetSection(title: detailsTitle, spacing: 14) {
                NativeExpensePickerRow(
                    label: appLocalized("Category"),
                    value: viewModel.categoryValue,
                    iconKey: viewModel.selectedCategory?.iconKey ?? "category",
                    colorKey: viewModel.selectedCategoryId,
                    enabled: true,
                    action: { showCategoryPicker = true }
                )

                NativeExpensePickerRow(
                    label: appLocalized("Date"),
                    value: nativeExpenseEditorDateString(viewModel.selectedDate),
                    systemImageName: "calendar",
                    enabled: true,
                    action: { showDatePicker = true }
                )

                NativeExpenseDescriptionField(
                    descriptionText: $viewModel.description,
                    readOnly: false
                )
            }

            AppGlassSheetSection(title: appLocalized("Options"), spacing: 14) {
                if viewModel.selectedKind == .expense {
                    NativeInstallmentRulerPicker(
                        label: appLocalized("Installments"),
                        systemImageName: "calendar.badge.clock",
                        value: installmentCountBinding,
                        enabled: !viewModel.isRecurringMonthly,
                        range: 1 ... 30,
                        singlePaymentLabel: appLocalized("Single Payment"),
                        installmentsLabel: appLocalized("Installments")
                    )
                }

                if shouldShowRecurringMonthlyToggle {
                    VStack(spacing: 0) {
                        NativeExpenseToggleRow(
                            label: appLocalized("Recurring Monthly"),
                            systemImageName: "arrow.triangle.2.circlepath",
                            isOn: recurringMonthlyBinding,
                            enabled: true
                        )

                        if viewModel.isRecurringMonthly {
                            NativeExpenseInfoCard(
                                systemImageName: "arrow.triangle.2.circlepath",
                                text: appLocalized(
                                    "Creates the same expense every month on this day for the next %lld years.",
                                    viewModel.recurringMonthlyYears
                                )
                            )
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    .transition(.asymmetric(
                        insertion: .opacity.combined(with: .move(edge: .top)),
                        removal: .opacity.combined(with: .move(edge: .top))
                    ))
                }

                if viewModel.selectedKind == .expense {
                    NativeExpenseToggleRow(
                        label: appLocalized("Shared Expense"),
                        systemImageName: "person.2.fill",
                        isOn: $viewModel.isShared,
                        enabled: true
                    )
                }
            }
        }
    }

    private var chrome: some View {
        VStack(spacing: 0) {
            ExpenseEditorGlassHeader(
                title: viewModel.title,
                showsDeleteAction: false,
                onBack: onClose,
                onDelete: {}
            )
            .padding(.horizontal, ExpenseEditorChromeLayout.horizontalPadding)
            .padding(.top, ExpenseEditorChromeLayout.topPadding)

            MonthlyTransactionKindGlassControl(selection: $viewModel.selectedKind)
                .padding(.horizontal, 22)
                .padding(.top, NativeTransactionEditorChromeLayout.selectorTopSpacing)

            Spacer()

            ExpenseEditorGlassFooter(
                onCancel: onClose,
                onConfirm: saveTransaction,
                confirmLabel: appLocalized("Save"),
                showsSecondaryAction: true
            )
            .disabled(viewModel.isSaving)
        }
    }

    private var detailsTitle: String {
        viewModel.selectedKind == .income ? appLocalized("Income Details") : appLocalized("Expense Details")
    }

    private var shouldShowRecurringMonthlyToggle: Bool {
        viewModel.selectedKind != .expense || viewModel.installmentCount == 1
    }

    private var recurringToggleAnimation: Animation {
        .spring(response: 0.28, dampingFraction: 0.88)
    }

    private var recurringMonthlyBinding: Binding<Bool> {
        Binding(
            get: { viewModel.isRecurringMonthly },
            set: { isOn in
                withAnimation(recurringToggleAnimation) {
                    viewModel.setRecurringMonthly(isOn)
                }
            }
        )
    }

    private var installmentCountBinding: Binding<Int> {
        Binding(
            get: { viewModel.installmentCount },
            set: { count in
                withAnimation(recurringToggleAnimation) {
                    viewModel.setInstallmentCount(count)
                }
            }
        )
    }


    private func saveTransaction() {
        appDismissKeyboard()
        viewModel.save { errorKey in
            if let errorKey {
                bannerPresenter.show(appLocalized(errorKey), style: .error)
            } else {
                onClose()
            }
        }
    }
}

private struct NativeTransactionAmountCard: View {
    let kind: AddTransactionKind
    @Binding var amount: String

    var body: some View {
        AppGlassListCard(verticalPadding: 18) {
            VStack(alignment: .leading, spacing: 10) {
                Text(appLocalized("Amount"))
                    .font(.headline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)

                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    Text("\(kind.amountPrefix) \(appCurrencySymbol())")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(kind.amountColor)

                    TextField("0.00", text: $amount)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 46, weight: .semibold, design: .rounded))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                Divider()
                    .overlay(Color.white.opacity(0.10))
            }
        }
    }
}

private enum NativeTransactionEditorChromeLayout {
    static let selectorTopSpacing = MonthlyTransactionsHeaderLayout.selectorTopSpacing
    static var reservedTopInset: CGFloat {
        ExpenseEditorChromeLayout.reservedTopInset +
            selectorTopSpacing +
            MonthlyTransactionsHeaderLayout.selectorHeight
    }
}

private extension AddTransactionKind {
    var categoryType: String {
        switch self {
        case .expense:
            return "expense"
        case .income:
            return "income"
        }
    }

    var amountPrefix: String {
        switch self {
        case .expense:
            return "-"
        case .income:
            return "+"
        }
    }

    var amountColor: Color {
        switch self {
        case .expense:
            return Color.red.opacity(0.7)
        case .income:
            return Color.green.opacity(0.75)
        }
    }
}
