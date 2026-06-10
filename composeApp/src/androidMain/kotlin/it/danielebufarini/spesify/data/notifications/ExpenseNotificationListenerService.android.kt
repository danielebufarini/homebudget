package it.danielebufarini.spesify.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExpenseNotificationListenerService : NotificationListenerService(), KoinComponent {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val whitelistRepository: AppWhitelistRepository by inject()
    private val parser: ExpenseNotificationTextParser by inject()
    private val merchantCategoryResolver: MerchantCategoryResolver by inject()
    private val notifier: ExpenseConfirmationNotifier by inject()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        serviceScope.launch {
            runCatching { processNotification(notification) }
                .onFailure { error ->
                    Log.w(TAG, "Notification candidate processing failed", error)
                }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {
        val sourcePackage = sbn.packageName?.takeIf(String::isNotBlank) ?: return
        Log.d(TAG, "Notification posted by package=$sourcePackage")

        val whitelistedPackages = whitelistRepository.getWhitelistedPackages()
        val isWhitelisted = sourcePackage in whitelistedPackages
        Log.d(
            TAG,
            "Whitelist check: package=$sourcePackage whitelisted=$isWhitelisted cachedPackages=${whitelistedPackages.size}"
        )
        if (!isWhitelisted) return

        val rawText = NotificationTextExtractor.extract(sbn.notification)
        if (rawText.isBlank()) {
            Log.d(TAG, "Ignored whitelisted notification: no text fields available")
            return
        }

        val parsed = parser.parse(rawText)
        if (parsed == null) {
            Log.d(TAG, "Ignored whitelisted notification: parser found no amount")
            return
        }

        val textHash = rawText.sha256Hex()
        val dedupeKey = listOf(
            sourcePackage,
            sbn.postTime.toString(),
            parsed.toAmountMinor().toString(),
            parsed.merchant.orEmpty(),
            textHash
        ).joinToString(separator = "|")
        if (!recentDeduplicator.shouldProcess(dedupeKey, System.currentTimeMillis())) {
            Log.d(TAG, "Ignored whitelisted notification: duplicate candidate")
            return
        }

        val categoryId = merchantCategoryResolver.resolveCategoryId(parsed.merchant)
        val candidate = ExpenseNotificationCandidate(
            sourcePackage = sourcePackage,
            amountMinor = parsed.toAmountMinor(),
            merchant = parsed.merchant,
            textHash = textHash,
            postTimeMillis = sbn.postTime,
            categoryId = categoryId
        )
        Log.d(
            TAG,
            "Parsed candidate: amountMinor=${candidate.amountMinor} merchantPresent=${!candidate.merchant.isNullOrBlank()} categoryResolved=${!categoryId.isNullOrBlank()} notificationId=${candidate.notificationId}"
        )
        val shown = notifier.show(candidate)
        Log.d(TAG, "Confirmation notification requested: shown=$shown notificationId=${candidate.notificationId}")
    }

    private companion object {
        private const val TAG = "SpesifyNotifDetect"
        private val recentDeduplicator = RecentNotificationDeduplicator()
    }
}
