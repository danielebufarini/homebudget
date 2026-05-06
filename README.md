# HomeBudget

HomeBudget is a Kotlin Multiplatform application for personal finance tracking. The codebase targets:

- Android, with a Compose UI entry point in `composeApp`
- iOS, with a SwiftUI host app in `iosApp` and a shared Kotlin framework embedded as `ComposeApp`

The project is not structured as a generic “shared everything” sample. Most business logic, persistence, localization, and a large part of the UI live in shared Kotlin code, while iOS keeps a native SwiftUI shell and selectively replaces some screens with native SwiftUI when the platform-specific UX is worth the extra surface area.

## Module Layout

### `composeApp`

`composeApp` is the shared Kotlin Multiplatform module. It contains:

- `commonMain`
  - application entry point for shared Compose UI
  - domain and persistence code
  - navigation screens built with Voyager
  - theme, shared UI components, and localization helpers
  - Room entities, DAOs, and schema export
  - Compose Multiplatform string resources under `composeResources`
- `androidMain`
  - Android entry points (`MainActivity`, `Application`)
  - Android-specific DI bootstrap
  - Android-only integrations such as speech recognition, WorkManager backup scheduling, Google Drive AppData sync, and startup-restore detection
- `iosMain`
  - `ComposeUIViewController` factories used by the SwiftUI app
  - iOS-specific DI bootstrap
  - iOS bridge objects used by Swift code for grouped expenses, categories, CSV import/export, backup preview/restore, and voice entry

### `iosApp`

`iosApp` is the native iOS application target. It is responsible for:

- the SwiftUI root navigation stack
- CSV file import/export UI
- iCloud background backup scheduling and startup restore confirmation
- some native list, category-management, and voice-entry flows
- hosting shared Compose screens inside `UIViewControllerRepresentable`

The iOS app is not a thin launcher. It decides when to push a Compose-backed screen and when to stay fully native.

## Architectural Overview

### 1. Dependency Injection

Koin is used as the runtime composition root.

- Shared registrations live in [`composeApp/src/commonMain/kotlin/it/homebudget/app/di/Koin.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/di/Koin.kt)
- Platform-specific registrations live in:
  - [`androidMain/.../di/Koin.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/di/Koin.android.kt)
  - [`iosMain/.../di/Koin.ios.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/di/Koin.ios.kt)

The shared module registers:

- `DatabaseBuilderFactory`
- `HomeBudgetDatabase`
- `ExpenseRepository`

The rest of the application resolves dependencies directly from Koin inside screens and bridge controllers. There is no separate service layer.

### 2. Persistence

Persistence is built on Room KMP with SQLite.

- Shared database definition: [`HomeBudgetDatabase.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/database/HomeBudgetDatabase.kt)
- Shared entities and DAOs: [`composeApp/src/commonMain/kotlin/it/homebudget/app/database`](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- Platform-specific builders:
  - shared expect declaration in [`DatabaseBuilderFactory.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.kt)
  - Android actual in [`DatabaseBuilderFactory.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.android.kt)
  - iOS actual in [`DatabaseBuilderFactory.ios.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.ios.kt)
- Exported Room schemas: [`composeApp/schemas`](./composeApp/schemas)

The Room database file is `homebudget-room.db` on both platforms.

The repository is intentionally simple. [`ExpenseRepository.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/ExpenseRepository.kt) owns:

- CRUD for expenses, incomes, and categories
- recurring-series update/delete operations
- database seeding for default categories
- read models exposed as `Flow<List<...>>`

The repository is the main application boundary. Screens and bridge objects talk to it directly rather than through view models.

### 3. UI Composition

There are three distinct UI layers in this codebase.

#### Shared Compose screens

Most screens are implemented in shared Kotlin under `commonMain/ui/screens`, for example:

- dashboard
- add/edit expense
- add/edit income
- grouped monthly/shared/category expense screens

The shared application entry point is [`App.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt). It wraps the app in the shared theme and starts a Voyager `Navigator` with `DashboardScreen`.

#### Android-specific UI integrations

Android uses the shared Compose screens as the primary UI, but a few paths remain platform-specific:

- speech recognition and Gemini-related voice input
- cloud-backup scheduling and restore confirmation before the shared navigator starts
- a native RecyclerView host for the categories screen

Those Android-only pieces live under `androidMain/ui/screens`.

#### iOS mixed SwiftUI + Compose UI

iOS uses a mixed architecture:

- SwiftUI owns the top-level navigation stack in [`ContentView.swift`](./iosApp/iosApp/ContentView.swift)
- shared Compose screens are exposed through `ComposeUIViewController` factories in [`MainViewController.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt)
- some flows remain native SwiftUI, notably grouped expense/income lists, categories, and voice entry

The grouped expense lists on iOS now cover:

- monthly expenses
- shared expenses
- category-filtered monthly expenses
- the dashboard `Highest Day` route, which reuses the same native list and is locked to category grouping for that single-day view

The categories route is the clearest example of the "native interface, shared logic" approach:

- SwiftUI screen: [`CategoriesScreen.swift`](./iosApp/iosApp/CategoriesScreen.swift)
- Kotlin bridge: [`IosCategoriesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosCategoriesBridge.kt)

The iOS UI is native, but data loading, default-category seeding, insert/update/delete operations, and built-in category name resolution still live in shared Kotlin.

This split is deliberate. Shared Kotlin owns the business rules and most screen logic; SwiftUI is used where native navigation or native platform APIs are more practical.

### 4. Navigation

Navigation is platform-dependent.

#### Android / shared Compose navigation

Voyager is used inside the shared Compose app. Shared screens push and pop other shared screens through the Voyager navigator.

#### iOS navigation

SwiftUI owns navigation through a `NavigationStack` and a local `Route` enum in [`ContentView.swift`](./iosApp/iosApp/ContentView.swift).

When a route needs a shared screen, SwiftUI creates a host view that wraps the corresponding `ComposeUIViewController`.

When a route has been promoted to native iOS UI, SwiftUI talks to a small Kotlin bridge instead of hosting a Compose controller. The categories screen currently uses this pattern.

The `Highest Day` dashboard card also follows the native-route pattern on iOS: SwiftUI pushes a native grouped-expenses screen for the selected day instead of a Compose day-details screen.

### 5. Localization

Localization is resource-based, not object-based.

- Shared Kotlin strings use Compose Multiplatform resources:
  - `composeApp/src/commonMain/composeResources/values/strings.xml`
  - `composeApp/src/commonMain/composeResources/values-it/strings.xml`
- iOS native strings use:
  - [`iosApp/iosApp/Localizable.xcstrings`](./iosApp/iosApp/Localizable.xcstrings)

The shared Kotlin side resolves strings through `stringResource(...)` and narrow helper functions in [`AppLocalization.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/localization/AppLocalization.kt).

Currency handling is also localized:

- English uses `$`
- Italian uses `€`

### 6. Data and Presentation Flow

The common pattern across the app is:

1. resolve `ExpenseRepository` from Koin
2. subscribe to `Flow` from Room DAO queries
3. transform database rows into UI-specific grouped or formatted structures
4. render directly from those structures

There is no Redux-style state container and no layered MVVM hierarchy. State is local to the screen unless it has to cross the Kotlin/Swift boundary.

Where data needs to cross into SwiftUI, Kotlin-side bridge classes expose stable snapshots rather than raw database flows. The grouped expenses path is the clearest example:

- Kotlin bridge: [`IosGroupedExpensesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosGroupedExpensesBridge.kt)
- SwiftUI consumer: [`MonthlyExpensesSectionsScreen.swift`](./iosApp/iosApp/MonthlyExpensesSectionsScreen.swift)

That same bridge now supports day-of-month filtering so the iOS `Highest Day` destination can reuse the native monthly expenses list implementation.

The categories path uses the same pattern:

- Kotlin bridge: [`IosCategoriesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosCategoriesBridge.kt)
- SwiftUI consumer: [`CategoriesScreen.swift`](./iosApp/iosApp/CategoriesScreen.swift)

### 7. Platform Bridges

The iOS target contains several bridge layers that convert shared Kotlin behavior into Swift-friendly APIs.

Examples:

- grouped expenses snapshots
- categories snapshots and mutations
- CSV import/export controllers
- backup preview/restore controllers
- voice-entry persistence and lookup support
- deletion flows for expenses/incomes

These classes sit in `composeApp/src/iosMain/kotlin/.../ui/screens` and exist specifically to keep Swift code away from low-level Room or repository details.

### 8. Data Transfer and Backup

The app currently exposes one explicit data-transfer path in the UI:

- `CSV Import / Export`

Full cloud backup is no longer a manual backup action. It runs silently in the background once enabled, and restore is gated by a startup confirmation dialog when a backup is found.

#### Full Cloud Backup

Full cloud backup is complete-database transfer, not a CSV export. The shared flow is:

1. build a canonical JSON snapshot from shared Kotlin
2. write it atomically to platform storage
3. let the platform cloud mechanism sync that file
4. on first launch after reinstall or device migration, detect the file and ask the user before restoring

The shared pieces are:

- backup format and file name in [`BudgetBackup.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/BudgetBackup.kt)
- orchestration in [`CloudSyncService.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CloudSyncService.kt)

Android implementation:

- the canonical backup file is stored under `files/Data/homebudget-backup.json`
- writes are atomic in [`AndroidCloudBackupStore.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/AndroidCloudBackupStore.android.kt)
- periodic and bootstrap sync are scheduled through WorkManager in [`CloudBackupWorkScheduler.kt`](./androidApp/src/main/kotlin/it/homebudget/app/CloudBackupWorkScheduler.kt) and [`CloudBackupWorker.kt`](./androidApp/src/main/kotlin/it/homebudget/app/CloudBackupWorker.kt)
- Android Auto Backup includes only the JSON backup artifact and excludes redundant SQLite journals:
  - [`androidApp/src/main/AndroidManifest.xml`](./androidApp/src/main/AndroidManifest.xml)
  - [`androidApp/src/main/res/xml/data_extraction_rules.xml`](./androidApp/src/main/res/xml/data_extraction_rules.xml)
  - [`androidApp/src/main/res/xml/backup_rules.xml`](./androidApp/src/main/res/xml/backup_rules.xml)
- Google Drive AppData mirroring is handled by:
  - [`GoogleDriveAuthorizationManager.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/GoogleDriveAuthorizationManager.android.kt)
  - [`GoogleDriveAppDataStore.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/GoogleDriveAppDataStore.android.kt)
- the app checks authorization silently in the background and only syncs to Drive when an access token is already available
- if Drive has not been authorized yet, Android exposes a `Full Cloud Backup` toggle directly in the navigation drawer, with the backup description and note rendered underneath in small text; toggling it on starts the explicit Google consent flow on demand:
  - shared drawer hook in [`AndroidNavigationRail.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/AndroidNavigationRail.kt)
  - Android drawer section in [`PlatformCloudBackupDrawerSection.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/PlatformCloudBackupDrawerSection.android.kt)
- the optional Credential Manager returning-user path is enabled only when `google_web_client_id` is configured in [`composeApp/src/androidMain/res/values/google_identity.xml`](./composeApp/src/androidMain/res/values/google_identity.xml)
- startup restore detection lives in [`AndroidStartupRestore.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/AndroidStartupRestore.android.kt) and the confirmation dialog is rendered by the shared Compose entry point in [`App.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt)

iOS implementation:

- the canonical backup file is stored in the app's iCloud ubiquity container at `Data/homebudget-backup.json`
- file IO is handled in [`ICloudBackupStore.swift`](./iosApp/iosApp/ICloudBackupStore.swift)
- background backup scheduling uses BGTaskScheduler in [`CloudBackupBackgroundTasks.swift`](./iosApp/iosApp/CloudBackupBackgroundTasks.swift)
- startup restore confirmation is handled in [`iOSApp.swift`](./iosApp/iosApp/iOSApp.swift)

On both platforms, restore is no longer automatic. If the local database is still empty and a cloud backup file is present, the app asks the user for permission before importing it.

Android caveats:

- local JSON backup plus Android Auto Backup work without any Google OAuth setup
- custom Drive AppData sync requires Google OAuth client configuration for the app's package name and SHA-1
- the current repository does not implement true Android 16 Restore Credentials account-link restore, because that flow needs an app-account/backend integration in addition to Google client setup

#### CSV Import / Export

CSV import/export is file-based transfer for selected data ranges. It remains separate from full cloud backup.

- shared CSV import/export logic:
  - [`CsvBudgetImport.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CsvBudgetImport.kt)
  - [`CsvBudgetExport.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CsvBudgetExport.kt)
- Android launchers and transfer UI:
  - [`CsvImportLauncher.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/CsvImportLauncher.android.kt)
  - [`CsvExportLauncher.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens/CsvExportLauncher.android.kt)
  - [`AndroidDataTransferUi.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens/AndroidDataTransferUi.kt)
- iOS shell flow:
  - [`ContentView.swift`](./iosApp/iosApp/ContentView.swift)

### 9. Voice Input

Voice input is platform-specific by design.

#### Android

Android voice input is split into focused files under `androidMain/ui/screens`:

- models
- speech session handling
- parsing
- persistence
- presentation/status formatting
- orchestration workflows

This path uses Android speech recognition and can fall back to a local parser when Gemini is unavailable.

#### iOS

iOS voice input is split between:

- Swift speech capture and parsing support in `iosApp`
- Kotlin bridge/persistence helpers in `iosMain`

The iOS implementation is intentionally more native because the recording and UX flow are SwiftUI-driven.

## Important Source Directories

- Shared app entry: [`composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt)
- Shared DI: [`composeApp/src/commonMain/kotlin/it/homebudget/app/di`](./composeApp/src/commonMain/kotlin/it/homebudget/app/di)
- Shared data layer: [`composeApp/src/commonMain/kotlin/it/homebudget/app/data`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data)
- Shared Room database: [`composeApp/src/commonMain/kotlin/it/homebudget/app/database`](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- Shared screens: [`composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens)
- Shared resources: [`composeApp/src/commonMain/composeResources`](./composeApp/src/commonMain/composeResources)
- Android-specific code: [`composeApp/src/androidMain/kotlin`](./composeApp/src/androidMain/kotlin)
- iOS Kotlin bridges: [`composeApp/src/iosMain/kotlin`](./composeApp/src/iosMain/kotlin)
- iOS SwiftUI app: [`iosApp/iosApp`](./iosApp/iosApp)
- Native iOS categories screen: [`iosApp/iosApp/CategoriesScreen.swift`](./iosApp/iosApp/CategoriesScreen.swift)

## Build and Run

The current toolchain baseline in the repository is:

- AGP `9.2.1`
- Kotlin `2.3.21`
- Android compile/target SDK `36`
- JDK `21` pinned in [`gradle/gradle-daemon-jvm.properties`](./gradle/gradle-daemon-jvm.properties)

### Android

Build the debug APK:

```sh
./gradlew :androidApp:assembleDebug
```

Google Drive AppData sync also needs Android-specific Google OAuth configuration:

1. Create an Android OAuth client for the app package and signing SHA-1 used by the build you install.
2. If you want Credential Manager returning-user sign-in, also create a Web OAuth client.
3. Put that Web client ID into [`composeApp/src/androidMain/res/values/google_identity.xml`](./composeApp/src/androidMain/res/values/google_identity.xml).

Without that setup, the app still keeps the canonical JSON backup locally and through Android Auto Backup, but the custom Drive consent toggle cannot complete end-to-end.

### iOS

Open [`iosApp`](./iosApp) in Xcode and run the `iosApp` scheme, or build from the command line:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.3.1' build
```

## Notes

- The app is backed by SQLite on both platforms through Room KMP.
- The iOS target always rebuilds the Kotlin framework during Xcode builds.
- Default categories are seeded at runtime if the category table is empty.
- The codebase is intentionally pragmatic: shared where it reduces real cost, native where platform APIs, visual language, or host navigation make that simpler.
