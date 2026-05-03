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
  - SQLDelight schema under `sqldelight`
  - Compose Multiplatform string resources under `composeResources`
- `androidMain`
  - Android entry points (`MainActivity`, `Application`)
  - Android-specific DI bootstrap
  - Android-only integrations such as speech recognition and a few RecyclerView-backed list hosts
- `iosMain`
  - `ComposeUIViewController` factories used by the SwiftUI app
  - iOS-specific DI bootstrap
  - iOS bridge objects used by Swift code for grouped expenses, categories, CSV import/export, and voice entry

### `iosApp`

`iosApp` is the native iOS application target. It is responsible for:

- the SwiftUI root navigation stack
- file import/export UI
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

- `DatabaseDriverFactory`
- `HomeBudgetDatabase`
- `ExpenseRepository`

The rest of the application resolves dependencies directly from Koin inside screens and bridge controllers. There is no separate service layer.

### 2. Persistence

Persistence is built on SQLDelight with SQLite.

- Schema: `composeApp/src/commonMain/sqldelight`
- Driver factory:
  - shared expect declaration in [`DatabaseDriverFactory.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/data/DatabaseDriverFactory.kt)
  - Android actual in [`DatabaseDriverFactory.android.kt`](./composeApp/src/androidMain/kotlin/it/homebudget/app/data/DatabaseDriverFactory.android.kt)
  - iOS actual in [`DatabaseDriverFactory.ios.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/data/DatabaseDriverFactory.ios.kt)

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
- calendar
- grouped monthly/shared/category expense screens

The shared application entry point is [`App.kt`](./composeApp/src/commonMain/kotlin/it/homebudget/app/App.kt). It wraps the app in the shared theme and starts a Voyager `Navigator` with `DashboardScreen`.

#### Android-specific UI integrations

Android uses the shared Compose screens as the primary UI, but a few paths remain platform-specific:

- speech recognition and Gemini-related voice input
- RecyclerView-backed grouped/category lists where Compose is hosted inside row containers

Those Android-only pieces live under `androidMain/ui/screens`.

#### iOS mixed SwiftUI + Compose UI

iOS uses a mixed architecture:

- SwiftUI owns the top-level navigation stack in [`ContentView.swift`](./iosApp/iosApp/ContentView.swift)
- shared Compose screens are exposed through `ComposeUIViewController` factories in [`MainViewController.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/MainViewController.kt)
- some flows remain native SwiftUI, notably grouped expense/income lists, categories, and voice entry

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
2. subscribe to `Flow` from SQLDelight queries
3. transform database rows into UI-specific grouped or formatted structures
4. render directly from those structures

There is no Redux-style state container and no layered MVVM hierarchy. State is local to the screen unless it has to cross the Kotlin/Swift boundary.

Where data needs to cross into SwiftUI, Kotlin-side bridge classes expose stable snapshots rather than raw database flows. The grouped expenses path is the clearest example:

- Kotlin bridge: [`IosGroupedExpensesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosGroupedExpensesBridge.kt)
- SwiftUI consumer: [`MonthlyExpensesSectionsScreen.swift`](./iosApp/iosApp/MonthlyExpensesSectionsScreen.swift)

The categories path uses the same pattern:

- Kotlin bridge: [`IosCategoriesBridge.kt`](./composeApp/src/iosMain/kotlin/it/homebudget/app/ui/screens/IosCategoriesBridge.kt)
- SwiftUI consumer: [`CategoriesScreen.swift`](./iosApp/iosApp/CategoriesScreen.swift)

### 7. Platform Bridges

The iOS target contains several bridge layers that convert shared Kotlin behavior into Swift-friendly APIs.

Examples:

- grouped expenses snapshots
- categories snapshots and mutations
- CSV import/export controllers
- voice-entry persistence and lookup support
- deletion flows for expenses/incomes

These classes sit in `composeApp/src/iosMain/kotlin/.../ui/screens` and exist specifically to keep Swift code away from low-level SQLDelight or repository details.

### 8. Voice Input

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
- Shared screens: [`composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens`](./composeApp/src/commonMain/kotlin/it/homebudget/app/ui/screens)
- SQLDelight schema: [`composeApp/src/commonMain/sqldelight`](./composeApp/src/commonMain/sqldelight)
- Shared resources: [`composeApp/src/commonMain/composeResources`](./composeApp/src/commonMain/composeResources)
- Android-specific code: [`composeApp/src/androidMain/kotlin`](./composeApp/src/androidMain/kotlin)
- iOS Kotlin bridges: [`composeApp/src/iosMain/kotlin`](./composeApp/src/iosMain/kotlin)
- iOS SwiftUI app: [`iosApp/iosApp`](./iosApp/iosApp)
- Native iOS categories screen: [`iosApp/iosApp/CategoriesScreen.swift`](./iosApp/iosApp/CategoriesScreen.swift)

## Build and Run

### Android

Build the debug APK:

```sh
./gradlew :composeApp:assembleDebug
```

### iOS

Open [`iosApp`](./iosApp) in Xcode and run the `iosApp` scheme, or build from the command line:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' build
```

## Notes

- The app is backed by SQLite on both platforms through SQLDelight.
- The iOS target always rebuilds the Kotlin framework during Xcode builds.
- Default categories are seeded at runtime if the category table is empty.
- The codebase is intentionally pragmatic: shared where it reduces real cost, native where platform APIs, visual language, or host navigation make that simpler.
