package it.danielebufarini.spesify.data.notifications

import it.danielebufarini.spesify.data.runCatchingCancellable

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors

class ExpenseNotificationListenerService : NotificationListenerService(), KoinComponent {
    private val processorDispatcher = Executors.newFixedThreadPool(MAX_PARALLEL_NOTIFICATION_JOBS) { runnable ->
        Thread(runnable, "SpesifyNotificationProcessor").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val serviceScope = CoroutineScope(SupervisorJob() + processorDispatcher)
    private val whitelistRepository: AppWhitelistRepository by inject()
    private val interpretExpenseTextUseCase: InterpretExpenseTextUseCase by inject()
    private val merchantCategoryResolver: MerchantCategoryResolver by inject()
    private val notifier: ExpenseConfirmationNotifier by inject()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        serviceScope.launch {
            runCatchingCancellable { processNotification(notification) }
                .onFailure { error ->
                    Log.w(TAG, "Notification candidate processing failed", error)
                }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        processorDispatcher.close()
        super.onDestroy()
    }

    internal suspend fun processNotification(sbn: StatusBarNotification) {
        val sourcePackage = sbn.packageName?.takeIf(String::isNotBlank) ?: return
        Log.d(TAG, "Notification posted by package=$sourcePackage")

        val whitelistedPackages = whitelistRepository.getWhitelistedPackages()
        val isWhitelisted = sourcePackage in whitelistedPackages
        val isGoogleMessages = sourcePackage == GOOGLE_MESSAGES_PACKAGE
        Log.d(
            TAG,
            "Source check: package=$sourcePackage whitelisted=$isWhitelisted googleMessages=$isGoogleMessages cachedPackages=${whitelistedPackages.size}"
        )
        // SMS support must also work with an existing bank-only whitelist cache.
        if (!isWhitelisted && !isGoogleMessages) return

        val rawText = NotificationTextExtractor.extract(sbn.notification)
        if (rawText.isBlank()) {
            Log.d(TAG, "Ignored whitelisted notification: no text fields available")
            return
        }

        if (isGoogleMessages && !finecoSmsRegex.containsMatchIn(rawText)) return

        val interpretedCandidate = interpretExpenseTextUseCase.execute(
            rawText = rawText,
            canUseLocalLlmFallback = isWhitelisted && !isGoogleMessages
        )
        if (interpretedCandidate == null) {
            Log.d(TAG, "Ignored whitelisted notification: no amount candidate found")
            return
        }

        val merchant = interpretedCandidate.merchantDescription
        val textHash = rawText.sha256Hex()
        val dedupeKey = listOf(
            sourcePackage,
            sbn.postTime.toString(),
            interpretedCandidate.amountMinor.toString(),
            merchant.orEmpty(),
            textHash
        ).joinToString(separator = "|")
        if (!recentDeduplicator.shouldProcess(dedupeKey, System.currentTimeMillis())) {
            Log.d(TAG, "Ignored whitelisted notification: duplicate candidate")
            return
        }

        val categoryId = merchantCategoryResolver.resolveCategoryId(merchant)
        val candidate = ExpenseNotificationCandidate(
            sourcePackage = sourcePackage,
            amountMinor = interpretedCandidate.amountMinor,
            merchant = merchant,
            textHash = textHash,
            postTimeMillis = sbn.postTime,
            categoryId = categoryId
        )
        Log.d(
            TAG,
            "Parsed candidate: source=${interpretedCandidate.source} amountMinor=${candidate.amountMinor} merchantPresent=${!candidate.merchant.isNullOrBlank()} categoryResolved=${!categoryId.isNullOrBlank()} notificationId=${candidate.notificationId}"
        )
        notifier.show(candidate)
        Log.d(TAG, "Confirmation notification requested: notificationId=${candidate.notificationId}")
    }

    private companion object {
        private const val TAG = "SpesifyNotifDetect"
        private const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        private const val MAX_PARALLEL_NOTIFICATION_JOBS = 2
        private val finecoSmsRegex = Regex(
            "(?i)\\bFineco\\s*-\\s*SMS\\s+Gratuito:\\s*Autorizzato\\s+utilizzo\\s+Carta\\s+\\*{3}\\d{4}\\s+Importo:"
        )
        private val recentDeduplicator = RecentNotificationDeduplicator()
    }
}
