package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.data.notifications.ExpenseTextCandidate
import it.danielebufarini.spesify.data.notifications.InterpretExpenseTextUseCase
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosPaymentScreenshotExpenseCandidate(
    val amountInput: String,
    val descriptionText: String,
    val dateMillis: Long,
    val categoryId: String?
)

class IosPaymentScreenshotExpenseCandidateQueue(
    candidates: List<IosPaymentScreenshotExpenseCandidate>
) {
    private val pending = candidates.toMutableList()

    val remainingCount: Int
        get() = pending.size

    fun nextCandidate(): IosPaymentScreenshotExpenseCandidate? =
        if (pending.isEmpty()) null else pending.removeAt(0)
}

class IosPaymentScreenshotController {
    private val interpretExpenseTextUseCase: InterpretExpenseTextUseCase by lazy {
        ensureIosPaymentScreenshotKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    suspend fun interpretOcrText(rawText: String): IosPaymentScreenshotExpenseCandidate? =
        interpretOcrTextQueue(rawText).nextCandidate()

    suspend fun interpretOcrTextQueue(rawText: String): IosPaymentScreenshotExpenseCandidateQueue {
        val candidates = withContext(Dispatchers.Default) {
            interpretExpenseTextUseCase.executeAll(
                rawText = rawText,
                canUseLocalLlmFallback = true
            )
        }

        return IosPaymentScreenshotExpenseCandidateQueue(
            candidates.map { it.toIosPaymentScreenshotExpenseCandidate() }
        )
    }

    private fun ExpenseTextCandidate.toIosPaymentScreenshotExpenseCandidate(): IosPaymentScreenshotExpenseCandidate =
        IosPaymentScreenshotExpenseCandidate(
            amountInput = formatAmountInput(amountMinor),
            descriptionText = description.orEmpty(),
            dateMillis = dateMillis ?: 0L,
            categoryId = categoryId
        )
}

private fun ensureIosPaymentScreenshotKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
