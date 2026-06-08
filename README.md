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
- Android and iOS home screen widgets
- English and Italian localization

## Tech Stack

- Kotlin Multiplatform
- Compose Multiplatform
- SwiftUI
- Room KMP with bundled SQLite
- Koin
- Voyager
- SKIE
- kotlinx.serialization
- kotlinx.datetime

Current baseline:

- JDK 21
- Kotlin 2.3.10
- Compose Multiplatform 1.11.1
- Android Gradle Plugin 9.2.1
- Android min SDK 26
- Android compile/target SDK 37

Dependency versions are defined in [gradle/libs.versions.toml](./gradle/libs.versions.toml).

## Project Layout

- [composeApp](./composeApp): shared Kotlin Multiplatform module.
- [androidApp](./androidApp): Android host application, manifest, widgets, and platform wiring.
- [iosApp](./iosApp): Xcode project, SwiftUI app shell, iCloud integration, native iOS screens, and widget extension.

Key shared source sets:

- [composeApp/src/commonMain](./composeApp/src/commonMain): shared app entry point, Compose UI, repositories, Room schema, resources, and localization.
- [composeApp/src/androidMain](./composeApp/src/androidMain): Android DI, platform bridges, Google Drive backup, and Android voice entry.
- [composeApp/src/iosMain](./composeApp/src/iosMain): iOS DI, `ComposeUIViewController` factories, and Kotlin bridges used by SwiftUI.

Key iOS areas:

- [iosApp/iosApp/App](./iosApp/iosApp/App): SwiftUI app entry point, navigation, shell actions, and localization helpers.
- [iosApp/iosApp/Features/Expenses](./iosApp/iosApp/Features/Expenses): native transaction, monthly, grouped, income, and search screens.
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

### UI and Navigation

- Shared Compose navigation uses Voyager.
- Android starts from [MainActivity.kt](./androidApp/src/main/kotlin/it/danielebufarini/spesify/MainActivity.kt) and runs the shared Compose app.
- iOS starts from [ContentView.swift](./iosApp/iosApp/App/ContentView.swift), uses SwiftUI `NavigationStack`, and hosts shared Compose through [MainViewController.kt](./composeApp/src/iosMain/kotlin/it/danielebufarini/spesify/MainViewController.kt).
- Native iOS expense screens use observer-backed SwiftUI view models and Kotlin bridge classes under [composeApp/src/iosMain](./composeApp/src/iosMain).

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

## Localization

- Shared Compose strings: [values](./composeApp/src/commonMain/composeResources/values/strings.xml), [values-it](./composeApp/src/commonMain/composeResources/values-it/strings.xml)
- Native iOS strings: [Localizable.xcstrings](./iosApp/iosApp/Localizable.xcstrings)
- iOS widget strings: [en](./iosApp/SpesifyWidget/en.lproj/Localizable.strings), [it](./iosApp/SpesifyWidget/it.lproj/Localizable.strings)

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

Run the iOS smoke checklist after changing SKIE, Kotlin/Native bridge APIs, Room persistence, recurring transaction generation, iCloud backup, CSV transfer, or native SwiftUI transaction screens:

1. Add an expense category in the native add/edit transaction flow, verify it is selected, then reopen the category picker and confirm it persists.
2. Open monthly expenses and monthly incomes, switch grouping between category and date, expand/collapse sections, and delete one non-recurring item.
3. Create or edit one recurring expense or income, navigate to a future month, then delete the series and verify the generated rows update consistently.
4. Search for a term with expense and income matches, load more results if available, switch grouping, and delete one result.
5. Export a CSV date range, verify the file content, import the file, and confirm success or skipped-row feedback.
6. Launch an empty iOS install with an iCloud backup available, preview restore counts, restore, and verify dashboard data.
7. Use voice entry to create or update a draft, save it, and verify the expense appears in the current month.
8. Trigger app backgrounding or widget refresh, then confirm the iOS widget summary shows current month totals and updated timestamp.

## Setup Notes

### Android Google Drive Backup

Drive backup requires Android OAuth configuration.

1. Create an Android OAuth client for the package name and signing certificate in use.
2. If Credential Manager sign-in is enabled, create a Web OAuth client as well.
3. Put the Web client ID in [google_identity.xml](./composeApp/src/androidMain/res/values/google_identity.xml).

Without this setup, local backup still works.

### iOS

The iOS target depends on the Xcode project configuration in [iosApp](./iosApp), including iCloud and widget entitlements.
