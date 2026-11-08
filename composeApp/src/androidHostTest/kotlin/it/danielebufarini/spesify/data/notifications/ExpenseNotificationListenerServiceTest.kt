package it.danielebufarini.spesify.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ExpenseNotificationListenerServiceTest {
    private val whitelistedPackages = mutableSetOf("com.fineco.it")
    private var llmCalls = 0
    private lateinit var controller: ServiceController<ExpenseNotificationListenerService>
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        val useCase = InterpretExpenseTextUseCase(
            ExpenseInterpretationPipeline(
                localLlmInterpreter = object : LocalExpenseTextLlmInterpreter {
                    override suspend fun interpret(text: String): String {
                        llmCalls += 1
                        return "{}"
                    }
                }
            )
        )
        startKoin {
            modules(module {
                single<AppWhitelistRepository> {
                    object : AppWhitelistRepository {
                        override suspend fun refreshWhitelist() = Result.success(Unit)
                        override suspend fun getWhitelistedPackages(): Set<String> = whitelistedPackages
                        override suspend fun isCacheEmptyOrStale() = false
                        override fun observeWhitelistState() = flowOf(
                            AppWhitelistState(emptyList(), null, false)
                        )
                    }
                }
                single { useCase }
                single<MerchantCategoryResolver> {
                    object : MerchantCategoryResolver {
                        override suspend fun resolveCategoryId(merchant: String?) = "test_category"
                    }
                }
                single {
                    ExpenseConfirmationNotifier(context, NotificationDetectionPermissionHelper(context))
                }
            })
        }
        controller = Robolectric.buildService(ExpenseNotificationListenerService::class.java).create()
    }

    @After
    fun tearDown() {
        controller.destroy()
        stopKoin()
    }

    @Test
    fun googleMessagesFinecoSmsCreatesConfirmationWithExistingBankOnlyCache() = runTest {
        controller.get().processNotification(notification(GOOGLE_MESSAGES_PACKAGE, finecoSms))

        assertConfirmation(amountMinor = 960L, merchant = "PEPCO 220135 MONZA")
    }

    @Test
    fun googleMessagesFinecoSmsWorksBeforeWhitelistSync() = runTest {
        whitelistedPackages.clear()

        controller.get().processNotification(notification(GOOGLE_MESSAGES_PACKAGE, finecoSms))

        assertConfirmation(amountMinor = 960L, merchant = "PEPCO 220135 MONZA")
    }

    @Test
    fun googleMessagesFinecoSmsSupportsDifferentCardsAmountsAndMerchants() = runTest {
        controller.get().processNotification(notification(
            GOOGLE_MESSAGES_PACKAGE,
            "Fineco - SMS Gratuito: Autorizzato utilizzo Carta ***9007 Importo: 1.234,56 EUR, per: Amazon.it. Info: 0228992899"
        ))

        assertConfirmation(amountMinor = 123_456L, merchant = "Amazon.it")
    }

    @Test
    fun googleMessagesIgnoresOtherSmsEvenWhenPackageIsWhitelisted() = runTest {
        whitelistedPackages.add(GOOGLE_MESSAGES_PACKAGE)

        for (text in listOf(
            "Ci vediamo da PEPCO, porta 9,60 EUR",
            "Pagamento carta 9,60 EUR presso PEPCO",
            "Fineco - SMS Gratuito: Codice OTP 2813 per pagamento di 9,60 EUR presso PEPCO",
            "Fineco - SMS Gratuito: Negato utilizzo Carta ***2813 Importo: 9,6 EUR, per: PEPCO. Info: 0228992899"
        )) {
            controller.get().processNotification(notification(GOOGLE_MESSAGES_PACKAGE, text))
        }

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertEquals(0, llmCalls)
    }

    @Test
    fun unsupportedPackageCannotTriggerFinecoSmsConfirmation() = runTest {
        controller.get().processNotification(notification("com.example.messaging", finecoSms))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertEquals(0, llmCalls)
    }

    @Test
    fun bankNotificationsStillCreateConfirmations() = runTest {
        controller.get().processNotification(notification(
            "com.fineco.it",
            "Pagamento carta 12,34 EUR presso SUPERMERCATO TEST"
        ))

        assertConfirmation(amountMinor = 1_234L, merchant = "SUPERMERCATO TEST")
    }

    private fun assertConfirmation(amountMinor: Long, merchant: String) {
        val confirmation = shadowOf(notificationManager).allNotifications.single()
        val intent = shadowOf(confirmation.actions.first().actionIntent).savedIntent

        assertEquals(amountMinor, intent.getLongExtra(ExpenseConfirmationNotificationContract.EXTRA_AMOUNT_MINOR, -1L))
        assertEquals(merchant, intent.getStringExtra(ExpenseConfirmationNotificationContract.EXTRA_MERCHANT))
        assertEquals(0, llmCalls)
    }

    private fun notification(sourcePackage: String, text: String): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Fineco")
                putCharSequence(Notification.EXTRA_TEXT, text)
                putCharSequence(Notification.EXTRA_BIG_TEXT, text)
            }
        }
        return StatusBarNotification(
            sourcePackage, sourcePackage, 1, null, Process.myUid(), 0, 0,
            notification, Process.myUserHandle(), System.nanoTime()
        )
    }

    private companion object {
        const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        const val finecoSms = "Fineco - SMS Gratuito: Autorizzato utilizzo Carta ***2813 Importo: 9,6 EUR, per: PEPCO 220135 MONZA. Info: 0228992899"
    }
}
