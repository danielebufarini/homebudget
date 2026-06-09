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
            kind: kind.rawValue,
            amount: amountMinorUnits,
            categoryName: normalizedCategoryName,
            description: transactionDescription,
            dateMillis: dateMillis
        )

        if result.isCreated {
            return .result(dialog: "Transaction added to Spesify.")
        }

        if result.needsConfirmation {
            return .result(dialog: IntentDialog(stringLiteral: result.message ?? "More information is needed before adding the transaction."))
        }

        return .result(dialog: IntentDialog(stringLiteral: result.message ?? "Unable to add the transaction."))
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
    }
}

private enum AmountParser {
    static func minorUnits(from input: String) -> Int64? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.contains("-") else { return nil }

        let decimalSeparator = detectedDecimalSeparator(in: trimmed)
        let integerPart: String
        let fractionPart: String

        if let decimalSeparator, let separatorIndex = trimmed.lastIndex(of: decimalSeparator) {
            integerPart = String(trimmed[..<separatorIndex])
            fractionPart = String(trimmed[trimmed.index(after: separatorIndex)...])
        } else {
            integerPart = trimmed
            fractionPart = ""
        }

        let integerDigits = digitsOnly(integerPart)
        guard !integerDigits.isEmpty, let wholeUnits = Int64(integerDigits) else { return nil }

        let fractionDigits = digitsOnly(fractionPart)
        guard fractionDigits.count <= 2 else { return nil }

        let normalizedFractionDigits = fractionDigits.padding(toLength: 2, withPad: "0", startingAt: 0)
        guard let cents = Int64(normalizedFractionDigits) else { return nil }

        let wholeMinorUnits = wholeUnits.multipliedReportingOverflow(by: 100)
        guard !wholeMinorUnits.overflow else { return nil }

        let totalMinorUnits = wholeMinorUnits.partialValue.addingReportingOverflow(cents)
        guard !totalMinorUnits.overflow, totalMinorUnits.partialValue > 0 else { return nil }

        return totalMinorUnits.partialValue
    }

    private static func detectedDecimalSeparator(in input: String) -> Character? {
        let commaIndex = input.lastIndex(of: ",")
        let dotIndex = input.lastIndex(of: ".")

        if let commaIndex, let dotIndex {
            return commaIndex > dotIndex ? "," : "."
        }

        if let commaIndex {
            return separatorRepresentsDecimals(input: input, separatorIndex: commaIndex) ? "," : nil
        }

        if let dotIndex {
            return separatorRepresentsDecimals(input: input, separatorIndex: dotIndex) ? "." : nil
        }

        return nil
    }

    private static func separatorRepresentsDecimals(input: String, separatorIndex: String.Index) -> Bool {
        let digitsAfterSeparator = digitsOnly(String(input[input.index(after: separatorIndex)...])).count
        return digitsAfterSeparator > 0 && digitsAfterSeparator <= 2
    }

    private static func digitsOnly(_ input: String) -> String {
        input.unicodeScalars
            .filter { CharacterSet.decimalDigits.contains($0) }
            .map(String.init)
            .joined()
    }
}

private struct MonthComponents {
    let year: Int
    let month: Int

    var monthName: String {
        var dateComponents = DateComponents()
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = 1
        guard let date = Calendar.current.date(from: dateComponents) else {
            return "month \(month)"
        }
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateFormat = "LLLL"
        return formatter.string(from: date)
    }
}

private enum IntentAmountFormatter {
    static func displayAmount(_ minorUnits: Int64) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = .current
        let amount = NSDecimalNumber(value: minorUnits).dividing(by: NSDecimalNumber(value: 100))
        return formatter.string(from: amount) ?? amount.stringValue
    }
}

private extension IosFinancialQueryIntentResult {
    func dialog(prefix: String) -> IntentDialog {
        guard isSuccess else {
            return IntentDialog(stringLiteral: message ?? "Unable to read the requested amount.")
        }
        let amountText = IntentAmountFormatter.displayAmount(amount)
        return IntentDialog(stringLiteral: "\(prefix): \(amountText).")
    }
}

private extension Date {
    var epochMilliseconds: Int64 {
        Int64((timeIntervalSince1970 * 1000.0).rounded())
    }

    var monthComponents: MonthComponents? {
        let components = Calendar.current.dateComponents([.year, .month], from: self)
        guard let year = components.year, let month = components.month else { return nil }
        return MonthComponents(year: year, month: month)
    }

    var shortDateText: String {
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter.string(from: self)
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
