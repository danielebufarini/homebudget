# HomeBudget

HomeBudget is a Kotlin Multiplatform personal finance app for Android and iOS.

- Android runs as a native app in `androidApp` and uses the shared Compose UI from `composeApp`.
- iOS runs as a native SwiftUI app in `iosApp` and embeds the shared Kotlin framework as `ComposeApp`.

Most domain logic, persistence, resources, and a large part of the UI live in shared Kotlin. Native code is used where the platform APIs or interaction model justify it.

## Features

- expense and income tracking
- recurring transactions
- category management, including archive and reassignment flows
- monthly and grouped transaction views
- CSV import and export
- full JSON backup and restore
- Android Google Drive backup
- iOS iCloud backup
- voice-assisted expense entry
- Android home screen widgets
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

Current toolchain baseline:

- JDK 21
- Kotlin 2.3.21
- AGP 9.2.1
- Android min SDK 26
- Android target/compile SDK 37

Versions are defined in [gradle/libs.versions.toml](./gradle/libs.versions.toml). The Gradle daemon toolchain is pinned in [gradle/gradle-daemon-jvm.properties](./gradle/gradle-daemon-jvm.properties).

## Project layout

### `composeApp`

Shared KMP module.

- `src/commonMain`
  - app entry point
  - shared Compose screens
  - repository and service layer
  - Room entities and DAOs
  - shared resources and localization
- `src/androidMain`
  - Android-specific DI
  - Drive backup integration
  - voice-entry implementation
  - platform file and date-picker integrations
- `src/iosMain`
  - `ComposeUIViewController` factories
  - iOS-specific DI
  - bridge classes used by SwiftUI

Key directories:

- [composeApp/src/commonMain/kotlin/it/homebudget/app/data](./composeApp/src/commonMain/kotlin/it/homebudget/app/data)
- [composeApp/src/commonMain/kotlin/it/homebudget/app/database](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- [composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens)
- [composeApp/src/commonMain/composeResources](./composeApp/src/commonMain/composeResources)
- [composeApp/schemas](./composeApp/schemas)

### `androidApp`

Android application module.

- launcher activity
- widget providers
- WorkManager scheduling
- Android manifest and resources

### `iosApp`

Native iOS application.

- SwiftUI app shell and navigation
- native iOS screens and utilities
- iCloud backup integration
- widget support

Key directory:

- [iosApp/iosApp](./iosApp/iosApp)

## Architecture

### Dependency injection

Koin is the composition root.

- shared graph: [composeApp/src/commonMain/kotlin/it/homebudget/app/di](./composeApp/src/commonMain/kotlin/it/homebudget/app/di)
- Android graph: [composeApp/src/androidMain/kotlin/it/homebudget/app/di](./composeApp/src/androidMain/kotlin/it/homebudget/app/di)
- iOS graph: [composeApp/src/iosMain/kotlin/it/homebudget/app/di](./composeApp/src/iosMain/kotlin/it/homebudget/app/di)

### Persistence

Persistence uses Room KMP on top of SQLite.

- database: [HomeBudgetDatabase.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/database/HomeBudgetDatabase.kt)
- entities and DAOs: [composeApp/src/commonMain/kotlin/it/homebudget/app/database](./composeApp/src/commonMain/kotlin/it/homebudget/app/database)
- database builders:
  - [DatabaseBuilderFactory.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.kt)
  - [DatabaseBuilderFactory.android.kt](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.android.kt)
  - [DatabaseBuilderFactory.ios.kt](./composeApp/src/iosMain/kotlin/it/homebudget/app/data/DatabaseBuilderFactory.ios.kt)

The main application boundary is [ExpenseRepository.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/ExpenseRepository.kt), backed by [CategoryRepository.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CategoryRepository.kt) and the other shared repositories and services in the same package.

Starter categories are seeded at runtime when the category table is empty.

### UI split

Android uses the shared Compose screens as its main UI surface.

iOS uses a mixed model:

- SwiftUI owns the app shell and top-level navigation
- shared Kotlin screens are hosted through `ComposeUIViewController`
- native SwiftUI is used for routes that depend on iOS-specific interaction or platform APIs

Entry points:

- shared app: [composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt)
- Android activity: [androidApp/src/main/kotlin/it/homebudget/app/MainActivity.kt](./androidApp/src/main/kotlin/it/homebudget/app/MainActivity.kt)
- iOS root view: [iosApp/iosApp/App/ContentView.swift](./iosApp/iosApp/App/ContentView.swift)
- iOS shared screen hosting: [composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt](./composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt)

### Navigation

- shared Compose navigation uses Voyager
- iOS top-level navigation uses SwiftUI `NavigationStack`

## Localization

Shared Kotlin strings live in:

- [composeApp/src/commonMain/composeResources/values/strings.xml](./composeApp/src/commonMain/composeResources/values/strings.xml)
- [composeApp/src/commonMain/composeResources/values-it/strings.xml](./composeApp/src/commonMain/composeResources/values-it/strings.xml)

Native iOS strings live in:

- [iosApp/iosApp/Localizable.xcstrings](./iosApp/iosApp/Localizable.xcstrings)

## Backup and data transfer

### Full backup

Full backup is JSON-based.

- shared format and restore logic: [BudgetBackup.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/BudgetBackup.kt)
- backup orchestration: [CloudSyncService.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/CloudSyncService.kt)

Android stores the canonical backup locally and can mirror it to Google Drive AppData.

- store: [AndroidCloudBackupStore.android.kt](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/AndroidCloudBackupStore.android.kt)
- Drive auth: [GoogleDriveAuthorizationManager.android.kt](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/GoogleDriveAuthorizationManager.android.kt)

iOS stores the canonical backup in the app ubiquity container.

- store: [ICloudBackupStore.swift](./iosApp/iosApp/Sync/ICloudBackupStore.swift)

### CSV

CSV import/export is separate from full backup.

- export: [CsvBudgetExport.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/csv/CsvBudgetExport.kt)
- import: [CsvBudgetImport.kt](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/csv/CsvBudgetImport.kt)

## Voice input

Voice entry is platform-specific.

- Android implementation lives under [composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens](./composeApp/src/androidMain/kotlin/it/homebudget/app/ui/screens)
- iOS implementation lives in [iosApp/iosApp/Features/VoiceExpense](./iosApp/iosApp/Features/VoiceExpense) with Kotlin bridge support in `iosMain`

## Build

### Android

Build the debug APK:

```sh
./gradlew :androidApp:assembleDebug
```

Compile only:

```sh
./gradlew :composeApp:compileAndroidMain
```

### iOS

Open [iosApp/iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) in Xcode and run the `iosApp` scheme.

Compile the shared iOS Kotlin target from Gradle:

```sh
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

## Setup notes

### Android Google Drive backup

Drive backup requires Android OAuth configuration.

1. Create an Android OAuth client for the package name and signing certificate you use.
2. If Credential Manager sign-in is enabled, create a Web OAuth client as well.
3. Put the Web client ID in [composeApp/src/androidMain/res/values/google_identity.xml](./composeApp/src/androidMain/res/values/google_identity.xml).

Without this setup, local backup still works and Android Auto Backup remains unaffected.

### iOS

The iOS target depends on the Xcode project configuration already present in `iosApp`, including iCloud-related entitlements.

## Verification

The shared codebase is routinely checked with:

```sh
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64
```
