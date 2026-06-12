import AppIntents

struct SpesifyAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddTransactionIntent(),
            phrases: [
                "Add a transaction in \(.applicationName)",
                "Add an expense in \(.applicationName)",
                "Register income in \(.applicationName)"
            ],
            shortTitle: "Add Transaction",
            systemImageName: "plus.circle"
        )
        AppShortcut(
            intent: CurrentMonthExpenseTotalIntent(),
            phrases: [
                "Show current month expenses in \(.applicationName)",
                "How much did I spend this month in \(.applicationName)"
            ],
            shortTitle: "Month Expenses",
            systemImageName: "chart.bar"
        )
        AppShortcut(
            intent: MonthExpenseTotalIntent(),
            phrases: [
                "Show monthly expenses in \(.applicationName)",
                "How much did I spend in a month in \(.applicationName)"
            ],
            shortTitle: "Expenses by Month",
            systemImageName: "calendar"
        )
        AppShortcut(
            intent: PeriodExpenseTotalIntent(),
            phrases: [
                "Show expenses for a period in \(.applicationName)",
                "How much did I spend between dates in \(.applicationName)"
            ],
            shortTitle: "Expenses by Period",
            systemImageName: "calendar.badge.clock"
        )
        AppShortcut(
            intent: CurrentMonthIncomeTotalIntent(),
            phrases: [
                "Show current month income in \(.applicationName)",
                "How much income this month in \(.applicationName)"
            ],
            shortTitle: "Month Income",
            systemImageName: "arrow.down.circle"
        )
        AppShortcut(
            intent: MonthIncomeTotalIntent(),
            phrases: [
                "Show monthly income in \(.applicationName)",
                "How much income in a month in \(.applicationName)"
            ],
            shortTitle: "Income by Month",
            systemImageName: "calendar"
        )
        AppShortcut(
            intent: PeriodIncomeTotalIntent(),
            phrases: [
                "Show income for a period in \(.applicationName)",
                "How much income between dates in \(.applicationName)"
            ],
            shortTitle: "Income by Period",
            systemImageName: "calendar.badge.clock"
        )
        AppShortcut(
            intent: CurrentBalanceIntent(),
            phrases: [
                "Show current balance in \(.applicationName)",
                "What is my balance in \(.applicationName)"
            ],
            shortTitle: "Current Balance",
            systemImageName: "sum"
        )
        AppShortcut(
            intent: ListCategoriesIntent(),
            phrases: [
                "List categories in \(.applicationName)",
                "Show categories in \(.applicationName)"
            ],
            shortTitle: "List Categories",
            systemImageName: "list.bullet"
        )
        AppShortcut(
            intent: AddCategoryIntent(),
            phrases: [
                "Add a category in \(.applicationName)",
                "Create a category in \(.applicationName)"
            ],
            shortTitle: "Add Category",
            systemImageName: "folder.badge.plus"
        )
    }
}
