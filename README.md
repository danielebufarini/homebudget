# Spesify

Spesify is a Kotlin Multiplatform personal finance app for Android and iOS.

The shared module contains the domain model, persistence, repositories, resources, and most Compose UI. Android uses the shared Compose app directly. iOS uses a SwiftUI shell that hosts shared Compose screens and keeps selected native SwiftUI flows for platform-specific UX.

## Features

- Expense and income tracking
- Recurring expenses and recurring incomes
- Typed categories for expenses and incomes, with archive, reassignment, color, and icon support
- Monthly views grouped by category or date
- Transaction search
- CSV import/export
- JSON backup/restore
- Google Drive AppData backup on Android
- iCloud backup on iOS
- Voice-assisted expense entry
- iOS payment screenshot import with Apple Vision OCR, Foundation Models availability checks, regex-first parsing, candidate-constrained local fallback, and sequential review of multiple detected expenses
- Android notification-based expense detection for supported apps, with deterministic parsing, local Gemini Nano fallback, and explicit confirmation
- Assistant and agent integrations for transaction entry, financial summaries, and category management
- Android and iOS home screen widgets
- English and Italian localization

## Tech Stack

- Kotlin Multiplatform
- Compose Multiplatform
- SwiftUI
- Apple Vision OCR
- Apple Foundation Models, when available on iOS
- Room KMP with bundled SQLite
- Koin
- Voyager
- WorkManager
- Ktor
- Jetpack DataStore
- ML Kit GenAI Prompt API / Gemini Nano on Android
- SKIE
- Apple App Intents
- Android AppFunctions
- kotlinx.serialization
- kotlinx.datetime

Current baseline:

- Kotlin 2.4.0
- Compose Multiplatform 1.11.1
- Android Gradle Plugin 9.2.1
- Android min SDK 31
- Android compile/target SDK 37

Dependency versions are defined in [gradle/libs.versions.toml](./gradle/libs.versions.toml).

## Project Layout

- [composeApp](./composeApp): shared Kotlin Multiplatform module.
- [androidApp](./androidApp): Android host application, manifest, widgets, AppFunctions, and platform wiring.
- [iosApp](./iosApp): Xcode project, SwiftUI app shell, App Intents, iCloud integration, native iOS screens, and widget extension.

Key shared source sets:

- [composeApp/src/commonMain](./composeApp/src/commonMain): shared app entry point, Compose UI, repositories, Room schema, resources, localization, and platform-neutral notification and payment screenshot interpretation pipeline.
- [composeApp/src/androidMain](./composeApp/src/androidMain): Android DI, platform bridges, Google Drive backup, Android voice entry, Android notification expense detection, and Gemini Nano fallback wiring.
- [composeApp/src/iosMain](./composeApp/src/iosMain): iOS DI, `ComposeUIViewController` factories, and Kotlin bridges used by SwiftUI, including the payment screenshot interpretation bridge.

Key Android areas:

- [androidApp/src/main/kotlin/it/danielebufarini/spesify/notificationsync](./androidApp/src/main/kotlin/it/danielebufarini/spesify/notificationsync): WorkManager scheduling and worker for supported-app whitelist synchronization.
- [composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/data/notifications](./composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/data/notifications): Android-only notification listener, whitelist cache, Gemini Nano fallback, confirmation actions, and permission helpers.
- [androidApp/src/main/assets/android_banks_packages_list.json](./androidApp/src/main/assets/android_banks_packages_list.json): bundled supported-app whitelist used only as a first-run fallback when the remote sync is unavailable and the cache is empty.

Key iOS areas:

- [iosApp/iosApp/App](./iosApp/iosApp/App): SwiftUI app entry point, navigation, shell actions, and localization helpers.
- [iosApp/iosApp/AppIntents](./iosApp/iosApp/AppIntents): native iOS App Intents exposed to Shortcuts and Siri.
- [iosApp/iosApp/Features/Expenses](./iosApp/iosApp/Features/Expenses): native transaction, monthly, grouped, income, search, and payment screenshot import screens.
- [iosApp/iosApp/Features/VoiceExpense](./iosApp/iosApp/Features/VoiceExpense): native voice expense flow.
- [iosApp/iosApp/Sync](./iosApp/iosApp/Sync): iCloud backup and widget summary storage.
- [iosApp/SpesifyWidget](./iosApp/SpesifyWidget): iOS widget extension.

The shared iOS framework is built as `ComposeApp.framework` and embedded by the Xcode project. The Xcode project uses file-system synchronized groups, so new Swift files under `iosApp/iosApp` are picked up without manual `project.pbxproj` edits.

## Architecture

### Persistence

Room KMP is the persistence layer, backed by bundled SQLite.

- Database: [SpesifyDatabase.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/database/SpesifyDatabase.kt)
- Entities and DAOs: [composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/database](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/database)
- Database builders: [common](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/DatabaseBuilderFactory.kt), [Android](./composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/data/DatabaseBuilderFactory.android.kt), [iOS](./composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/data/DatabaseBuilderFactory.ios.kt)
- Room schemas: [composeApp/schemas](./composeApp/schemas)

Repositories define the main application data boundary:

- [ExpenseRepository.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/ExpenseRepository.kt)
- [CategoryRepository.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/CategoryRepository.kt)

Categories are typed as expense or income, can be archived, and are seeded when the category table is empty.

Recurring transactions are stored as rules and materialized into expense or income rows for a rolling future window. This keeps the rule as the source of truth while preserving direct monthly queries, search, backup, export, and widget updates.

### Dependency Injection

Koin is the composition root.

- Shared graph: [composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/di](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/di)
- Android graph: [composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/di](./composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/di)
- iOS graph: [composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/di](./composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/di)

### Assistant and Agent Integrations

Spesify exposes selected finance actions through native system integration points while keeping business logic in shared Kotlin code.

- Shared transaction creation: [AddTransactionUseCase.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/AddTransactionUseCase.kt)
- Shared financial queries: [FinancialQueryUseCase.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/FinancialQueryUseCase.kt)
- Shared category management: [CategoryAgentUseCase.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/CategoryAgentUseCase.kt)
- iOS bridge: [composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/ui/screens](./composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/ui/screens)
- iOS App Intents: [iosApp/iosApp/AppIntents](./iosApp/iosApp/AppIntents)
- Android AppFunctions: [SpesifyTransactionAppFunctions.kt](./androidApp/src/main/kotlin/it/danielebufarini/spesify/appfunctions/SpesifyTransactionAppFunctions.kt)
- Android AppFunctions metadata: [app_metadata.xml](./androidApp/src/main/res/xml/app_metadata.xml)

Supported actions:

- Add a single non-recurring expense or income. User-facing amounts are accepted in standard currency format, such as `55.56`, and converted internally to `Long` minor units. Transaction creation does not auto-create missing categories; unknown or wrong-type categories return a confirmation/error result so an assistant does not silently create hallucinated categories.
- Request total expenses for the current month, for a selected month, or for an inclusive date period.
- Request total income for the current month, for a selected month, or for an inclusive date period.
- Request the current balance, matching the dashboard cumulative balance through the current calendar month.
- List existing expense or income categories using compact, human-readable output for Shortcuts/Siri and structured data for Android agents.
- Add an explicit expense or income category.

App Intents and AppFunctions should remain thin adapters: they parse native parameters, call the shared use cases, and return display-ready results without duplicating persistence or validation logic. Android AppFunctions expose flat primitive parameters for easier ADB and agent invocation. Destructive category deletion is intentionally not exposed through assistant/agent integrations.

### UI and Navigation

- Shared Compose navigation uses Voyager.
- Android starts from [MainActivity.kt](./androidApp/src/main/kotlin/it/danielebufarini/spesify/MainActivity.kt) and runs the shared Compose app.
- iOS starts from [ContentView.swift](./iosApp/iosApp/App/ContentView.swift), uses SwiftUI `NavigationStack`, and hosts shared Compose through [MainViewController.kt](./composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/MainViewController.kt).
- Native iOS expense screens use observer-backed SwiftUI view models and Kotlin bridge classes under [composeApp/src/iosMain](./composeApp/src/iosMain).
- The native iOS transaction date picker uses a custom SwiftUI calendar whose weekday headers are ordered from `Calendar.current.firstWeekday`, so localized weekday names line up with the day grid.

### Backup and Transfer

Full backup is JSON-based.

- Backup format and restore logic: [BudgetBackup.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/BudgetBackup.kt)
- Backup orchestration: [CloudSyncService.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/CloudSyncService.kt)
- Android Google Drive store: [AndroidCloudBackupStore.android.kt](./composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/data/AndroidCloudBackupStore.android.kt)
- Android authorization: [GoogleDriveAuthorizationManager.android.kt](./composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/data/GoogleDriveAuthorizationManager.android.kt)
- iOS iCloud store: [ICloudBackupStore.swift](./iosApp/iosApp/Sync/ICloudBackupStore.swift)

CSV import/export is separate from full backup.

- [CsvBudgetExport.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/csv/CsvBudgetExport.kt)
- [CsvBudgetImport.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/data/csv/CsvBudgetImport.kt)

### Voice Entry

Voice entry is platform-specific, with shared prompt and contract helpers in [VoiceExpensePrompt.kt](./composeApp/src/commonMain/kotlin/it/danielebufarini/spesify/ui/screens/VoiceExpensePrompt.kt).

- Android implementation: [composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/ui/screens](./composeApp/src/androidMain/kotlin/it/danielebufarini/spesify/ui/screens)
- iOS implementation: [iosApp/iosApp/Features/VoiceExpense](./iosApp/iosApp/Features/VoiceExpense)

### Android Notification Expense Detection

Notification-based expense detection is Android-only and opt-in. The Android host registers the `NotificationListenerService`, while shared Android source-set code owns whitelist caching, parsing, confirmation notifications, action handling, and permission helpers.

Supported financial app packages are fetched with Ktor from the remote JSON whitelist and cached locally with Jetpack DataStore. The sync runs through WorkManager periodically and can also be triggered at cold start when the cache is empty or stale. If the remote sync fails and there is no cached whitelist yet, the app bootstraps the cache from the bundled asset [android_banks_packages_list.json](./androidApp/src/main/assets/android_banks_packages_list.json). Existing cache data is preserved on sync failure.

When Notification Listener access is enabled by the user, Spesify filters posted notifications by source package using the local whitelist, extracts notification text locally, and interprets candidate expense amounts and merchants through a shared pipeline. The pipeline runs the deterministic regex parser first and uses its high-confidence result directly. If regex parsing fails, produces low confidence, or finds an amount without a useful merchant, shared Kotlin extracts deterministic money candidates from explicit currency amounts before Android may fall back to the local Gemini Nano interpreter. Gemini receives the notification text and the allowed candidate list, then can only classify the text and select one `selectedAmountMinor` value from those candidates. The fallback is only attempted for whitelisted source packages, runs outside the notification listener callback, and normalizes strictly validated JSON into the same parsed candidate model used by regex.

Matching notifications show a local confirmation notification with explicit actions: confirm, modify, or ignore. Confirm saves through the existing transaction creation path; modify opens the existing expense editor with parsed values pre-filled; ignore dismisses without saving. Raw notification contents are used only transiently for local interpretation: they are not persisted, logged, or sent to external services. Parsed amounts and LLM-selected candidates are represented internally as `Long` minor units.

### iOS Payment Screenshot Import

Payment screenshot import is iOS-native, expense-oriented, and available from the dashboard and monthly expenses list input dock. It is intentionally not exposed from the income list dock, where the secondary dock action starts voice input directly instead of showing the screenshot import menu. The SwiftUI flow lets the user pick an image from Photos, extracts text on-device with Apple Vision OCR, checks local Foundation Models availability and language support, then passes the transient OCR text into the shared `InterpretExpenseTextUseCase`. The OCR text is not persisted, logged, sent to analytics, or sent to external services.

The shared interpretation pipeline can return multiple expense candidates from one OCR input. It first applies deterministic regex parsing, including payment-notification blocks such as `pagamento di <amount> EUR presso <merchant> con la tua carta`, `payment of <amount> EUR at <merchant> with your card`, and known bank formats such as Fineco `Importo: <amount> EUR, per: <merchant>`. When the regex result is incomplete or ambiguous, common Kotlin extracts deterministic money candidates and the local iOS fallback remains available through the platform LLM bridge. The Swift/Foundation Models adapter can use guided generation, but it receives only the OCR text plus an allowed amount-candidate list and returns raw JSON with `selectedAmountMinor` choices for shared Kotlin validation. Multiple payment notifications in one screenshot are treated as separate candidates, not as ambiguity by themselves.

The Kotlin validator is the safety boundary for both Android and iOS. It accepts only safe candidates: expenses with positive `Long` minor-unit amounts, EUR currency or safely inferred EUR, sufficient confidence, and a `selectedAmountMinor` that exactly matches one deterministic money candidate from the source text. Invented amounts, unsupported currencies, refunds, incoming transfers, salaries, top-ups, balance-only notifications, and plain numbers from the lock screen or status bar, such as time, date, battery percentage, phone numbers, or card suffixes, are rejected as transaction amounts. The shared pipeline allows up to 15 seconds for local LLM fallback work, and the iOS bridge allows up to 25 seconds for the on-device Foundation Models response before failing closed.

When multiple candidates are found, iOS queues them in memory and opens the native expense editor one at a time. The user confirms, edits, or cancels each candidate before anything is saved.

## Localization

- Shared Compose strings: [values](./composeApp/src/commonMain/composeResources/values/strings.xml), [values-it](./composeApp/src/commonMain/composeResources/values-it/strings.xml)
- Native iOS strings: [Localizable.xcstrings](./iosApp/iosApp/Localizable.xcstrings)
- iOS widget strings: [en](./iosApp/SpesifyWidget/en.lproj/Localizable.strings), [it](./iosApp/SpesifyWidget/it.lproj/Localizable.strings)

Native iOS localization also covers App Intent/App Shortcut metadata and payment screenshot import errors, including Foundation Models availability, Apple Intelligence enablement, model preparation, unsupported app language, OCR failure, and no-text cases.

## Build

Build the Android debug APK:

```sh
./gradlew :androidApp:assembleDebug
```

Compile shared Android code:

```sh
./gradlew :composeApp:compileAndroidMain
```

Compile the shared iOS simulator framework:

```sh
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Build the iOS app from Xcode by opening [iosApp/iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) and running the `iosApp` scheme.

Command-line iOS build:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=<installed simulator name>' build
```

## Verification

Run the local verification suite:

```sh
./gradlew verifyAll
```

`verifyAll` runs:

- `:androidApp:test`
- `:composeApp:testAndroidHostTest`
- `:androidApp:assembleDebug`
- `:composeApp:compileKotlinIosSimulatorArm64`

Run the assistant integration smoke checks after changing App Intents, AppFunctions, transaction creation, financial queries, category management, or amount/date parsing:

1. iOS: delete and reinstall the app, open it once, recreate the Shortcuts actions, then add one expense using a standard amount such as `55.56`.
2. iOS: run the total-expenses, total-income, current-balance, and list-categories shortcuts; verify the displayed values against the app UI and confirm category output is readable.
3. Android: rebuild and reinstall the debug app, list registered AppFunctions with `adb shell cmd app_function list-app-functions`, then execute one add-transaction call, one financial-query call, and one non-destructive category-management call such as list or add category.
4. Android: verify that returned minor-unit amounts, display strings, and category results match the dashboard, monthly lists, and category list.

Run the Android notification detection smoke checklist after changing the notification listener, whitelist sync, parser, permission handling, or confirmation actions:

1. Install the Android app, enable Notification Listener access for Spesify, and allow app notifications on Android 13+.
2. Open Spesify once so the whitelist cache can be populated from the remote source or bundled fallback.
3. Post a notification from a whitelisted test package, such as `com.fineco.it`, with text like `Pagamento carta 12,34 EUR presso SUPERMERCATO TEST`.
4. Verify the confirmation notification appears, `Ignora` dismisses without saving, `Modifica` opens the pre-filled expense editor, and `Conferma` saves exactly one expense.
5. Verify notifications from non-whitelisted packages are ignored and do not trigger the local LLM fallback.
6. Post a whitelisted notification that regex cannot fully parse, then verify the local fallback does not block the listener and either produces a validated confirmation candidate or safely ignores the notification when Gemini Nano is unavailable or validation fails.
7. Check Logcat while testing and verify raw notification text is not printed.

Run the iOS smoke checklist after changing SKIE, Kotlin/Native bridge APIs, Room persistence, recurring transaction generation, iCloud backup, CSV transfer, or native SwiftUI transaction screens:

1. Add an expense category in the native add/edit transaction flow, verify it is selected, then reopen the category picker and confirm it persists.
2. Open the native transaction date picker and verify weekday headers match the day grid for the current locale and first-weekday setting.
3. Open monthly expenses and monthly incomes, switch grouping between category and date, expand/collapse sections, and delete one non-recurring item.
4. Create or edit one recurring expense or income, navigate to a future month, then delete the series and verify the generated rows update consistently.
5. Search for a term with expense and income matches, load more results if available, switch grouping, and delete one result.
6. Export a CSV date range, verify the file content, import the file, and confirm success or skipped-row feedback.
7. Launch an empty iOS install with an iCloud backup available, preview restore counts, restore, and verify dashboard data.
8. Use voice entry to create or update a draft, save it, and verify the expense appears in the current month.
9. From the monthly expenses screen, import a payment screenshot with one notification and verify the native editor is pre-filled with the correct amount and merchant.
10. Import a payment screenshot with multiple payment notifications and verify the editor opens candidates sequentially, one transaction at a time.
11. Import a screenshot that also contains lock-screen/status-bar numbers and verify time, date, battery percentage, and card suffixes are not accepted as transaction amounts.
12. Trigger app backgrounding or widget refresh, then confirm the iOS widget summary shows current month totals and updated timestamp.

## Setup Notes

### Android Notification Detection

Notification-based expense detection requires the user to enable Android Notification Listener access manually from system settings. This is not a normal runtime permission and cannot be granted through a permission dialog. On Android 13 and later, Spesify also needs the standard notification posting permission to show confirmation notifications.

The feature remains inactive when the required access is missing. It only processes notifications locally from packages present in the cached supported-app whitelist.

### Android AppFunctions

AppFunctions are wired in the Android host app and backed by shared Kotlin use cases. They require an Android version that exposes the `app_function` system service; older devices can still build and run the app, but cannot execute AppFunctions through system agents or `adb shell cmd app_function`.

For ADB smoke tests, pass flat JSON parameters and wrap the full command in `adb shell "..."` when the JSON contains quotes, for example `--parameters '{\"kind\":\"expense\",\"amount\":\"12.50\"}'`.

### Android Google Drive Backup

Drive backup requires Android OAuth configuration.

1. Create an Android OAuth client for the package name and signing certificate in use.
2. If Credential Manager sign-in is enabled, create a Web OAuth client as well.
3. Put the Web client ID in [google_identity.xml](./composeApp/src/androidMain/res/values/google_identity.xml).

Without this setup, local backup still works.

### iOS Payment Screenshot Import

Payment screenshot import is an in-app flow exposed from the monthly expenses screen. It is not currently an iOS Share Sheet target; appearing in Photos as a share recipient would require a separate Share Extension.

The app still builds and runs when Foundation Models are unavailable, but the screenshot import flow checks availability after OCR and reports a localized error when the device is not eligible, Apple Intelligence is disabled, the model is still preparing, or the current app language is unsupported. The fallback bridge also fails closed: model errors or invalid output produce no import candidate rather than an unsafe transaction.

### iOS

The iOS target depends on the Xcode project configuration in [iosApp](./iosApp), including iCloud and widget entitlements.
