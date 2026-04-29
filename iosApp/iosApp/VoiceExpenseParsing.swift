@preconcurrency import ComposeApp
import Foundation
import FoundationModels

struct VoiceExpenseCategory: Identifiable {
    let id: String
    let name: String
}

struct VoiceExpenseSnapshotData {
    let categories: [VoiceExpenseCategory]
    let recentExpenses: [VoiceExpenseCandidate]
}

struct VoiceExpenseCandidate: Identifiable {
    let id: String
    let amountInput: String
    let categoryId: String
    let categoryName: String
    let description: String?
    let date: Date
    let isShared: Bool
}

struct VoiceExpenseDraft {
    let intent: VoiceExpenseInterpretation.Intent
    let expenseId: String?
    let amountInput: String
    let categoryId: String
    let categoryName: String
    let description: String?
    let date: Date
    let isShared: Bool
    let summary: String

    var actionLabel: String {
        intent == .create ? "Create new expense" : "Update existing expense"
    }

    var actionButtonTitle: String {
        intent == .create ? "Save Expense" : "Update Expense"
    }

    var amountLabel: String? {
        "€ \(amountInput)"
    }

    var dateLabel: String? {
        voiceExpenseDisplayDateFormatter.string(from: date)
    }
}

@Generable(description: "A parsed expense action extracted from a spoken user command.")
struct VoiceExpenseInterpretation {
    @Generable
    enum Intent {
        case create
        case update
        case needClarification
    }

    @Guide(description: "Whether the user wants to create a new expense, update an existing expense, or the command needs clarification.")
    var intent: Intent

    @Guide(description: "Existing expense id to update. Use null for new expenses or when clarification is needed.")
    var expenseId: String?

    @Guide(description: "Expense amount in euros with two decimal digits, for example 12.50. Use null only when clarification is needed or the amount is unchanged in an update.")
    var amount: String?

    @Guide(description: "Category id from the provided categories. Use null only when clarification is needed or the category is unchanged in an update.")
    var categoryId: String?

    @Guide(description: "Category name that exactly matches one of the provided categories whenever possible. Use null only when clarification is needed or the category is unchanged in an update.")
    var categoryName: String?

    @Guide(description: "Short expense description. Use null when omitted.")
    var description: String?

    @Guide(description: "Date in yyyy-MM-dd format. Resolve relative dates like yesterday, today, and tomorrow to a concrete date. For new expenses with no spoken date, use today's date.")
    var date: String?

    @Guide(description: "Whether the expense is shared. Use null when not mentioned for updates.")
    var isShared: Bool?

    @Guide(description: "Short user-facing summary of the parsed action or of the clarification needed.")
    var summary: String
}

func parseExpenseIntent(
    transcript: String,
    categories: [VoiceExpenseCategory],
    expenses: [VoiceExpenseCandidate]
) async throws -> VoiceExpenseInterpretation {
    let today = voiceExpenseISODateFormatter.string(from: Date())
    let categoriesText = categories
        .map { "- id=\($0.id), name=\($0.name)" }
        .joined(separator: "\n")
    let expensesText = expenses
        .map { expense in
            let dateText = voiceExpenseISODateFormatter.string(from: expense.date)
            let description = expense.description.ifEmptyNil
            let shared = expense.isShared ? "yes" : "no"
            return "- id=\(expense.id), amount=\(expense.amountInput), date=\(dateText), categoryId=\(expense.categoryId), categoryName=\(expense.categoryName), shared=\(shared), description=\(description)"
        }
        .joined(separator: "\n")

    let session = LanguageModelSession(
        model: SystemLanguageModel.default,
        instructions: """
        You convert a spoken budget command into one structured expense action.
        The input may be in Italian or English.
        Prefer create when the user is adding a new expense.
        Prefer update only when one listed expense is a clear match.
        If the command is ambiguous, return needClarification.
        Use only category ids and category names from the provided category list.
        For update, keep omitted fields as null so the app can preserve the existing value.
        Resolve relative dates like yesterday, today, and tomorrow into yyyy-MM-dd dates.
        For create, if no date is spoken, use today's date.
        Return short summaries.
        """
    )

    let response = try await session.respond(
        generating: VoiceExpenseInterpretation.self
    ) {
        """
        Today's date: \(today)

        Transcript:
        \(transcript)

        Categories:
        \(categoriesText)

        Existing expenses available for updates:
        \(expensesText)
        """
    }

    return response.content
}

func parseSimpleExpenseIntent(transcript: String) -> VoiceExpenseInterpretation? {
    guard let amount = parseSimpleExpenseAmount(from: transcript) else {
        return nil
    }

    return VoiceExpenseInterpretation(
        intent: .create,
        expenseId: nil,
        amount: amount,
        categoryId: nil,
        categoryName: nil,
        description: nil,
        date: nil,
        isShared: nil,
        summary: "Ready to save a new expense."
    )
}

func normalizeAmountInput(_ amount: String?) -> String? {
    guard let amount else {
        return nil
    }

    let trimmed = amount.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
        return nil
    }

    let normalized = trimmed.replacingOccurrences(of: ",", with: ".")
    let parts = normalized.split(separator: ".", omittingEmptySubsequences: false)
    guard parts.count <= 2 else {
        return nil
    }

    let whole = parts.first.map(String.init) ?? "0"
    guard whole.allSatisfy(\.isNumber) else {
        return nil
    }

    let decimals = parts.count == 2 ? String(parts[1]) : ""
    guard decimals.allSatisfy(\.isNumber), decimals.count <= 2 else {
        return nil
    }

    return "\(whole).\(decimals.padding(toLength: 2, withPad: "0", startingAt: 0))"
}

func expenseParsingFailureMessage(for error: Error) -> String {
    let errorDescription = error.localizedDescription
    let debugDescription = String(describing: error)
    if errorDescription.contains("FoundationModels") || debugDescription.contains("FoundationModels") {
        return "The on-device language model could not parse this command. Try again, or use a simpler phrase like \"20 euros yesterday food\"."
    }

    return errorDescription
}

func resolveExpenseDate(isoValue: String?, transcript: String, summary: String?, defaultDate: Date) -> Date {
    if let relativeDate = parseRelativeSpokenDate(from: transcript) {
        return relativeDate
    }

    if let summary, let relativeDate = parseRelativeSpokenDate(from: summary) {
        return relativeDate
    }

    if let parsedDate = parseISODate(isoValue) {
        return parsedDate
    }

    return defaultDate
}

func normalizeVoiceExpenseToken(_ value: String) -> String {
    let normalized = value.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
    return String(normalized.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) })
}

func voiceExpenseCategoryAliases(for category: VoiceExpenseCategory) -> [String] {
    let normalizedName = normalizeVoiceExpenseToken(category.name)
    let defaultAliases: [String: [String]] = [
        "cibo": ["cibo", "food", "groceries", "grocery", "meal", "meals", "ristorante", "restaurant"],
        "bollette": ["bollette", "bills", "bill", "utilities", "utility"],
        "speseauto": ["spese auto", "auto", "car", "fuel", "gas", "gasoline", "parking", "parcheggio"],
        "spesecasa": ["spese casa", "casa", "home", "house", "rent", "affitto"],
        "varie": ["varie", "misc", "miscellaneous", "other", "others"]
    ]

    return [category.name] + (defaultAliases[normalizedName] ?? [])
}

func availabilityMessage(for availability: SystemLanguageModel.Availability) -> String {
    switch availability {
    case .available:
        return ""
    case let .unavailable(reason):
        switch reason {
        case .deviceNotEligible:
            return "Foundation Models are unavailable on this device."
        case .appleIntelligenceNotEnabled:
            return "Apple Intelligence must be enabled to use voice expense parsing."
        case .modelNotReady:
            return "The on-device model is still preparing. Try again in a moment."
        @unknown default:
            return "Foundation Models are currently unavailable."
        }
    }
}

func buildVoiceExpenseSnapshotData(from snapshot: IosVoiceExpenseSnapshot) -> VoiceExpenseSnapshotData {
    VoiceExpenseSnapshotData(
        categories: snapshot.categories.map { category in
            VoiceExpenseCategory(
                id: category.id,
                name: category.name
            )
        },
        recentExpenses: snapshot.recentExpenses.map { expense in
            VoiceExpenseCandidate(
                id: expense.id,
                amountInput: expense.amountInput,
                categoryId: expense.categoryId,
                categoryName: expense.categoryName,
                description: expense.description,
                date: Date(timeIntervalSince1970: TimeInterval(expense.date) / 1000.0),
                isShared: expense.isShared
            )
        }
    )
}

private func parseISODate(_ value: String?) -> Date? {
    guard let value else {
        return nil
    }
    return voiceExpenseISODateFormatter.date(from: value)
}

private func parseSimpleExpenseAmount(from transcript: String) -> String? {
    let patterns = [
        #"(?<![\d.,])(?:€|eur|euro|euros)\s*(\d+(?:[.,]\d{1,2})?)(?![\d.,])"#,
        #"(?<![\d.,])(\d+(?:[.,]\d{1,2})?)\s*(?:€|eur|euro|euros)(?![\d.,])"#,
        #"(?<![\d.,])(\d+(?:[.,]\d{1,2})?)(?![\d.,])"#
    ]

    for pattern in patterns {
        guard
            let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
        else {
            continue
        }

        let nsRange = NSRange(transcript.startIndex..<transcript.endIndex, in: transcript)
        guard
            let match = regex.firstMatch(in: transcript, range: nsRange),
            match.numberOfRanges > 1,
            let amountRange = Range(match.range(at: 1), in: transcript)
        else {
            continue
        }

        if let amount = normalizeAmountInput(String(transcript[amountRange])) {
            return amount
        }
    }

    return nil
}

private func parseRelativeSpokenDate(from transcript: String) -> Date? {
    let normalizedTranscript = " \(transcript.lowercased()) "
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())

    let relativeOffsets: [(phrases: [String], days: Int)] = [
        (["the day before yesterday", "day before yesterday", "l'altro ieri", "altro ieri"], -2),
        (["dopodomani"], 2),
        (["yesterday", "ieri"], -1),
        (["tomorrow", "domani"], 1),
        (["today", "oggi"], 0)
    ]

    for entry in relativeOffsets {
        if entry.phrases.contains(where: { normalizedTranscript.contains(" \($0) ") }) {
            return calendar.date(byAdding: .day, value: entry.days, to: today)
        }
    }

    return nil
}

private let voiceExpenseISODateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone.current
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter
}()

private let voiceExpenseDisplayDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    return formatter
}()

private extension Optional where Wrapped == String {
    var ifEmptyNil: String {
        switch self?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case .some(let value) where !value.isEmpty:
            return value
        default:
            return "-"
        }
    }
}

extension Optional where Wrapped == String {
    var trimmedNilIfBlank: String? {
        guard let value = self?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        return value
    }
}
