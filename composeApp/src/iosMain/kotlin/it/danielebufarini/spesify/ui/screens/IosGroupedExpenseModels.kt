package it.danielebufarini.spesify.ui.screens

class IosGroupedExpenseRow(
    val id: String,
    val title: String,
    val subtitleText: String,
    val amountText: String,
    val categoryColorKey: String?,
    val categoryIconKey: String?,
    val recurringSeriesId: String?
)

class IosGroupedExpenseSection(
    val id: String,
    val title: String,
    val categoryColorKey: String?,
    val categoryIconKey: String?,
    val totalAmountText: String,
    val rows: List<IosGroupedExpenseRow>
)

class IosGroupedExpensesSnapshot(
    val totalAmountText: String,
    val emptyStateText: String,
    val sections: List<IosGroupedExpenseSection>
)

class IosRecurringExpensesSnapshot(
    val totalAmountText: String,
    val totalRecurringText: String,
    val recurringExpensesText: String,
    val emptyStateTitle: String,
    val emptyStateDescription: String,
    val rows: List<IosGroupedExpenseRow>
)

class IosIncomeRow(
    val id: String,
    val title: String,
    val subtitleText: String,
    val amountText: String,
    val categoryColorKey: String?,
    val categoryIconKey: String?,
    val recurringSeriesId: String?
)

class IosIncomeSection(
    val id: String,
    val title: String,
    val categoryColorKey: String?,
    val categoryIconKey: String?,
    val totalAmountText: String,
    val rows: List<IosIncomeRow>
)

class IosMonthlyIncomesSnapshot(
    val totalAmountText: String,
    val emptyStateText: String,
    val sections: List<IosIncomeSection>
)

internal data class GroupedExpensesCacheKey(
    val year: Int,
    val month: Int,
    val screenType: String,
    val categoryName: String?,
    val dayOfMonth: Int? = null
)

internal data class PreparedIosExpense(
    val id: String,
    val amount: Long,
    val amountText: String,
    val categoryId: String?,
    val categoryName: String,
    val categoryIconKey: String?,
    val description: String?,
    val recurringSeriesId: String?,
    val dateText: String,
    val dateGroupTitleText: String,
    val dateMillis: Long,
    val year: Int,
    val month: Int,
    val isShared: Boolean
)

internal data class PreparedIosIncome(
    val id: String,
    val amount: Long,
    val amountText: String,
    val categoryId: String?,
    val categoryName: String,
    val categoryIconKey: String?,
    val description: String?,
    val recurringSeriesId: String?,
    val dateText: String,
    val dateGroupTitleText: String,
    val dateMillis: Long,
    val year: Int,
    val month: Int
)

internal data class IosGroupedLocalization(
    val currencySymbol: String,
    val expense: String,
    val income: String,
    val sharedExpense: String,
    val category: String,
    val noExpensesForDay: String,
    val noExpensesForMonth: String,
    val noSharedExpensesForMonth: String,
    val noIncomeForMonth: String,
    val noSearchResults: String,
    val unknownCategory: String,
    val noExpensesForCategoryThisMonthTemplate: String,
    val shortMonthNames: List<String>,
    val resolveCategoryName: (String, String) -> String
)

internal data class IosRecurringLocalization(
    val currencySymbol: String,
    val monthly: String,
    val next: String,
    val noRecurringExpenses: String,
    val noRecurringExpensesDescription: String,
    val perMonth: String,
    val recurringExpenses: String,
    val shared: String,
    val totalRecurring: String,
    val unknownCategory: String,
    val shortMonthNames: List<String>,
    val resolveCategoryName: (String, String) -> String
)

internal data class IosGroupedSnapshotsCache(
    val byCategory: IosGroupedExpensesSnapshot,
    val byDate: IosGroupedExpensesSnapshot
) {
    fun snapshotFor(groupingMode: String): IosGroupedExpensesSnapshot = when (groupingMode) {
        "date" -> byDate
        else -> byCategory
    }
}
