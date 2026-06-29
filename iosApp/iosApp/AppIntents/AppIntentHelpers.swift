import AppIntents
@preconcurrency import ComposeApp
import Foundation

enum AmountParser {
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

struct MonthComponents {
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

extension IosFinancialQueryIntentResult {
    func dialog(prefix: String) -> IntentDialog {
        guard isSuccess else {
            return IntentDialog(stringLiteral: message ?? "Unable to read the requested amount.")
        }
        let amountText = IntentAmountFormatter.displayAmount(amount)
        return IntentDialog(stringLiteral: "\(prefix): \(amountText).")
    }
}

extension AddTransactionResult {
    var intentDialog: IntentDialog {
        switch onEnum(of: self) {
        case .created:
            return IntentDialog(stringLiteral: "Transaction added to Spesify.")
        case let .needsConfirmation(result):
            return IntentDialog(stringLiteral: result.message)
        case let .failed(result):
            return IntentDialog(stringLiteral: result.message)
        }
    }
}

extension CategoryCommandResult {
    var intentDialog: IntentDialog {
        switch onEnum(of: self) {
        case let .success(result):
            return IntentDialog(stringLiteral: result.message)
        case let .needsConfirmation(result):
            return IntentDialog(stringLiteral: result.message)
        case let .failed(result):
            return IntentDialog(stringLiteral: result.message)
        }
    }
}

extension Date {
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

extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
