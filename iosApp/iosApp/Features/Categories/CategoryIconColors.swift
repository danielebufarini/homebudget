import SwiftUI

// Stable category icon colors are derived from category IDs so the same category keeps
// the same accent across lists and management screens.
func categoryIconColor(_ colorKey: String?) -> Color {
    categoryIconPalette[stableCategoryColorIndex(colorKey)]
}

private let categoryIconPalette: [Color] = [
    Color(red: 0.839, green: 0.353, blue: 0.353),
    Color(red: 0.776, green: 0.353, blue: 0.620),
    Color(red: 0.545, green: 0.361, blue: 0.965),
    Color(red: 0.357, green: 0.431, blue: 0.882),
    Color(red: 0.231, green: 0.510, blue: 0.965),
    Color(red: 0.055, green: 0.647, blue: 0.643),
    Color(red: 0.133, green: 0.627, blue: 0.420),
    Color(red: 0.518, green: 0.651, blue: 0.239),
    Color(red: 0.961, green: 0.620, blue: 0.043),
    Color(red: 0.976, green: 0.451, blue: 0.086)
]

private func stableCategoryColorIndex(_ colorKey: String?) -> Int {
    let key = colorKey?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    guard !key.isEmpty else {
        return 0
    }

    var hash: UInt32 = 0
    for scalar in key.unicodeScalars {
        hash = hash &* 31 &+ scalar.value
    }
    return Int(hash % UInt32(categoryIconPalette.count))
}
