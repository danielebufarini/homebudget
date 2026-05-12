@preconcurrency import ComposeApp
import SwiftUI

enum AddTransactionKind: Hashable {
    case expense
    case income
}

enum Route: Hashable {
    case categories
    case addTransaction(initialKind: AddTransactionKind, year: Int?, month: Int?)
    case addExpense(expenseId: String?, readOnly: Bool)
    case addIncome(incomeId: String?, year: Int?, month: Int?)
    case dayExpenses(year: Int, month: Int, day: Int)
    case monthlyIncomes(year: Int, month: Int)
    case monthlyExpenses(year: Int, month: Int)
    case sharedExpenses(year: Int, month: Int)
    case categoryExpenses(year: Int, month: Int, categoryName: String)
}

func addExpenseTitle(expenseId: String?, readOnly: Bool) -> String {
    if readOnly {
        return appLocalized("Expense Details")
    }

    return expenseId == nil ? appLocalized("Add Expense") : appLocalized("Edit Expense")
}

func kotlinInt(_ value: Int) -> KotlinInt {
    KotlinInt(int: Int32(value))
}
