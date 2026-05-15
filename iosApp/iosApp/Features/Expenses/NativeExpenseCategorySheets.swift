import SwiftUI

struct NativeExpenseCategoryPickerSheet: View {
    let categories: [NativeExpenseCategory]
    let selectedCategoryId: String
    let onAddCategory: () -> Void
    let onSelectCategory: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(appLocalized("Select Category"))
                .font(.largeTitle.weight(.bold))
                .padding(.horizontal, 20)
                .padding(.top, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    NativeExpenseCategoryGridSection(
                        title: appLocalized("Categories"),
                        categories: categories,
                        selectedCategoryId: selectedCategoryId,
                        showAddTile: true,
                        onAddCategory: onAddCategory,
                        onSelectCategory: onSelectCategory
                    )
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
            }
        }
        .appGlassSheetChrome()
    }
}

private struct NativeExpenseCategoryGridSection: View {
    let title: String
    let categories: [NativeExpenseCategory]
    let selectedCategoryId: String
    let showAddTile: Bool
    let onAddCategory: () -> Void
    let onSelectCategory: (String) -> Void

    private var entries: [NativeExpenseCategory?] {
        showAddTile ? [nil] + categories.map(Optional.some) : categories.map(Optional.some)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)

            VStack(spacing: 12) {
                ForEach(Array(entries.chunked(into: 4).enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 12) {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, category in
                            if let category {
                                NativeExpenseCategoryTile(
                                    title: category.name,
                                    iconKey: category.iconKey,
                                    colorKey: category.id,
                                    isSelected: category.id == selectedCategoryId,
                                    action: { onSelectCategory(category.id) }
                                )
                            } else {
                                NativeExpenseAddCategoryTile(action: onAddCategory)
                            }
                        }

                        if row.count < 4 {
                            ForEach(0..<(4 - row.count), id: \.self) { _ in
                                Color.clear
                                    .frame(maxWidth: .infinity)
                                    .aspectRatio(0.92, contentMode: .fit)
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct NativeExpenseCategoryTile: View {
    let title: String
    let iconKey: String
    let colorKey: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Spacer(minLength: 0)

                Image(systemName: nativeExpenseCategorySystemImageName(iconKey))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(categoryIconColor(colorKey))

                Text(title)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                Spacer(minLength: 0)
            }
            .padding(8)
            .frame(maxWidth: .infinity)
            .aspectRatio(0.92, contentMode: .fit)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(isSelected ? Color.accentColor : Color.white.opacity(0.16), lineWidth: isSelected ? 2 : 1)
            )
            .appGlassSurface(cornerRadius: 24)
        }
        .buttonStyle(.plain)
    }
}

private struct NativeExpenseAddCategoryTile: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Spacer(minLength: 0)

                Image(systemName: "plus")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.accentColor)

                Text(appLocalized("Add Category"))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                Spacer(minLength: 0)
            }
            .padding(8)
            .frame(maxWidth: .infinity)
            .aspectRatio(0.92, contentMode: .fit)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.white.opacity(0.16), lineWidth: 1)
            )
            .appGlassSurface(cornerRadius: 24)
        }
        .buttonStyle(.plain)
    }
}

struct NativeAddCategorySheet: View {
    let onCancel: () -> Void
    let onConfirm: (String, String) -> Void

    @State private var name = ""
    @State private var selectedIconKey = "category"

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(appLocalized("Add Category"))
                .font(.largeTitle.weight(.bold))
                .padding(.horizontal, 20)
                .padding(.top, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    AppGlassSheetSection(spacing: 14, verticalPadding: 16) {
                        TextField(appLocalized("Category Name"), text: $name)
                            .textInputAutocapitalization(.words)
                    }

                    ForEach(nativeExpenseEditorIconSections) { section in
                        VStack(alignment: .leading, spacing: 10) {
                            Text(section.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)

                            VStack(spacing: 12) {
                                ForEach(Array(section.iconKeys.chunked(into: 4).enumerated()), id: \.offset) { _, row in
                                    HStack(spacing: 12) {
                                        ForEach(row, id: \.self) { iconKey in
                                            NativeExpenseCategoryIconChoice(
                                                iconKey: iconKey,
                                                isSelected: selectedIconKey == iconKey,
                                                action: { selectedIconKey = iconKey }
                                            )
                                        }

                                        if row.count < 4 {
                                            ForEach(0..<(4 - row.count), id: \.self) { _ in
                                                Color.clear
                                                    .frame(maxWidth: .infinity)
                                                    .aspectRatio(1, contentMode: .fit)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
            }

            AppGlassSheetActionBar {
                Button(appLocalized("Cancel"), action: onCancel)
                    .buttonStyle(.glass)

                Button(appLocalized("Add")) {
                    onConfirm(name.trimmingCharacters(in: .whitespacesAndNewlines), selectedIconKey)
                }
                .buttonStyle(.glassProminent)
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.bottom, 8)
        }
        .appGlassSheetChrome()
    }
}

private struct NativeExpenseCategoryIconChoice: View {
    let iconKey: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: nativeExpenseCategorySystemImageName(iconKey))
                .font(.title3.weight(.semibold))
                .foregroundStyle(categoryIconColor(iconKey))
                .frame(maxWidth: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(isSelected ? Color.accentColor : Color.white.opacity(0.16), lineWidth: isSelected ? 2 : 1)
                )
                .appGlassSurface(cornerRadius: 18)
        }
        .buttonStyle(.plain)
    }
}
