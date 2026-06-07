@preconcurrency import ComposeApp
import SwiftUI
import Observation


struct NativeTransactionEditorScreen: View {
    let onClose: () -> Void

    @State private var viewModel: NativeTransactionEditorViewModel
    @State private var bannerPresenter = AppGlassBannerPresenter()
    @State private var showDatePicker = false
    @State private var showCategoryPicker = false
    @State private var showAddCategorySheet = false
    @State private var showRecurringDeleteDialog = false
    @State private var showRecurringSaveDialog = false

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

    init(
        incomeId: String,
        onClose: @escaping () -> Void
    ) {
        self.onClose = onClose
        _viewModel = State(initialValue: NativeTransactionEditorViewModel(incomeId: incomeId))
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
                Color.clear.frame(height: reservedTopInset)
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
        .overlay {
            if showRecurringDeleteDialog {
                AppGlassDialogOverlay {
                    AppGlassRecurringDeleteConfirmationDialog(
                        message: appLocalized("Choose whether to delete only this instance or the whole series."),
                        onDeleteInstance: {
                            showRecurringDeleteDialog = false
                            deleteIncome(deleteWholeSeries: false)
                        },
                        onDeleteSeries: {
                            showRecurringDeleteDialog = false
                            deleteIncome(deleteWholeSeries: true)
                        },
                        onCancel: {
                            showRecurringDeleteDialog = false
                        }
                    )
                }
            } else if showRecurringSaveDialog {
                AppGlassDialogOverlay {
                    NativeRecurringSaveConfirmationDialog(
                        onUpdateInstance: {
                            showRecurringSaveDialog = false
                            saveIncome(updateWholeSeries: false)
                        },
                        onUpdateSeries: {
                            showRecurringSaveDialog = false
                            saveIncome(updateWholeSeries: true)
                        },
                        onCancel: {
                            showRecurringSaveDialog = false
                        }
                    )
                }
            }
        }
        .onAppear {
            viewModel.start()
        }
        .onDisappear {
            viewModel.stop()
            SpesifyWidgetSummaryRefresher.shared.refresh()
        }
        .dismissesKeyboardOnTap()
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 18) {
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 220)
            } else if viewModel.didFailToLoad {
                AppGlassListCard {
                    Text(appLocalized("Income not found."))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
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

                    if viewModel.hasRecurringSeries {
                        NativeExpenseInfoCard(
                            systemImageName: "arrow.triangle.2.circlepath",
                            text: appLocalized("This income belongs to a recurring monthly series.")
                        )
                    } else if shouldShowRecurringMonthlyToggle {
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
    }

    private var chrome: some View {
        VStack(spacing: 0) {
            ExpenseEditorGlassHeader(
                title: viewModel.title,
                showsDeleteAction: viewModel.isEditingIncome && viewModel.canEdit,
                onBack: onClose,
                onDelete: {
                    if viewModel.hasRecurringSeries {
                        showRecurringDeleteDialog = true
                    } else {
                        deleteIncome(deleteWholeSeries: false)
                    }
                }
            )
            .padding(.horizontal, ExpenseEditorChromeLayout.horizontalPadding)
            .padding(.top, ExpenseEditorChromeLayout.topPadding)

            if viewModel.editableKindSelection {
                MonthlyTransactionKindGlassControl(selection: $viewModel.selectedKind)
                    .padding(.horizontal, 22)
                    .padding(.top, NativeTransactionEditorChromeLayout.selectorTopSpacing)
            }

            Spacer()

            ExpenseEditorGlassFooter(
                onCancel: onClose,
                onConfirm: saveTransaction,
                confirmLabel: viewModel.isEditingIncome ? appLocalized("Update") : appLocalized("Save"),
                showsSecondaryAction: true
            )
            .disabled(viewModel.isSaving || viewModel.isLoading || viewModel.didFailToLoad || !viewModel.hasValidAmount)
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }

    private var reservedTopInset: CGFloat {
        viewModel.editableKindSelection
            ? NativeTransactionEditorChromeLayout.reservedTopInset
            : ExpenseEditorChromeLayout.reservedTopInset
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
        if viewModel.isEditingIncome, viewModel.hasRecurringSeries {
            showRecurringSaveDialog = true
            return
        }
        if viewModel.isEditingIncome {
            saveIncome(updateWholeSeries: false)
            return
        }
        viewModel.save { errorKey in
            if let errorKey {
                bannerPresenter.show(appLocalized(errorKey), style: .error)
            } else {
                onClose()
            }
        }
    }

    private func saveIncome(updateWholeSeries: Bool) {
        appDismissKeyboard()
        viewModel.saveIncome(updateWholeSeries: updateWholeSeries) { errorKey in
            if let errorKey {
                bannerPresenter.show(appLocalized(errorKey), style: .error)
            } else {
                onClose()
            }
        }
    }

    private func deleteIncome(deleteWholeSeries: Bool) {
        viewModel.deleteIncome(deleteWholeSeries: deleteWholeSeries) { errorKey in
            if let errorKey {
                bannerPresenter.show(appLocalized(errorKey), style: .error)
            } else {
                onClose()
            }
        }
    }
}
