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

extension SpesifyIntentTransactionKind {
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
