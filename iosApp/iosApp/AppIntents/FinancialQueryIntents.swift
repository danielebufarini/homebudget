import AppIntents
@preconcurrency import ComposeApp
import Foundation

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct CurrentMonthExpenseTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Current Month Expenses"
    static let description = IntentDescription("Returns the total expenses recorded in Spesify for the current month.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getCurrentMonthExpensesTotal()
        return .result(dialog: result.dialog(prefix: "Current month expenses"))
    }
}

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct MonthExpenseTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Monthly Expenses"
    static let description = IntentDescription("Returns the total expenses recorded in Spesify for a selected month.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

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

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct PeriodExpenseTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Expenses for Period"
    static let description = IntentDescription("Returns the total expenses recorded in Spesify for an inclusive date period.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

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

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct CurrentMonthIncomeTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Current Month Income"
    static let description = IntentDescription("Returns the total income recorded in Spesify for the current month.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getCurrentMonthIncomeTotal()
        return .result(dialog: result.dialog(prefix: "Current month income"))
    }
}

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct MonthIncomeTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Monthly Income"
    static let description = IntentDescription("Returns the total income recorded in Spesify for a selected month.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

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

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct PeriodIncomeTotalIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Income for Period"
    static let description = IntentDescription("Returns the total income recorded in Spesify for an inclusive date period.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

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

#if SPESIFY_SIRI_AI
@AppIntent(.tool)
#endif
struct CurrentBalanceIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Current Balance"
    static let description = IntentDescription("Returns the current balance recorded in Spesify. Balance matches the dashboard cumulative balance through the current calendar month.")
    static let openAppWhenRun = false

    #if SPESIFY_SIRI_AI
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    #endif

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = try await IosFinancialQueryIntentController().getCurrentBalance()
        return .result(dialog: result.dialog(prefix: "Current balance"))
    }
}
