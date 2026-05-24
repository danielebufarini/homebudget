# HomeBudget

HomeBudget is a Kotlin Multiplatform personal finance app for Android and iOS.

- `composeApp` contains the shared domain layer, persistence, resources, and most UI.
- `androidApp` is the Android host app.
- `iosApp` is the SwiftUI iOS app shell plus widgets and iCloud integration.

The shared iOS framework is built as `ComposeApp.framework` and embedded by the Xcode project.

## Feature set

- expense and income tracking
- recurring expenses and recurring incomes
- category management with archive, reassignment, color, and per-type support
- monthly transaction views with expense/income switching and grouping
- grouped views by category or by date
- CSV import/export
- JSON backup/restore
- Google Drive AppData backup on Android
- iCloud backup on iOS
- voice-assisted expense entry
- Android and iOS home screen widgets
- English and Italian localization

## Stack

- Kotlin Multiplatform
- Compose Multiplatform
- SwiftUI
- Room KMP with bundled SQLite
- Koin
- Voyager
- kotlinx.serialization
- kotlinx.datetime

Current baseline:

- JDK 21
- Kotlin 2.3.21
- Compose Multiplatform 1.10.3
- AGP 9.2.1
- Android min SDK 26
- Android target/compile SDK 37

Versions are defined in [gradle/libs.versions.toml](./gradle/libs.versions.toml).

## Project layout

### `composeApp`

Shared KMP module.

- `src/commonMain`
  - app entry point
  - shared Compose screens
  - repositories and services
  - Room entities and DAOs
  - Compose resources and shared localization
- `src/androidMain`
  - Android DI
  - Drive backup integration
  - Android voice-entry implementation
  - Android-specific platform bridges
- `src/iosMain`
  - `ComposeUIViewController` factories
  - iOS DI
  - bridge classes used by SwiftUI hosts

Relevant directories:

- [composeApp/src/commonMain/kotlin/it/homebudget/app/data](./composeApp/src/commonMain/kotlin/it/homebudget/app/data)
- [composeApp/src/commonMain/kotlin/it/homebudget/app/database](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- [composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens)
- [composeApp/src/commonMain/composeResources](./composeApp/src/commonMain/composeResources)
- [composeApp/schemas](./composeApp/schemas)

### `androidApp`

Android application module.

- launcher activity
- manifest and resources
- widget providers
- WorkManager integration

### `iosApp`

Native iOS application.

- SwiftUI app shell and navigation
- hosted Compose screens
- native SwiftUI screens where platform-native UX is preferable
- iCloud sync helpers
- widget extension

Relevant directories:

- [iosApp/iosApp](./iosApp/iosApp)
- [iosApp/HomeBudgetWidget](./iosApp/HomeBudgetWidget)

Important iOS feature areas:

- [iosApp/iosApp/App](./iosApp/iosApp/App): app entry point, navigation, localization helpers, and shared shell concerns.
- [iosApp/iosApp/Features/Expenses](./iosApp/iosApp/Features/Expenses): native SwiftUI transaction, grouped expense, income, and search screens.
- [iosApp/iosApp/Features/VoiceExpense](./iosApp/iosApp/Features/VoiceExpense): native voice expense flow.
- [iosApp/iosApp/Sync](./iosApp/iosApp/Sync): iCloud backup and widget summary stores.
- [iosApp/iosApp/UI](./iosApp/iosApp/UI): shared SwiftUI glass surfaces, hosting helpers, and reusable controls.

## Architecture

### Persistence

Persistence is built on Room KMP over bundled SQLite.

- database: [HomeBudgetDatabase.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/database/HomeBudgetDatabase.kt)
- entities and DAOs: [composeApp/src/commonMain/kotlin/it/homebudget/app/database](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- database builders:
  - [DatabaseBuilderFactory.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.kt)
  - [DatabaseBuilderFactory.android.kt](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.android.kt)
  - [DatabaseBuilderFactory.ios.kt](./composeApp/src/iosMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.ios.kt)

The main application boundary is [ExpenseRepository.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/ExpenseRepository.kt), with category-specific operations in [CategoryRepository.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CategoryRepository.kt).

Categories are typed (`expense` / `income`), can be archived, and are seeded when the category table is empty.

### Dependency injection

Koin is the composition root.

- shared graph: [composeApp/src/commonMain/kotlin/it/homebudget/app/di](./composeApp/src/commonMain/kotlin/it/homebudget/app/di)
- Android graph: [composeApp/src/androidMain/kotlin/it/homebudget/app/di](./composeApp/src/androidMain/kotlin/it/homebudget/app/di)
- iOS graph: [composeApp/src/iosMain/kotlin/it/homebudget/app/di](./composeApp/src/iosMain/kotlin/it/homebudget/app/di)

### UI split

Android runs primarily on shared Compose screens.

iOS uses a mixed model:

- SwiftUI owns the root shell and top-level navigation
- Compose screens are hosted through `ComposeUIViewController`
- native SwiftUI remains in place for iOS-specific flows and presentation

Entry points:

- shared app: [composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt)
- Android host: [androidApp/src/main/kotlin/it/homebudget/app/MainActivity.kt](./androidApp/src/main/kotlin/it/homebudget/app/MainActivity.kt)
- iOS root view: [iosApp/iosApp/App/ContentView.swift](./iosApp/iosApp/App/ContentView.swift)
- iOS shared screen host: [composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt](./composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt)

Native iOS expense screens are split by responsibility:

- [MonthlyExpensesSectionsScreen.swift](./iosApp/iosApp/Features/Expenses/MonthlyExpensesSectionsScreen.swift): orchestration for grouped expense routes and monthly expense/income switching.
- [GroupedExpensesSectionsList.swift](./iosApp/iosApp/Features/Expenses/GroupedExpensesSectionsList.swift): reusable grouped expense list rendering.
- [MonthlyIncomesSectionsScreen.swift](./iosApp/iosApp/Features/Expenses/MonthlyIncomesSectionsScreen.swift): monthly income list rendering.
- [MonthlyExpenseSectionViewModels.swift](./iosApp/iosApp/Features/Expenses/MonthlyExpenseSectionViewModels.swift): observer-backed SwiftUI view models for grouped expenses and incomes.
- [MonthNavigationSupport.swift](./iosApp/iosApp/Features/Expenses/MonthNavigationSupport.swift): month cursor, swipe navigation, and month header support.
- [MonthlyExpenseControls.swift](./iosApp/iosApp/Features/Expenses/MonthlyExpenseControls.swift): expense/income and grouping glass controls.
- [GroupedExpenseRows.swift](./iosApp/iosApp/Features/Expenses/GroupedExpenseRows.swift): grouped section headers, rows, category icons, and recurring badges.
- [TransactionSearchSectionsScreen.swift](./iosApp/iosApp/Features/Expenses/TransactionSearchSectionsScreen.swift): native full-text search results for expenses and income.

The iOS Xcode project uses file-system synchronized groups, so new Swift files under `iosApp/iosApp` are picked up by the app target without manual `project.pbxproj` edits.

### Navigation

- shared Compose navigation uses Voyager
- iOS top-level navigation uses SwiftUI `NavigationStack`

### Design patterns

The codebase uses classic GoF patterns where they reduce coupling without obscuring platform idioms:

- Strategy: [GroupedSectionExpansionStrategy.swift](./iosApp/iosApp/Features/Expenses/GroupedSectionExpansionStrategy.swift) encapsulates section expansion behavior shared by expense and income lists.
- Factory: `CategoryIconSymbolFactory` in [GroupedExpenseRows.swift](./iosApp/iosApp/Features/Expenses/GroupedExpenseRows.swift) centralizes the mapping from persisted category icon keys to SF Symbols.
- Bridge: iOS-specific Kotlin bridge classes under [composeApp/src/iosMain](./composeApp/src/iosMain) expose shared data snapshots to native SwiftUI screens.
- Repository: shared data access is concentrated in [ExpenseRepository.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/ExpenseRepository.kt) and [CategoryRepository.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CategoryRepository.kt).

## Localization

Shared strings:

- [composeApp/src/commonMain/composeResources/values/strings.xml](./composeApp/src/commonMain/composeResources/values/strings.xml)
- [composeApp/src/commonMain/composeResources/values-it/strings.xml](./composeApp/src/commonMain/composeResources/values-it/strings.xml)

Native iOS strings:

- [iosApp/iosApp/Localizable.xcstrings](./iosApp/iosApp/Localizable.xcstrings)

## Backup and transfer

### JSON backup

Full backup is JSON-based.

- format and restore logic: [BudgetBackup.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/BudgetBackup.kt)
- orchestration: [CloudSyncService.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CloudSyncService.kt)

Android stores the canonical backup locally and can mirror it to Google Drive AppData.

- [AndroidCloudBackupStore.android.kt](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/AndroidCloudBackupStore.android.kt)
- [GoogleDriveAuthorizationManager.android.kt](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/GoogleDriveAuthorizationManager.android.kt)

iOS stores the canonical backup in the app ubiquity container.

- [ICloudBackupStore.swift](./iosApp/iosApp/Sync/ICloudBackupStore.swift)

### CSV

CSV import/export is separate from full backup.

- [CsvBudgetExport.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/csv/CsvBudgetExport.kt)
- [CsvBudgetImport.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/csv/CsvBudgetImport.kt)

## Voice entry

Voice entry is platform-specific.

- Android implementation lives under [composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens)
- iOS implementation lives under [iosApp/iosApp/Features/VoiceExpense](./iosApp/iosApp/Features/VoiceExpense)
- shared prompt and contract helpers live in [VoiceExpensePrompt.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/VoiceExpensePrompt.kt)

## Build

### Android

Build the debug APK:

```sh
./gradlew :androidApp:assembleDebug
```

Compile shared Android code only:

```sh
./gradlew :composeApp:compileAndroidMain
```

### iOS

Open [iosApp/iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) in Xcode and run the `iosApp` scheme.

Compile the shared iOS target from Gradle:

```sh
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Build the iOS app from the command line:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

## Setup notes

### Android Google Drive backup

Drive backup requires Android OAuth configuration.

1. Create an Android OAuth client for the package name and signing certificate in use.
2. If Credential Manager sign-in is enabled, create a Web OAuth client as well.
3. Put the Web client ID in [composeApp/src/androidMain/res/values/google_identity.xml](./composeApp/src/androidMain/res/values/google_identity.xml).

Without this setup, local backup still works.

### iOS

The iOS target depends on the Xcode project configuration already present in `iosApp`, including iCloud and widget entitlements.

## Verification

Common verification commands:

```sh
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64
./gradlew :androidApp:assembleDebug
```
