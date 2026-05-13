# HomeBudget

HomeBudget is a Kotlin Multiplatform personal-finance app with two production targets:

- Android, driven mainly by shared Compose UI in `composeApp`
- iOS, delivered as a SwiftUI app in `iosApp` that embeds the shared Kotlin framework as `ComposeApp`

Most business logic, persistence, resources, and a large part of the UI live in shared Kotlin. The iOS target keeps its own SwiftUI shell and uses native screens where that leads to a better platform fit.

## Project Layout

### `composeApp`

`composeApp` is the shared KMP module.

- `src/commonMain`
  - shared application entry point
  - repository and persistence code
  - Compose screens and reusable UI
  - localization helpers and Compose resources
  - Room entities, DAOs, and schema export
- `src/androidMain`
  - Android bootstrap and platform DI
  - Android-specific date pickers, file launchers, backup integration, and voice-input implementation
- `src/iosMain`
  - `ComposeUIViewController` factories used by SwiftUI
  - iOS platform DI
  - bridge classes used by Swift code for grouped expenses, native expense editing, CSV import/export, backup, date picking, and voice-entry support

Within `commonMain`, the screen source tree is grouped by responsibility:

- [`ui/screens/dashboard`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/dashboard)
- [`ui/screens/expenses`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/expenses)
- [`ui/screens/income`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/income)
- [`ui/screens/transactions`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/transactions)
- [`ui/screens/categories`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/categories)
- [`ui/screens/platform`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/platform)
- [`ui/screens/common`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/common)

The Kotlin package name is `it.homebudget.app.ui.screens` for most of these files.

### `iosApp`

`iosApp` is the native iOS application target. Its source tree is also split by role:

- [`App`](./iosApp/iosApp/App): SwiftUI app entry, root navigation, app-level localization helpers
- [`UI`](./iosApp/iosApp/UI): shared SwiftUI host utilities, glass styling, CSV sheets, Liquid Glass calendar
- [`Features`](./iosApp/iosApp/Features): feature-specific SwiftUI code
  - [`Expenses`](./iosApp/iosApp/Features/Expenses)
  - [`VoiceExpense`](./iosApp/iosApp/Features/VoiceExpense)
  - [`Categories`](./iosApp/iosApp/Features/Categories)
- [`Sync`](./iosApp/iosApp/Sync): iCloud backup and widget support

Build resources such as `Info.plist`, `HomeBudget.entitlements`, `Localizable.xcstrings`, and `Assets.xcassets` stay at the target root.

## Runtime Architecture

### Dependency injection

Koin is the composition root.

- Shared registrations: [`composeApp/src/commonMain/kotlin/it/homebudget/app/di/Koin.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/di/Koin.kt)
- Android registrations: [`composeApp/src/androidMain/kotlin/it/homebudget/app/di/Koin.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/di/Koin.android.kt)
- iOS registrations: [`composeApp/src/iosMain/kotlin/it/homebudget/app/di/Koin.ios.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/di/Koin.ios.kt)

The shared graph centers on:

- `DatabaseBuilderFactory`
- `HomeBudgetDatabase`
- `ExpenseRepository`

Screens and bridge objects resolve these dependencies directly from Koin.

### Persistence

Persistence uses Room KMP on top of SQLite.

- Database definition: [`HomeBudgetDatabase.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/database/HomeBudgetDatabase.kt)
- Entities and DAOs: [`composeApp/src/commonMain/kotlin/it/homebudget/app/database`](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- Database builders:
  - expect: [`DatabaseBuilderFactory.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.kt)
  - Android actual: [`DatabaseBuilderFactory.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.android.kt)
  - iOS actual: [`DatabaseBuilderFactory.ios.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.ios.kt)
- Room schemas: [`composeApp/schemas`](./composeApp/schemas)

The main application boundary is [`ExpenseRepository.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/ExpenseRepository.kt). It handles:

- expenses, incomes, and categories
- recurring-series update and delete rules
- default-category seeding
- query flows used directly by screens and iOS bridges

### Shared Compose application

The shared entry point is [`App.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt). It applies the shared theme and starts a Voyager navigator.

The dashboard is already broken into smaller files under [`ui/screens/dashboard`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/dashboard), including:

- route and state wiring
- scaffold and top-level actions
- summary cards
- category breakdown
- cash-flow chart and chart models

Most Android UI and part of the iOS UI use these shared screens directly.

## Android UI

Android uses the shared Compose screens as its main UI surface.

Platform-specific additions still exist where they need Android APIs:

- native Material date picker in [`PlatformDatePicker.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/PlatformDatePicker.android.kt)
- file import/export launchers in `CsvImportLauncher.android.kt` and `CsvExportLauncher.android.kt`
- backup and Drive-specific drawer UI in [`PlatformCloudBackupDrawerSection.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/PlatformCloudBackupDrawerSection.android.kt)
- voice input implementation in the `AndroidVoiceExpense*.android.kt` files under [`androidMain/ui/screens`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens)
- a native RecyclerView host for the categories list through [`AndroidRecyclerViewHosts.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/AndroidRecyclerViewHosts.android.kt)

Navigation inside the shared Android UI is handled by Voyager.

## iOS UI

The iOS app uses SwiftUI for the top-level shell and mixes hosted Compose screens with native SwiftUI screens.

- Root navigation lives in [`iosApp/iosApp/App/ContentView.swift`](./iosApp/iosApp/App/ContentView.swift)
- Shared Kotlin screens are exposed through [`MainViewController.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt)
- Shared screens are hosted in SwiftUI through [`HostingSupport.swift`](./iosApp/iosApp/UI/HostingSupport.swift)

At the moment, the split looks like this:

- Shared Compose on iOS:
  - dashboard content
  - add transaction
  - add income
  - categories management
  - several shared expense/income screens still hosted through SwiftUI wrappers
- Native SwiftUI on iOS:
  - grouped monthly/day/shared/category expense lists
  - the expense edit/detail route
  - voice expense flow
  - CSV import/export sheets
  - Liquid Glass date-picker UI

The expense edit/detail route is now a native SwiftUI screen backed by Kotlin bridge logic:

- SwiftUI screen pieces: [`iosApp/iosApp/Features/Expenses`](./iosApp/iosApp/Features/Expenses)
- Kotlin bridge: [`IosExpenseNativeEditorBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosExpenseNativeEditorBridge.kt)

The categories route currently goes the other direction: SwiftUI owns navigation to that route, but the screen itself is the shared Compose `CategoriesScreen` hosted from [`CategoriesRootView`](./iosApp/iosApp/Features/Expenses/ComposeHostedScreens.swift).

That route is now presented to users as `Custom Categories` and only manages user-defined categories. Default categories remain part of the expense picker, but they are not edited or deleted from the management screen.

## Navigation

Navigation is platform-specific.

### Android and shared Compose

Voyager drives navigation inside the shared Compose app. Screens push and pop other screens through the Voyager navigator.

### iOS

SwiftUI owns the top-level `NavigationStack` with a local `Route` enum in [`ContentView.swift`](./iosApp/iosApp/App/ContentView.swift).

When the target route is implemented in shared Kotlin, SwiftUI creates a host view around the appropriate `ComposeUIViewController`.

When the target route is native, SwiftUI talks to a Kotlin bridge or controller object for data loading and mutations.

## Localization

Shared Kotlin strings use Compose Multiplatform resources:

- [`composeApp/src/commonMain/composeResources/values/strings.xml`](./composeApp/src/commonMain/composeResources/values/strings.xml)
- [`composeApp/src/commonMain/composeResources/values-it/strings.xml`](./composeApp/src/commonMain/composeResources/values-it/strings.xml)

Native iOS strings live in:

- [`iosApp/iosApp/Localizable.xcstrings`](./iosApp/iosApp/Localizable.xcstrings)

On the Kotlin side, strings are resolved with `stringResource(...)` plus helper functions from [`AppLocalization.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/localization/AppLocalization.kt).

Currency is localized through resources:

- English: `$`
- Italian: `€`

## Data flow and iOS bridges

The usual shared flow is:

1. resolve `ExpenseRepository` from Koin
2. subscribe to `Flow` from Room queries
3. map rows into screen-specific presentation models
4. render from those models

The iOS side adds bridge objects where SwiftUI needs a stable snapshot or callback-oriented API instead of raw Kotlin flows.

Examples:

- grouped expenses: [`IosGroupedExpensesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosGroupedExpensesBridge.kt)
- categories: [`IosCategoriesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosCategoriesBridge.kt)
- native expense editor: [`IosExpenseNativeEditorBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosExpenseNativeEditorBridge.kt)
- CSV import/export: [`IosCsvImportBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosCsvImportBridge.kt), [`IosCsvExportBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosCsvExportBridge.kt)
- backup restore/export: [`IosBackupRestoreBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosBackupRestoreBridge.kt), [`IosBackupExportBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosBackupExportBridge.kt)
- date picking: [`IosNativeDatePickerBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosNativeDatePickerBridge.kt)
- voice expense persistence/support: `IosVoiceExpense*.kt`

## Backup and data transfer

### Full cloud backup

Full backup is JSON-based and separate from CSV export.

Shared backup logic:

- format and file naming: [`BudgetBackup.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/BudgetBackup.kt)
- orchestration: [`CloudSyncService.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CloudSyncService.kt)

Android:

- canonical file: `files/Data/homebudget-backup.json`
- store implementation: [`AndroidCloudBackupStore.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/AndroidCloudBackupStore.android.kt)
- WorkManager scheduling and Drive sync support live in `androidMain/data`
- startup restore detection lives in [`AndroidStartupRestore.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/AndroidStartupRestore.android.kt)

iOS:

- canonical file: `Data/homebudget-backup.json` inside the app ubiquity container
- store implementation: [`ICloudBackupStore.swift`](./iosApp/iosApp/Sync/ICloudBackupStore.swift)
- background scheduling: [`CloudBackupBackgroundTasks.swift`](./iosApp/iosApp/Sync/CloudBackupBackgroundTasks.swift)
- startup restore confirmation: [`iOSApp.swift`](./iosApp/iosApp/App/iOSApp.swift)

On both platforms, restore requires explicit user confirmation when a backup is found and the local database is still empty.

### CSV import/export

CSV import/export is meant for selected data transfer, not full restore.

- shared import/export logic:
  - [`CsvBudgetImport.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CsvBudgetImport.kt)
  - [`CsvBudgetExport.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CsvBudgetExport.kt)
- Android file launchers:
  - [`CsvImportLauncher.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/CsvImportLauncher.android.kt)
  - [`CsvExportLauncher.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/CsvExportLauncher.android.kt)
- iOS sheet flow:
  - [`CsvSheets.swift`](./iosApp/iosApp/UI/CsvSheets.swift)
  - [`ContentView.swift`](./iosApp/iosApp/App/ContentView.swift)

CSV imports are capped at 5 MiB before parsing.

## Voice input

Voice input is platform-specific.

### Android

Android voice entry is implemented under [`composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens) in focused files for:

- models
- speech capture
- parsing
- persistence
- status/presentation formatting
- workflow orchestration

### iOS

iOS voice entry is mostly native SwiftUI and Swift-side logic under [`iosApp/iosApp/Features/VoiceExpense`](./iosApp/iosApp/Features/VoiceExpense), with Kotlin bridge support in `iosMain/ui/screens`.

## Important directories

- Shared app entry: [`composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt)
- Shared DI: [`composeApp/src/commonMain/kotlin/it/homebudget/app/di`](./composeApp/src/commonMain/kotlin/it/homebudget/app/di)
- Shared data layer: [`composeApp/src/commonMain/kotlin/it/homebudget/app/data`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data)
- Shared Room database: [`composeApp/src/commonMain/kotlin/it/homebudget/app/database`](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- Shared screens: [`composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens)
- Shared resources: [`composeApp/src/commonMain/composeResources`](./composeApp/src/commonMain/composeResources)
- Android-specific code: [`composeApp/src/androidMain/kotlin`](./composeApp/src/androidMain/kotlin)
- iOS Kotlin code and bridges: [`composeApp/src/iosMain/kotlin`](./composeApp/src/iosMain/kotlin)
- Native iOS app: [`iosApp/iosApp`](./iosApp/iosApp)

## Build and run

Current toolchain baseline:

- AGP `9.2.1`
- Kotlin `2.3.21`
- Android compile SDK `37`
- Android target SDK `37`
- Android min SDK `26`
- JDK `21` in [`gradle/gradle-daemon-jvm.properties`](./gradle/gradle-daemon-jvm.properties)

### Android

Build the debug APK:

```sh
./gradlew :androidApp:assembleDebug
```

Google Drive AppData sync also needs Android OAuth setup:

1. Create an Android OAuth client for the package name and signing SHA-1 you use.
2. If you want Credential Manager returning-user sign-in, also create a Web OAuth client.
3. Put the Web client ID into [`composeApp/src/androidMain/res/values/google_identity.xml`](./composeApp/src/androidMain/res/values/google_identity.xml).

Without that setup, local JSON backup and Android Auto Backup still work, but the custom Drive sync path cannot complete end to end.

### iOS

Open [`iosApp`](./iosApp) in Xcode and run the `iosApp` scheme, or build from the command line:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.3.1' build
```

## Notes

- SQLite is the storage engine on both platforms through Room KMP.
- Xcode builds always rebuild the Kotlin framework through the `ComposeApp` integration step.
- Default categories are seeded at runtime when the category table is empty.
- The source tree is organized more aggressively by feature now than it was originally, especially in `commonMain/ui/screens` and `iosApp/iosApp`, but the runtime model is still straightforward: shared data and rules, shared UI where practical, native UI where the platform-specific experience is worth it.
