import Foundation

func monthName(_ month: Int) -> String {
    var components = DateComponents()
    components.calendar = monthNameCalendar
    components.year = 2024
    components.month = month
    components.day = 1

    guard let date = components.date else {
        return ""
    }

    return monthNameFormatter.string(from: date).capitalized(with: .current)
}

private let monthNameCalendar = Calendar(identifier: .gregorian)

private let monthNameFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.calendar = monthNameCalendar
    formatter.locale = .current
    formatter.dateFormat = "LLLL"
    return formatter
}()
