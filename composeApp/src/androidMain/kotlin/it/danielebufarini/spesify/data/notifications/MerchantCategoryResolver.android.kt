package it.danielebufarini.spesify.data.notifications

import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.Category

interface MerchantCategoryResolver {
    suspend fun resolveCategoryId(merchant: String?): String?
}

internal class DefaultMerchantCategoryResolver(
    private val categoryRepository: CategoryManagementRepository
) : MerchantCategoryResolver {
    override suspend fun resolveCategoryId(merchant: String?): String? {
        categoryRepository.seedStarterCategoriesIfEmpty()
        val categories = categoryRepository.getAllCategoriesSnapshot()
            .filter { category -> category.categoryType == CATEGORY_TYPE_EXPENSE && category.isArchived != 1L }

        val merchantCategory = merchant
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { merchantName -> resolveMerchantKeyword(merchantName, categories) }
        if (merchantCategory != null) return merchantCategory.id

        return categories.firstOrNull { category -> category.id == FALLBACK_OTHER_CATEGORY_ID }?.id
            ?: categories.firstOrNull { category -> category.name.equals("Other", ignoreCase = true) }?.id
            ?: categories.firstOrNull { category -> category.name.equals("Altro", ignoreCase = true) }?.id
    }

    private fun resolveMerchantKeyword(
        merchant: String,
        categories: List<Category>
    ): Category? {
        val normalizedMerchant = merchant.normalizeForMatch()
        val keywordCategoryIds = when {
            listOf("esselunga", "carrefour", "conad", "coop", "lidl", "aldi", "supermercato")
                .any(normalizedMerchant::contains) -> listOf("starter_expense_food")
            listOf("ristorante", "restaurant", "pizzeria", "trattoria", "bar", "caffe", "cafe")
                .any(normalizedMerchant::contains) -> listOf("starter_expense_restaurant", "starter_expense_food")
            listOf("benzina", "fuel", "eni", "q8", "tamoil", "ip ", "esso")
                .any(normalizedMerchant::contains) -> listOf("starter_expense_car")
            listOf("trenitalia", "italo", "ryanair", "easyjet", "hotel", "booking")
                .any(normalizedMerchant::contains) -> listOf("starter_expense_travel")
            listOf("farmacia", "pharmacy", "ospedale", "hospital")
                .any(normalizedMerchant::contains) -> listOf("starter_expense_healthcare")
            else -> emptyList()
        }
        return keywordCategoryIds.firstNotNullOfOrNull { categoryId ->
            categories.firstOrNull { category -> category.id == categoryId }
        }
    }

    private fun String.normalizeForMatch(): String = lowercase()
        .replace('à', 'a')
        .replace('è', 'e')
        .replace('é', 'e')
        .replace('ì', 'i')
        .replace('ò', 'o')
        .replace('ù', 'u')

    private companion object {
        private const val FALLBACK_OTHER_CATEGORY_ID = "starter_expense_other"
    }
}
