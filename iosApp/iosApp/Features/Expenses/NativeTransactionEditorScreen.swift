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

    init(initialKind: AddTransactionKind, initialYear: Int?, initialMonth: Int?) {
        selectedKind = initialKind
        selectedDate = Self.initialDate(year: initialYear, month: initialMonth)
    }

    deinit {
        editorController.dispose()
        categoriesController.dispose()
    }

    func start() {
        guard !hasStarted else {
            return
        }

        hasStarted = true
        reloadCategories()
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

    func save(onComplete: @escaping (String?) -> Void) {
        isSaving = true
        let dateMillis = Int64(selectedDate.timeIntervalSince1970 * 1000.0)

        switch selectedKind {
        case .expense:
            editorController.saveExpense(
                amountInput: amount,
                dateMillis: dateMillis,
                categoryId: selectedCategoryId,
                description: description,
                isShared: isShared,
                isRecurringMonthly: isRecurringMonthly,
                installmentCount: Int32(installmentCount)
            ) { [weak self] result in
                self?.completeSave(result: result, onComplete: onComplete)
            }
        case .income:
            editorController.saveIncome(
                amountInput: amount,
                dateMillis: dateMillis,
                categoryId: selectedCategoryId.isEmpty ? nil : selectedCategoryId,
                description: description,
                isRecurringMonthly: isRecurringMonthly
            ) { [weak self] result in
                self?.completeSave(result: result, onComplete: onComplete)
            }
        }
    }

    func insertCategory(name: String, iconKey: String, onComplete: @escaping (String?) -> Void) {
        categoriesController.insertCategoryAndReturnIdForCategoryType(
            name: name,
            iconKey: iconKey,
            categoryType: selectedKind.categoryType
        ) { [weak self] categoryId in
            guard let self else {
                return
            }

            Task { @MainActor in
                if let categoryId {
                    self.selectedCategoryId = categoryId
                }
                onComplete(categoryId)
            }
        }
    }

    private func reloadCategories() {
        categoriesController.stop()
        categoriesController.startForCategoryType(categoryType: selectedKind.categoryType) { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.categories = snapshot.categories.map {
                    NativeExpenseCategory(id: $0.id, name: $0.name, iconKey: $0.iconKey)
                }
            }
        }
    }

    private func completeSave(
        result: IosNativeTransactionEditorResult,
        onComplete: @escaping (String?) -> Void
    ) {
        Task { @MainActor in
            isSaving = false
            onComplete(result.isSuccess ? nil : result.errorKey)
        }
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
    @State private var showInstallmentsPicker = false

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
        .confirmationDialog(appLocalized("Installments"), isPresented: $showInstallmentsPicker) {
            ForEach(1 ... 12, id: \.self) { count in
                Button(installmentLabel(count)) {
                    viewModel.installmentCount = count
                    if count > 1 {
                        viewModel.setRecurringMonthly(false)
                    }
                }
            }
        }
        .overlay(alignment: .top) {
            AppGlassBannerOverlay(presenter: bannerPresenter)
        }
        .onAppear {
            viewModel.start()
        }
        .onDisappear {
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
                    NativeExpensePickerRow(
                        label: appLocalized("Installments"),
                        value: installmentLabel(viewModel.installmentCount),
                        systemImageName: "calendar.badge.clock",
                        enabled: !viewModel.isRecurringMonthly,
                        action: { showInstallmentsPicker = true }
                    )
                }

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

    private var recurringMonthlyBinding: Binding<Bool> {
        Binding(
            get: { viewModel.isRecurringMonthly },
            set: { viewModel.setRecurringMonthly($0) }
        )
    }

    private func installmentLabel(_ count: Int) -> String {
        count == 1 ? appLocalized("Single Payment") : appLocalized("%lld Installments", count)
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
