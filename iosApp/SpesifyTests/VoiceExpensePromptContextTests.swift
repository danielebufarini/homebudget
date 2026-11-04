import Foundation
import Testing
@testable import Spesify

struct VoiceExpensePromptContextTests {
    @Test
    func compactContextPreservesEveryCandidateAndEscapesDescriptions() throws {
        let date = Date(timeIntervalSince1970: 1_793_793_600)
        let description = "Pranzo, \"Zaffiro\"\nseconda riga\t| id=falso"
        let expenses = (0..<80).map { index in
            VoiceExpenseCandidate(
                id: "expense-\(index)", amountInput: "12.34", categoryId: "food-\(index % 2)",
                categoryName: "Food", description: description, date: date, isShared: index.isMultiple(of: 2)
            )
        }
        struct Table: Decodable {
            let fields: [String]
            let rows: [[String]]
        }
        let text = try voiceExpensePromptExpensesText(expenses)
        let decoded = try JSONDecoder().decode(Table.self, from: Data(text.utf8))
        #expect(decoded.fields == ["id", "amount", "date", "categoryId", "categoryName", "shared", "description"])
        #expect(decoded.rows.count == 80)
        for (index, row) in decoded.rows.enumerated() {
            #expect(row == [
                "expense-\(index)", "12.34", voiceExpenseISODateFormatter.string(from: date),
                "food-\(index % 2)", "Food", index.isMultiple(of: 2) ? "yes" : "no", description
            ])
        }
    }

    @Test
    func emptyHistoryRetainsItsExplicitAbsenceMarker() throws {
        #expect(try voiceExpensePromptExpensesText([]) == "- none")
    }
}
