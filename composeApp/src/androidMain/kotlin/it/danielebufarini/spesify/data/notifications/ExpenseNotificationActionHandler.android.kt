package it.danielebufarini.spesify.data.notifications

import it.danielebufarini.spesify.data.IdGenerator
import it.danielebufarini.spesify.data.PendingExpense
import it.danielebufarini.spesify.data.TransactionWriteRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal sealed interface ExpenseNotificationConfirmResult {
    data object Saved : ExpenseNotificationConfirmResult
    data object AlreadyHandled : ExpenseNotificationConfirmResult
    data object NeedsReview : ExpenseNotificationConfirmResult
    data object Failed : ExpenseNotificationConfirmResult
}

internal class ExpenseNotificationActionHandler(
    private val actionStore: ExpenseNotificationActionStore,
    private val transactionWriteRepository: TransactionWriteRepository
) {
    suspend fun confirm(data: ExpenseNotificationActionData): ExpenseNotificationConfirmResult {
        if (!actionStore.tryConsume(data.confirmationId)) {
            return ExpenseNotificationConfirmResult.AlreadyHandled
        }
        val categoryId = data.categoryId?.takeIf(String::isNotBlank)
            ?: return ExpenseNotificationConfirmResult.NeedsReview
        if (data.amountMinor <= 0L) return ExpenseNotificationConfirmResult.NeedsReview

        return runCatching {
            transactionWriteRepository.insertExpenses(
                expenses = listOf(
                    PendingExpense(
                        id = IdGenerator.newId("notification-expense"),
                        amount = data.amountMinor,
                        date = data.dateMillis.normalizeToLocalStartOfDay(),
                        categoryId = categoryId,
                        description = data.merchant?.trim()?.takeIf(String::isNotBlank),
                        isShared = false,
                        recurringSeriesId = null
                    )
                )
            )
        }.fold(
            onSuccess = { ExpenseNotificationConfirmResult.Saved },
            onFailure = { ExpenseNotificationConfirmResult.Failed }
        )
    }

    suspend fun consumeWithoutSaving(confirmationId: String): Boolean = actionStore.tryConsume(confirmationId)

    internal companion object {
        fun currentLocalDateMillis(): Long = Clock.System.now()
            .toEpochMilliseconds()
            .normalizeToLocalStartOfDay()
    }
}

internal fun Long.normalizeToLocalStartOfDay(): Long {
    val timeZone = TimeZone.currentSystemDefault()
    val instant = takeIf { it > 0L }?.let(Instant::fromEpochMilliseconds) ?: Clock.System.now()
    val localDate = instant.toLocalDateTime(timeZone).date
    return localDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
}
