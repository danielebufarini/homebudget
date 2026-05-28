import SwiftUI

struct NativeExpenseEditorScreen: View {
    let expenseId: String
    let readOnly: Bool
    let onClose: () -> Void

    @State private var viewModel: NativeExpenseEditorViewModel
    @State private var bannerPresenter = AppGlassBannerPresenter()
    @State private var showDatePicker = false
    @State private var showCategoryPicker = false
    @State private var showAddCategorySheet = false
    @State private var showRecurringDeleteDialog = false
    @State private var showRecurringSaveDialog = false

    init(
        expenseId: String,
        readOnly: Bool,
        onClose: @escaping () -> Void
    ) {
        self.expenseId = expenseId
        self.readOnly = readOnly
        self.onClose = onClose
        _viewModel = State(initialValue: NativeExpenseEditorViewModel(expenseId: expenseId))
    }

    private var title: String {
        readOnly ? appLocalized("Expense Details") : appLocalized("Edit Expense")
    }

    private var confirmLabel: String {
        readOnly ? appLocalized("Close") : appLocalized("Update Expense")
    }

    private var recurringExpenseInfoText: String {
        appLocalized(
            "Creates the same expense every month on this day for the next %lld years.",
            viewModel.recurringMonthlyYears
        )
    }

    var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if viewModel.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity, minHeight: 220)
                    } else if viewModel.didFailToLoad {
                        AppGlassListCard {
                            Text(appLocalized("Expense not found."))
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    } else {
                        NativeExpenseAmountCard(
                            amount: $viewModel.amount,
                            readOnly: readOnly
                        )

                        AppGlassSheetSection(title: appLocalized("Expense Details"), spacing: 14) {
                            NativeExpensePickerRow(
                                label: appLocalized("Category"),
                                value: viewModel.selectedCategory?.name ?? appLocalized("Select Category"),
                                iconKey: viewModel.selectedCategory?.iconKey ?? "category",
                                colorKey: viewModel.selectedCategoryId,
                                enabled: !readOnly,
                                action: { showCategoryPicker = true }
                            )

                            NativeExpensePickerRow(
                                label: appLocalized("Date"),
                                value: nativeExpenseEditorDateString(viewModel.selectedDate),
                                systemImageName: "calendar",
                                enabled: !readOnly,
                                action: { showDatePicker = true }
                            )

                            NativeExpenseDescriptionField(
                                descriptionText: $viewModel.description,
                                readOnly: readOnly
                            )
                        }

                        AppGlassSheetSection(title: appLocalized("Options"), spacing: 14) {
                            if viewModel.hasRecurringSeries {
                                NativeExpenseInfoCard(
                                    systemImageName: "arrow.triangle.2.circlepath",
                                    text: appLocalized("This expense belongs to a recurring monthly series.")
                                )
                            } else {
                                NativeExpenseToggleRow(
                                    label: appLocalized("Recurring Monthly"),
                                    systemImageName: "arrow.triangle.2.circlepath",
                                    isOn: $viewModel.isRecurringMonthly,
                                    enabled: !readOnly
                                )

                                if viewModel.isRecurringMonthly {
                                    NativeExpenseInfoCard(
                                        systemImageName: "arrow.triangle.2.circlepath",
                                        text: recurringExpenseInfoText
                                    )
                                }
                            }

                            NativeExpenseToggleRow(
                                label: appLocalized("Shared Expense"),
                                systemImageName: "person.2.fill",
                                isOn: $viewModel.isShared,
                                enabled: !readOnly
                            )
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 24)
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                Color.clear.frame(height: ExpenseEditorChromeLayout.reservedTopInset)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Color.clear.frame(height: ExpenseEditorChromeLayout.reservedBottomInset)
            }

            VStack {
                ExpenseEditorGlassHeader(
                    title: title,
                    showsDeleteAction: !readOnly && viewModel.canEdit,
                    onBack: onClose,
                    onDelete: {
                        if viewModel.hasRecurringSeries {
                            showRecurringDeleteDialog = true
                        } else {
                            deleteExpense(deleteWholeSeries: false)
                        }
                    }
                )
                .padding(.horizontal, ExpenseEditorChromeLayout.horizontalPadding)
                .padding(.top, ExpenseEditorChromeLayout.topPadding)

                Spacer()

                ExpenseEditorGlassFooter(
                    onCancel: onClose,
                    onConfirm: {
                        if readOnly {
                            onClose()
                        } else if viewModel.hasRecurringSeries {
                            showRecurringSaveDialog = true
                        } else {
                            saveExpense(updateWholeSeries: false)
                        }
                    },
                    confirmLabel: confirmLabel,
                    showsSecondaryAction: !readOnly
                )
                .disabled(viewModel.isSaving || viewModel.isLoading || viewModel.didFailToLoad)
            }
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
                            deleteExpense(deleteWholeSeries: false)
                        },
                        onDeleteSeries: {
                            showRecurringDeleteDialog = false
                            deleteExpense(deleteWholeSeries: true)
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
                            saveExpense(updateWholeSeries: false)
                        },
                        onUpdateSeries: {
                            showRecurringSaveDialog = false
                            saveExpense(updateWholeSeries: true)
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
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
        }
    }

    private func saveExpense(updateWholeSeries: Bool) {
        viewModel.save(updateWholeSeries: updateWholeSeries) { errorKey in
            if let errorKey {
                bannerPresenter.show(appLocalized(errorKey), style: .error)
            } else {
                onClose()
            }
        }
    }

    private func deleteExpense(deleteWholeSeries: Bool) {
        viewModel.delete(deleteWholeSeries: deleteWholeSeries) { errorKey in
            if let errorKey {
                bannerPresenter.show(appLocalized(errorKey), style: .error)
            } else {
                onClose()
            }
        }
    }
}
