import Testing
@testable import Spesify

struct VoiceExpenseParsingTests {
    @Test(arguments: [
        ("12", "12.00"),
        (" 12,5 ", "12.50"),
        ("0.25", "0.25"),
        ("42.00", "42.00")
    ])
    func normalizesSpokenAmounts(input: String, expected: String) {
        #expect(normalizeAmountInput(input) == expected)
    }

    @Test(arguments: ["", " ", "12.345", "12.3.4", "twelve", "-12"])
    func rejectsInvalidAmounts(input: String) {
        #expect(normalizeAmountInput(input) == nil)
    }

    @Test
    func absentAmountRemainsAbsent() {
        #expect(normalizeAmountInput(nil) == nil)
    }

    @Test
    func explicitCategoryIdentifierTakesPriorityOverTranscript() {
        let food = VoiceExpenseCategory(id: "food", name: "Food")
        let travel = VoiceExpenseCategory(id: "travel", name: "Travel")
        let resolved = resolveVoiceExpenseCategory(
            categoryId: travel.id,
            categoryName: food.name,
            transcript: "Food",
            summary: nil,
            categoriesById: [food.id: food, travel.id: travel]
        )
        #expect(resolved?.id == travel.id)
    }

    @Test
    func unknownCategoryIsNotInvented() {
        let resolved = resolveVoiceExpenseCategory(
            categoryId: "missing",
            categoryName: "Unknown",
            transcript: "12 euros",
            summary: nil,
            categoriesById: ["food": VoiceExpenseCategory(id: "food", name: "Food")]
        )
        #expect(resolved == nil)
    }
}
