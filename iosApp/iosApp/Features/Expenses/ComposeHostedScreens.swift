@preconcurrency import ComposeApp
import SwiftUI
import UIKit

struct DashboardRootView: View {
    @Binding var path: NavigationPath
    let onStartVoiceExpense: () -> Void
    let onOpenCsvTransfer: () -> Void
    let onNavigationDrawerVisibilityChange: (Bool) -> Void

    var body: some View {
        KotlinViewControllerHost(constrainToSafeArea: false) {
            MainViewControllerKt.DashboardContentViewController(
                onOpenCategories: {
                    path.append(Route.categories)
                },
                onOpenAddExpense: {
                    path.append(Route.addTransaction(initialKind: .expense, year: nil, month: nil))
                },
                onOpenVoiceExpense: onStartVoiceExpense,
                onOpenCsvTransfer: onOpenCsvTransfer,
                onNavigationDrawerVisibilityChange: { isOpen in
                    onNavigationDrawerVisibilityChange(isOpen.boolValue)
                },
                onOpenDayExpenses: { year, month, day in
                    path.append(
                        Route.dayExpenses(
                            year: year.intValue,
                            month: month.intValue,
                            day: day.intValue
                        )
                    )
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
                onOpenIncomeDetails: { incomeId in
                    path.append(Route.addIncome(incomeId: incomeId, year: nil, month: nil))
                },
                onOpenRecurringExpenses: { year, month in
                    path.append(Route.recurringExpenses(year: year.intValue, month: month.intValue))
                },
                onBalanceChartExpansionChange: { isExpanded in
                    AppOrientationController.setDashboardBalanceExpanded(isExpanded.boolValue)
                },
                onOpenCategoryTransactions: { year, month, _, categoryName in
                    path.append(
                        Route.categoryExpenses(
                            year: year.intValue,
                            month: month.intValue,
                            categoryName: categoryName
                        )
                    )
                },
                onOpenTransactionSearch: { year, month, query in
                    path.append(
                        Route.transactionSearch(
                            year: year.intValue,
                            month: month.intValue,
                            query: query
                        )
                    )
                }
            )
        }
        .appGlassHostedScreenChrome()
        .ignoresSafeArea(edges: .vertical)
    }
}

struct CategoriesRootView: View {
    let onClose: () -> Void

    var body: some View {
        ZStack {
            AppGlassBackdrop()

            KotlinViewControllerHost(constrainToSafeArea: false) {
                MainViewControllerKt.CategoriesViewController(onClose: onClose)
            }
            .ignoresSafeArea()
        }
        .overlay(alignment: .top) {
            CategoriesGlassHeader(
                title: appLocalized("Categories"),
                onBack: onClose,
                onAdd: {
                    IosCategoriesManagementBridgeKt.performIosCategoriesManagementAdd()
                }
            )
            .padding(.horizontal, CategoriesChromeLayout.horizontalPadding)
            .padding(.top, CategoriesChromeLayout.topPadding)
        }
        .ignoresSafeArea(edges: .bottom)
        .toolbarBackground(.hidden, for: .navigationBar)
        .scrollEdgeEffectStyle(.soft, for: .top)
    }
}

private enum CategoriesChromeLayout {
    static let horizontalPadding: CGFloat = 16
    static let topPadding: CGFloat = 12
}

private struct CategoriesGlassHeader: View {
    let title: String
    let onBack: () -> Void
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onBack) {
                AppGlassToolbarIcon(systemName: "chevron.left")
            }
            .buttonStyle(.glass)

            AppGlassToolbarTitle(text: title)
                .frame(maxWidth: .infinity)

            Button(action: onAdd) {
                AppGlassToolbarIcon(systemName: "plus")
            }
            .buttonStyle(.glass)
        }
        .frame(maxWidth: .infinity)
    }
}

struct MonthlyIncomesRootView: View {
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
    }
}

struct TransactionSearchRootView: View {
    let year: Int
    let month: Int
    let query: String
    @Binding var path: NavigationPath

    var body: some View {
        TransactionSearchSectionsRootView(
            query: query,
            onClose: {
                if !path.isEmpty {
                    path.removeLast()
                }
            },
            onOpenExpense: { expenseId in
                path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
            },
            onOpenIncome: { incomeId in
                path.append(Route.addIncome(incomeId: incomeId, year: nil, month: nil))
            }
        )
    }
}

struct RecurringExpensesRootView: View {
    let year: Int
    let month: Int
    @Binding var path: NavigationPath

    var body: some View {
        RecurringExpensesSectionsScreen(
            year: year,
            month: month,
            onClose: {
                if !path.isEmpty {
                    path.removeLast()
                }
            },
            onOpenExpense: { expenseId in
                path.append(Route.addExpense(expenseId: expenseId, readOnly: false))
            }
        )
    }
}
