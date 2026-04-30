import Foundation

func monthName(_ month: Int) -> String {
    var components = DateComponents()
    components.calendar = Calendar(identifier: .gregorian)
    components.year = 2024
    components.month = month
    components.day = 1

    guard let date = components.date else {
        return ""
    }

    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = .current
    formatter.dateFormat = "LLLL"
    return formatter.string(from: date).capitalized(with: .current)
}
