private let fullMonthNames = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
]

func monthName(_ month: Int) -> String {
    let index = month - 1
    guard fullMonthNames.indices.contains(index) else {
        return ""
    }

    return fullMonthNames[index]
}
