import SwiftUI

struct GroupedExpenseSectionHeaderView: View {
    let section: GroupedExpenseSectionModel
    let isExpanded: Bool

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .rotationEffect(.degrees(isExpanded ? 90 : 0))
                .frame(width: 12)

            CategoryIconLabelView(
                colorKey: section.categoryColorKey,
                iconKey: section.categoryIconKey,
                text: section.title,
                showIcon: section.categoryIconKey != nil
            )

            Spacer()

            Text(section.totalAmountText)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(.top, 4)
        .textCase(nil)
    }
}

struct GroupedExpenseRowView: View {
    let row: GroupedExpenseRowModel

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center, spacing: 8) {
                    if row.categoryIconKey != nil {
                        CategoryIconView(colorKey: row.categoryColorKey, iconKey: row.categoryIconKey)
                    }
                    if row.isRecurring {
                        RecurringBadgeView()
                    }

                    Text(row.title)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                Text(row.subtitleText)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(row.amountText)
                .foregroundStyle(.primary)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

private struct CategoryIconLabelView: View {
    let colorKey: String?
    let iconKey: String?
    let text: String
    var showIcon = true

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            if showIcon {
                CategoryIconView(colorKey: colorKey, iconKey: iconKey)
            }
            Text(text)
                .lineLimit(1)
        }
    }
}

private struct CategoryIconView: View {
    let colorKey: String?
    let iconKey: String?

    var body: some View {
        Image(systemName: CategoryIconSymbolFactory.systemImageName(for: iconKey))
            .foregroundStyle(categoryIconColor(colorKey))
            .frame(width: 18, height: 18)
    }
}

private enum CategoryIconSymbolFactory {
    static func systemImageName(for iconKey: String?) -> String {
        switch normalizedKey(iconKey) {
        case "home":
            return "house.fill"
        case "build":
            return "hammer.fill"
        case "shopping_cart":
            return "cart.fill"
        case "restaurant":
            return "fork.knife"
        case "local_cafe":
            return "cup.and.saucer.fill"
        case "cake":
            return "birthday.cake.fill"
        case "directions_car":
            return "car.fill"
        case "directions_bus":
            return "bus.fill"
        case "train":
            return "tram.fill"
        case "local_taxi":
            return "car.side.fill"
        case "flight":
            return "airplane"
        case "hotel":
            return "bed.double.fill"
        case "beach_access":
            return "beach.umbrella.fill"
        case "local_hospital":
            return "cross.case.fill"
        case "healing":
            return "bandage.fill"
        case "receipt":
            return "receipt.fill"
        case "person":
            return "person.fill"
        case "work":
            return "briefcase.fill"
        case "school":
            return "graduationcap.fill"
        case "pets":
            return "pawprint.fill"
        case "fitness_center":
            return "figure.strengthtraining.traditional"
        case "spa":
            return "leaf.fill"
        default:
            return "square.grid.2x2.fill"
        }
    }

    private static func normalizedKey(_ iconKey: String?) -> String {
        switch iconKey?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case nil, "":
            return "category"
        case "household_expenses":
            return "home"
        case "food":
            return "shopping_cart"
        case "car_expenses":
            return "directions_car"
        case "travel":
            return "flight"
        case "healthcare_expenses":
            return "local_hospital"
        case "bills":
            return "receipt"
        case "personal_expenses", "personal_expeses":
            return "person"
        case "miscellaneous":
            return "category"
        case let key?:
            return key
        }
    }
}

private struct RecurringBadgeView: View {
    var body: some View {
        Text("R")
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(.red, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            .fixedSize()
    }
}
