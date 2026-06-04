@preconcurrency import ComposeApp
import Foundation

enum GroupedExpensesKind: Hashable {
    case monthly
    case shared
    case category(name: String)
    case day(day: Int)

    var screenType: String {
        switch self {
        case .monthly:
            return "monthly"
        case .shared:
            return "shared"
        case .category:
            return "category"
        case .day:
            return "day"
        }
    }

    var categoryName: String? {
        switch self {
        case let .category(name):
            return name
        case .monthly, .shared, .day:
            return nil
        }
    }

    var dayOfMonth: KotlinInt? {
        switch self {
        case let .day(day):
            return KotlinInt(int: Int32(day))
        case .monthly, .shared, .category:
            return nil
        }
    }

    var supportsMonthNavigation: Bool {
        switch self {
        case .monthly, .shared:
            return true
        case .category, .day:
            return false
        }
    }

    var showsGroupingControl: Bool {
        switch self {
        case .day:
            return false
        case .monthly, .shared, .category:
            return true
        }
    }
}

enum ExpenseGroupingMode: String, Hashable {
    case byCategory
    case byDate

    var bridgeValue: String {
        switch self {
        case .byCategory:
            return "category"
        case .byDate:
            return "date"
        }
    }
}
