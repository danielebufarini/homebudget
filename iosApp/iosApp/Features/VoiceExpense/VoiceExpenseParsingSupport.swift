@preconcurrency import ComposeApp
import Foundation
import FoundationModels

// Fallback parsing, normalization, and category/date helpers for iOS voice expense input.

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
        summary: appLocalized("Ready to save a new expense.")
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
        return appLocalized("The on-device language model could not parse this command. Try again, or use a simpler phrase like \"20 euros yesterday food\".")
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
    return [category.name] + (voiceExpenseDefaultCategoryAliases[normalizedName] ?? [])
}

func availabilityMessage(for availability: SystemLanguageModel.Availability) -> String {
    switch availability {
    case .available:
        return ""
    case let .unavailable(reason):
        switch reason {
        case .deviceNotEligible:
            return appLocalized("Foundation Models are unavailable on this device.")
        case .appleIntelligenceNotEnabled:
            return appLocalized("Apple Intelligence must be enabled to use voice expense parsing.")
        case .modelNotReady:
            return appLocalized("The on-device model is still preparing. Try again in a moment.")
        @unknown default:
            return appLocalized("Foundation Models are currently unavailable.")
        }
    }
}

func resolveVoiceExpenseCategory(
    categoryId: String?,
    categoryName: String?,
    transcript: String,
    summary: String?,
    categoriesById: [String: VoiceExpenseCategory]
) -> VoiceExpenseCategory? {
    if let categoryId, let category = categoriesById[categoryId] {
        return category
    }

    if let categoryName {
        if let exactMatch = categoriesById.values.first(where: { $0.name.caseInsensitiveCompare(categoryName) == .orderedSame }) {
            return exactMatch
        }
        let normalizedCategoryName = normalizeVoiceExpenseToken(categoryName)
        if let normalizedMatch = categoriesById.values.first(where: {
            normalizeVoiceExpenseToken($0.name) == normalizedCategoryName
        }) {
            return normalizedMatch
        }
    }

    let searchText = summary.map { "\(transcript) \($0)" } ?? transcript
    return categoriesById.values.first(where: { category in
        voiceExpenseCategoryAliases(for: category).contains { alias in
            searchText.localizedCaseInsensitiveContains(alias)
        }
    })
}

func unresolvedVoiceExpenseDraftMessage(
    interpretation: VoiceExpenseInterpretation,
    transcript: String,
    categoriesById: [String: VoiceExpenseCategory]
) -> String {
    switch interpretation.intent {
    case .needClarification:
        return interpretation.summary

    case .ignore:
        return interpretation.summary.isEmpty
            ? appLocalized("I could not find a usable expense command.")
            : interpretation.summary

    case .create, .update:
        if normalizeAmountInput(interpretation.amount) == nil {
            return appLocalized("I could not understand the amount well enough to prepare the expense.")
        }

        if resolveVoiceExpenseCategory(
            categoryId: interpretation.categoryId,
            categoryName: interpretation.categoryName,
            transcript: transcript,
            summary: interpretation.summary,
            categoriesById: categoriesById
        ) == nil {
            return appLocalized("I could not match the spoken category to one of your categories.")
        }

        return appLocalized("I understood the request, but I could not prepare a saveable expense draft.")
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
    for regex in simpleExpenseAmountRegexes {
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

    for entry in relativeSpokenDateOffsets {
        if entry.phrases.contains(where: { normalizedTranscript.contains(" \($0) ") }) {
            return calendar.date(byAdding: .day, value: entry.days, to: today)
        }
    }

    return nil
}

private let simpleExpenseAmountRegexes: [NSRegularExpression] = [
    #"(?<![\d.,])(?:€|\$|eur|euro|euros|usd|dollar|dollars)\s*(\d+(?:[.,]\d{1,2})?)(?![\d.,])"#,
    #"(?<![\d.,])(\d+(?:[.,]\d{1,2})?)\s*(?:€|\$|eur|euro|euros|usd|dollar|dollars)(?![\d.,])"#,
    #"(?<![\d.,])(\d+(?:[.,]\d{1,2})?)(?![\d.,])"#
].compactMap { pattern in
    try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
}

private let relativeSpokenDateOffsets: [(phrases: [String], days: Int)] = [
    (["the day before yesterday", "day before yesterday", "l'altro ieri", "altro ieri"], -2),
    (["dopodomani"], 2),
    (["yesterday", "ieri"], -1),
    (["tomorrow", "domani"], 1),
    (["today", "oggi"], 0)
]

private let voiceExpenseDefaultCategoryAliases: [String: [String]] = [
    "cibo": ["cibo", "food", "groceries", "grocery", "meal", "meals", "ristorante", "restaurant"],
    "food": ["cibo", "food", "groceries", "grocery", "meal", "meals", "ristorante", "restaurant"],
    "bollette": ["bollette", "bills", "bill", "utilities", "utility"],
    "bills": ["bollette", "bills", "bill", "utilities", "utility"],
    "speseauto": ["spese auto", "auto", "car", "fuel", "gas", "gasoline", "parking", "parcheggio"],
    "carexpenses": ["spese auto", "auto", "car", "fuel", "gas", "gasoline", "parking", "parcheggio"],
    "spesecasa": ["spese casa", "casa", "home", "house", "rent", "affitto"],
    "homeexpenses": ["spese casa", "casa", "home", "house", "rent", "affitto"],
    "varie": ["varie", "misc", "miscellaneous", "other", "others"],
    "miscellaneous": ["varie", "misc", "miscellaneous", "other", "others"]
]

let voiceExpenseISODateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone.current
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter
}()

let voiceExpenseDisplayDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    return formatter
}()

extension Optional where Wrapped == String {
    var ifEmptyNil: String? {
        switch self?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case .some(let value) where !value.isEmpty:
            return value
        default:
            return nil
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
