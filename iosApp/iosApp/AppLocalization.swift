import Foundation

func appLocalized(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}

func appCurrencySymbol() -> String {
    Locale.current.language.languageCode?.identifier == "it" ? "€" : "$"
}

func appAmountLabel(_ amountInput: String) -> String {
    "\(appCurrencySymbol()) \(amountInput)"
}

func appMonthlyTitle(month: Int, key: String) -> String {
    "\(monthName(month)) \(appLocalized(key))"
}
