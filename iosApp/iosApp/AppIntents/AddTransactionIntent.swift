import AppIntents
@preconcurrency import ComposeApp
import Foundation

enum SpesifyIntentTransactionKind: String, AppEnum {
    case expense
    case income

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Transaction Type")

    static let caseDisplayRepresentations: [SpesifyIntentTransactionKind: DisplayRepresentation] = [
        .expense: DisplayRepresentation(title: "Expense"),
        .income: DisplayRepresentation(title: "Income")
    ]
}

private extension SpesifyIntentTransactionKind {
    var sharedKind: TransactionKind {
        switch self {
        case .expense:
            return .expense
        case .income:
            return .income
        }
    }
}

struct AddTransactionIntent: AppIntent {
    static let title: LocalizedStringResource = "Add Transaction"
    static let description = IntentDescription("Adds a single non-recurring expense or income to Spesify.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Type",
        description: "Choose whether the transaction is an expense or income.",
        requestValueDialog: IntentDialog(stringLiteral: "Is this an expense or income?")
    )
    var kind: SpesifyIntentTransactionKind

    @Parameter(
        title: "Amount",
        description: "Standard currency amount. For example, use $55.56 or 55.56.",
        requestValueDialog: IntentDialog(stringLiteral: "What is the amount?")
    )
    var amount: String

    @Parameter(
        title: "Category",
        description: "Category name or category ID.",
        requestValueDialog: IntentDialog(stringLiteral: "Which category should I use?")
    )
    var categoryName: String

    @Parameter(title: "Description", description: "Optional note to save with the transaction.")
    var transactionDescription: String?

    @Parameter(title: "Date", description: "Optional transaction date. Today is used when omitted.")
    var date: Date?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$kind) of \(\.$amount) in \(\.$categoryName)") {
            \.$transactionDescription
            \.$date
        }
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let normalizedCategoryName = categoryName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty

        guard let normalizedCategoryName else {
            return .result(dialog: IntentDialog(stringLiteral: "Please specify a category before adding the transaction."))
        }

        guard let amountMinorUnits = AmountParser.minorUnits(from: amount) else {
            return .result(dialog: IntentDialog(stringLiteral: "Please enter a valid amount, for example $55.56 or 55.56."))
        }

        let controller = IosAddTransactionIntentController()
        let dateMillis = date.map { $0.epochMilliseconds } ?? 0
        let result = try await controller.addTransaction(
            kind: kind.sharedKind,
            amount: amountMinorUnits,
            categoryName: normalizedCategoryName,
            description: transactionDescription,
            dateMillis: dateMillis
        )

        return .result(dialog: result.intentDialog)
    }
}

struct CurrentMonthExpenseTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Current Month Expenses"
    static let description = IntentDescription("Returns the total expenses recorded in Spesify for the current month.")
    static let openAppWhenRun = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getCurrentMonthExpensesTotal()
        return .result(dialog: result.dialog(prefix: "Current month expenses"))
    }
}

struct MonthExpenseTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Monthly Expenses"
    static let description = IntentDescription("Returns the total expenses recorded in Spesify for a selected month.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Month",
        description: "Any date inside the month to query.",
        requestValueDialog: IntentDialog(stringLiteral: "Which month should I check?")
    )
    var month: Date

    static var parameterSummary: some ParameterSummary {
        Summary("Get expenses for \(\.$month)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let components = month.monthComponents else {
            return .result(dialog: IntentDialog(stringLiteral: "Please specify a valid month."))
        }
        let result = try await IosFinancialQueryIntentController().getExpensesTotalForMonth(
            year: Int32(components.year),
            month: Int32(components.month)
        )
        return .result(dialog: result.dialog(prefix: "Expenses for \(components.monthName) \(components.year)"))
    }
}

struct PeriodExpenseTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Expenses for Period"
    static let description = IntentDescription("Returns the total expenses recorded in Spesify for an inclusive date period.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Start Date",
        description: "The first date to include.",
        requestValueDialog: IntentDialog(stringLiteral: "What is the start date?")
    )
    var startDate: Date

    @Parameter(
        title: "End Date",
        description: "The last date to include.",
        requestValueDialog: IntentDialog(stringLiteral: "What is the end date?")
    )
    var endDate: Date

    static var parameterSummary: some ParameterSummary {
        Summary("Get expenses from \(\.$startDate) to \(\.$endDate)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getExpensesTotalForPeriod(
            startDateMillis: startDate.epochMilliseconds,
            endDateMillis: endDate.epochMilliseconds
        )
        return .result(dialog: result.dialog(prefix: "Expenses from \(startDate.shortDateText) to \(endDate.shortDateText)"))
    }
}

struct CurrentMonthIncomeTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Current Month Income"
    static let description = IntentDescription("Returns the total income recorded in Spesify for the current month.")
    static let openAppWhenRun = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getCurrentMonthIncomeTotal()
        return .result(dialog: result.dialog(prefix: "Current month income"))
    }
}

struct MonthIncomeTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Monthly Income"
    static let description = IntentDescription("Returns the total income recorded in Spesify for a selected month.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Month",
        description: "Any date inside the month to query.",
        requestValueDialog: IntentDialog(stringLiteral: "Which month should I check?")
    )
    var month: Date

    static var parameterSummary: some ParameterSummary {
        Summary("Get income for \(\.$month)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let components = month.monthComponents else {
            return .result(dialog: IntentDialog(stringLiteral: "Please specify a valid month."))
        }
        let result = try await IosFinancialQueryIntentController().getIncomeTotalForMonth(
            year: Int32(components.year),
            month: Int32(components.month)
        )
        return .result(dialog: result.dialog(prefix: "Income for \(components.monthName) \(components.year)"))
    }
}

struct PeriodIncomeTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Income for Period"
    static let description = IntentDescription("Returns the total income recorded in Spesify for an inclusive date period.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Start Date",
        description: "The first date to include.",
        requestValueDialog: IntentDialog(stringLiteral: "What is the start date?")
    )
    var startDate: Date

    @Parameter(
        title: "End Date",
        description: "The last date to include.",
        requestValueDialog: IntentDialog(stringLiteral: "What is the end date?")
    )
    var endDate: Date

    static var parameterSummary: some ParameterSummary {
        Summary("Get income from \(\.$startDate) to \(\.$endDate)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getIncomeTotalForPeriod(
            startDateMillis: startDate.epochMilliseconds,
            endDateMillis: endDate.epochMilliseconds
        )
        return .result(dialog: result.dialog(prefix: "Income from \(startDate.shortDateText) to \(endDate.shortDateText)"))
    }
}

struct CurrentBalanceIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Current Balance"
    static let description = IntentDescription("Returns the current balance recorded in Spesify. Balance matches the dashboard cumulative balance through the current calendar month.")
    static let openAppWhenRun = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getCurrentBalance()
        return .result(dialog: result.dialog(prefix: "Current balance"))
    }
}

struct ListCategoriesIntent: AppIntent {
    static let title: LocalizedStringResource = "List Categories"
    static let description = IntentDescription("Lists active Spesify categories for the selected type.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Type",
        description: "Choose expense or income categories.",
        requestValueDialog: IntentDialog(stringLiteral: "Which categories should I list?")
    )
    var kind: SpesifyIntentTransactionKind

    static var parameterSummary: some ParameterSummary {
        Summary("List \(\.$kind) categories")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosCategoryManagementIntentController().listCategories(kind: kind.sharedKind)
        return .result(dialog: IntentDialog(stringLiteral: result.message))
    }
}

struct AddCategoryIntent: AppIntent {
    static let title: LocalizedStringResource = "Add Category"
    static let description = IntentDescription("Adds a new Spesify category explicitly requested by the user.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Type",
        description: "Choose expense or income category.",
        requestValueDialog: IntentDialog(stringLiteral: "Is this an expense or income category?")
    )
    var kind: SpesifyIntentTransactionKind

    @Parameter(
        title: "Name",
        description: "Category name to create.",
        requestValueDialog: IntentDialog(stringLiteral: "What is the category name?")
    )
    var name: String

    @Parameter(title: "Icon Key", description: "Optional Spesify icon key. Leave empty to use the default icon.")
    var iconKey: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$kind) category \(\.$name)") {
            \.$iconKey
        }
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            return .result(dialog: IntentDialog(stringLiteral: "Please provide a category name."))
        }
        let result = try await IosCategoryManagementIntentController().addCategory(
            kind: kind.sharedKind,
            name: trimmedName,
            iconKey: iconKey?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        )
        return .result(dialog: result.intentDialog)
    }
}

struct DeleteCategoryIntent: AppIntent {
    static let title: LocalizedStringResource = "Delete Category"
    static let description = IntentDescription("Deletes a Spesify category after moving existing transactions to another category.")
    static let openAppWhenRun = false

    @Parameter(
        title: "Type",
        description: "Choose expense or income category.",
        requestValueDialog: IntentDialog(stringLiteral: "Is this an expense or income category?")
    )
    var kind: SpesifyIntentTransactionKind

    @Parameter(
        title: "Category",
        description: "Category name or ID to delete.",
        requestValueDialog: IntentDialog(stringLiteral: "Which category should I delete?")
    )
    var categoryName: String

    @Parameter(
        title: "Move Transactions To",
        description: "Replacement category name or ID that should receive transactions using the deleted category.",
        requestValueDialog: IntentDialog(stringLiteral: "Which category should receive existing transactions?")
    )
    var moveToCategoryName: String

    static var parameterSummary: some ParameterSummary {
        Summary("Delete \(\.$categoryName) and move transactions to \(\.$moveToCategoryName)") {
            \.$kind
        }
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let source = categoryName.trimmingCharacters(in: .whitespacesAndNewlines)
        let target = moveToCategoryName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !source.isEmpty else {
            return .result(dialog: IntentDialog(stringLiteral: "Please specify the category to delete."))
        }
        guard !target.isEmpty else {
            return .result(dialog: IntentDialog(stringLiteral: "Please specify the category that should receive existing transactions."))
        }
        let result = try await IosCategoryManagementIntentController().deleteCategory(
            kind: kind.sharedKind,
            categoryName: source,
            moveToCategoryName: target
        )
        return .result(dialog: result.intentDialog)
    }
}
