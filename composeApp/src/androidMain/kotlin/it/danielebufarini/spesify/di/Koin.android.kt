package it.danielebufarini.spesify.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import it.danielebufarini.spesify.AndroidStartupRestore
import it.danielebufarini.spesify.PlatformStartupRestore
import it.danielebufarini.spesify.data.AndroidCloudBackupStore
import it.danielebufarini.spesify.data.DashboardPreferencesStore
import it.danielebufarini.spesify.data.DatabaseBuilderFactory
import it.danielebufarini.spesify.data.GoogleDriveAuthorizationManager
import it.danielebufarini.spesify.data.PlatformDashboardPreferencesStore
import it.danielebufarini.spesify.data.notifications.AndroidLocalLlmExpenseTextInterpreter
import it.danielebufarini.spesify.data.notifications.AppWhitelistCache
import it.danielebufarini.spesify.data.notifications.AppWhitelistFallbackDataSource
import it.danielebufarini.spesify.data.notifications.AppWhitelistHttpTransport
import it.danielebufarini.spesify.data.notifications.AppWhitelistRemoteDataSource
import it.danielebufarini.spesify.data.notifications.AppWhitelistRepository
import it.danielebufarini.spesify.data.notifications.AssetsAppWhitelistFallbackDataSource
import it.danielebufarini.spesify.data.notifications.DataStoreAppWhitelistCache
import it.danielebufarini.spesify.data.notifications.DataStoreExpenseNotificationActionStore
import it.danielebufarini.spesify.data.notifications.DefaultAppWhitelistRepository
import it.danielebufarini.spesify.data.notifications.DefaultMerchantCategoryResolver
import it.danielebufarini.spesify.data.notifications.ExpenseConfirmationNotifier
import it.danielebufarini.spesify.data.notifications.ExpenseInterpretationPipeline
import it.danielebufarini.spesify.data.notifications.ExpenseNotificationActionHandler
import it.danielebufarini.spesify.data.notifications.ExpenseNotificationActionStore
import it.danielebufarini.spesify.data.notifications.KtorAppWhitelistHttpTransport
import it.danielebufarini.spesify.data.notifications.KtorAppWhitelistRemoteDataSource
import it.danielebufarini.spesify.data.notifications.LlmExpenseJsonValidator
import it.danielebufarini.spesify.data.notifications.MerchantCategoryResolver
import it.danielebufarini.spesify.data.notifications.NotificationDetectionPermissionHelper
import it.danielebufarini.spesify.data.notifications.RegexExpenseTextInterpreter
import it.danielebufarini.spesify.data.notifications.appWhitelistJson
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { AndroidStartupRestore(androidContext(), get(), get()) }
    single { GoogleDriveAuthorizationManager(androidContext()) }
    single { AndroidCloudBackupStore(androidContext(), get()) }
    single { DatabaseBuilderFactory(androidContext()) }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore(androidContext()) }
    single { appWhitelistJson }
    single { HttpClient(Android) { expectSuccess = false } }
    single<AppWhitelistHttpTransport> { KtorAppWhitelistHttpTransport(get()) }
    single<AppWhitelistRemoteDataSource> { KtorAppWhitelistRemoteDataSource(get(), get()) }
    single<AppWhitelistFallbackDataSource> { AssetsAppWhitelistFallbackDataSource(androidContext(), get()) }
    single<AppWhitelistCache> { DataStoreAppWhitelistCache(androidContext(), get()) }
    single<AppWhitelistRepository> { DefaultAppWhitelistRepository(get(), get(), get()) }
    single { NotificationDetectionPermissionHelper(androidContext()) }
    single { RegexExpenseTextInterpreter() }
    single { LlmExpenseJsonValidator() }
    single { AndroidLocalLlmExpenseTextInterpreter(get()) }
    single {
        ExpenseInterpretationPipeline(
            regexInterpreter = get<RegexExpenseTextInterpreter>(),
            localLlmInterpreter = get<AndroidLocalLlmExpenseTextInterpreter>()
        )
    }
    single<MerchantCategoryResolver> { DefaultMerchantCategoryResolver(get()) }
    single<ExpenseNotificationActionStore> { DataStoreExpenseNotificationActionStore(androidContext()) }
    single { ExpenseNotificationActionHandler(get(), get()) }
    single { ExpenseConfirmationNotifier(androidContext(), get()) }
}
