import AppIntents
@preconcurrency import ComposeApp
import Foundation

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
        let result = try await IosCategoryManagementIntentController().listCategories(kind: kind.rawValue)
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
            kind: kind.rawValue,
            name: trimmedName,
            iconKey: iconKey?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        )
        return .result(dialog: result.dialog(defaultMessage: "Category added."))
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
            kind: kind.rawValue,
            categoryName: source,
            moveToCategoryName: target
        )
        return .result(dialog: result.dialog(defaultMessage: "Category deleted."))
    }
}
