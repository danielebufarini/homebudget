package it.danielebufarini.spesify.data.notifications

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue

internal data class ExpenseNotificationCandidate(
    val sourcePackage: String,
    val amountMinor: Long,
    val merchant: String?,
    val textHash: String,
    val postTimeMillis: Long,
    val categoryId: String?
) {
    val confirmationId: String = buildConfirmationId(
        sourcePackage = sourcePackage,
        amountMinor = amountMinor,
        merchant = merchant,
        textHash = textHash,
        postTimeMillis = postTimeMillis
    )

    val notificationId: Int = confirmationId.hashCode().absoluteValue.coerceAtLeast(1)
}

internal data class ExpenseNotificationActionData(
    val confirmationId: String,
    val notificationId: Int,
    val amountMinor: Long,
    val merchant: String?,
    val categoryId: String?,
    val dateMillis: Long
)

internal fun ParsedExpenseNotification.toAmountMinor(): Long = amount.toMinorUnits()

internal fun BigDecimal.toMinorUnits(): Long = movePointRight(2)
    .setScale(0, RoundingMode.HALF_UP)
    .longValueExact()

private fun buildConfirmationId(
    sourcePackage: String,
    amountMinor: Long,
    merchant: String?,
    textHash: String,
    postTimeMillis: Long
): String = listOf(
    sourcePackage,
    postTimeMillis.toString(),
    amountMinor.toString(),
    merchant.orEmpty(),
    textHash
).joinToString(separator = "|").sha256Hex()
