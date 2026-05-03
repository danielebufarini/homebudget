@preconcurrency import ComposeApp
import SwiftUI

private struct NativeCategory: Identifiable, Equatable {
    let id: String
    let name: String
    let iconKey: String
    let isCustom: Bool
}

@MainActor
private final class CategoriesViewModel: ObservableObject {
    @Published var categories: [NativeCategory] = []
    @Published var errorMessage: String?

    private let controller = IosCategoriesController()
    private var isObserving = false

    deinit {
        controller.dispose()
    }

    func start() {
        guard !isObserving else {
            return
        }

        isObserving = true
        controller.start { [weak self] snapshot in
            guard let self else {
                return
            }

            Task { @MainActor in
                self.categories = snapshot.categories.map { category in
                    NativeCategory(
                        id: category.id,
                        name: category.name,
                        iconKey: category.iconKey,
                        isCustom: category.isCustom
                    )
                }
            }
        }
    }

    func stop() {
        guard isObserving else {
            return
        }

        controller.stop()
        isObserving = false
    }

    func save(category: NativeCategory?, name: String, iconKey: String, onSuccess: @escaping () -> Void) {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            errorMessage = appLocalized("Category name is required")
            return
        }

        if let category {
            controller.updateCategory(id: category.id, name: trimmedName, iconKey: iconKey) { [weak self] success in
                Task { @MainActor in
                    if success.boolValue {
                        onSuccess()
                    } else {
                        self?.errorMessage = appLocalized("Unable to save category")
                    }
                }
            }
        } else {
            controller.insertCategory(name: trimmedName, iconKey: iconKey) { [weak self] success in
                Task { @MainActor in
                    if success.boolValue {
                        onSuccess()
                    } else {
                        self?.errorMessage = appLocalized("Unable to save category")
                    }
                }
            }
        }
    }

    func delete(_ category: NativeCategory) {
        guard category.isCustom else {
            return
        }

        controller.deleteCategory(id: category.id) { [weak self] success in
            Task { @MainActor in
                if !success.boolValue {
                    self?.errorMessage = appLocalized("Unable to delete category")
                }
            }
        }
    }
}

struct NativeCategoriesScreen: View {
    @StateObject private var viewModel = CategoriesViewModel()
    @State private var categoryBeingEdited: NativeCategory?
    @State private var isEditorPresented = false

    var body: some View {
        ZStack {
            AppGlassBackdrop()

            if viewModel.categories.isEmpty {
                ProgressView()
                    .appGlassSurface(cornerRadius: 20)
            } else {
                List {
                    ForEach(viewModel.categories) { category in
                        CategoryRow(category: category)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                if category.isCustom {
                                    categoryBeingEdited = category
                                    isEditorPresented = true
                                }
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                if category.isCustom {
                                    Button(role: .destructive) {
                                        viewModel.delete(category)
                                    } label: {
                                        Label(appLocalized("Delete"), systemImage: "trash")
                                    }
                                }
                            }
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .safeAreaInset(edge: .top) {
                    Color.clear.frame(height: 8)
                }
            }
        }
        .appGlassHostedScreenChrome()
        .toolbar {
            ToolbarItem(placement: .principal) {
                AppGlassToolbarTitle(text: appLocalized("Categories"))
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    categoryBeingEdited = nil
                    isEditorPresented = true
                } label: {
                    AppGlassToolbarIcon(systemName: "plus")
                }
                .buttonStyle(.plain)
            }
        }
        .sheet(isPresented: $isEditorPresented) {
            CategoryEditorSheet(
                category: categoryBeingEdited,
                onCancel: {
                    isEditorPresented = false
                    categoryBeingEdited = nil
                },
                onSave: { name, iconKey in
                    viewModel.save(
                        category: categoryBeingEdited,
                        name: name,
                        iconKey: iconKey
                    ) {
                        isEditorPresented = false
                        categoryBeingEdited = nil
                    }
                }
            )
            .presentationCornerRadius(28)
            .presentationDragIndicator(.visible)
        }
        .alert(
            appLocalized("Categories"),
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        viewModel.errorMessage = nil
                    }
                }
            )
        ) {
            Button(appLocalized("Close"), role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .onAppear {
            viewModel.start()
        }
        .onDisappear {
            viewModel.stop()
        }
    }
}

private struct CategoryRow: View {
    let category: NativeCategory

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: categorySystemImageName(category.iconKey))
                .font(.title3.weight(.semibold))
                .foregroundStyle(.tint)
                .frame(width: 28, height: 28)

            VStack(alignment: .leading, spacing: 4) {
                Text(category.name)
                    .font(.title3)
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Text(category.isCustom ? appLocalized("Custom category") : appLocalized("Default category"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if category.isCustom {
                Image(systemName: "pencil")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .appGlassSurface(cornerRadius: 22)
    }
}

private struct CategoryEditorSheet: View {
    let category: NativeCategory?
    let onCancel: () -> Void
    let onSave: (String, String) -> Void

    @State private var name: String
    @State private var selectedIconKey: String

    init(
        category: NativeCategory?,
        onCancel: @escaping () -> Void,
        onSave: @escaping (String, String) -> Void
    ) {
        self.category = category
        self.onCancel = onCancel
        self.onSave = onSave
        _name = State(initialValue: category?.name ?? "")
        _selectedIconKey = State(initialValue: category?.iconKey ?? "category")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(appLocalized("Category Name"))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)

                        TextField(appLocalized("Category Name"), text: $name)
                            .textInputAutocapitalization(.words)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .appGlassSurface(cornerRadius: 16)
                    }

                    CategoryIconPicker(selectedIconKey: $selectedIconKey)
                }
                .padding(20)
            }
            .appGlassSheetChrome()
            .navigationTitle(category == nil ? appLocalized("Add Category") : appLocalized("Edit Category"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(appLocalized("Cancel"), action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(category == nil ? appLocalized("Add") : appLocalized("Update")) {
                        onSave(name, selectedIconKey)
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

private struct CategoryIconPicker: View {
    @Binding var selectedIconKey: String

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 4)

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(appLocalized("Icon"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)

            ForEach(categoryIconSections) { section in
                VStack(alignment: .leading, spacing: 10) {
                    Text(appLocalized(section.titleKey))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)

                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(section.iconKeys, id: \.self) { iconKey in
                            Button {
                                selectedIconKey = iconKey
                            } label: {
                                Image(systemName: categorySystemImageName(iconKey))
                                    .font(.title3.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .foregroundStyle(normalizedCategoryIconKey(selectedIconKey) == normalizedCategoryIconKey(iconKey) ? Color.white : Color.accentColor)
                                    .background(
                                        normalizedCategoryIconKey(selectedIconKey) == normalizedCategoryIconKey(iconKey) ? Color.accentColor : Color.clear,
                                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    )
                                    .appGlassSurface(cornerRadius: 16)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}

private struct CategoryIconSection: Identifiable {
    let id: String
    let titleKey: String
    let iconKeys: [String]
}

private let categoryIconSections = [
    CategoryIconSection(
        id: "home",
        titleKey: "Home & Bills",
        iconKeys: ["home", "receipt", "build"]
    ),
    CategoryIconSection(
        id: "food",
        titleKey: "Food & Dining",
        iconKeys: ["shopping_cart", "restaurant", "local_cafe", "cake"]
    ),
    CategoryIconSection(
        id: "transport",
        titleKey: "Transport & Travel",
        iconKeys: ["directions_car", "directions_bus", "train", "local_taxi", "flight", "hotel", "beach_access"]
    ),
    CategoryIconSection(
        id: "health",
        titleKey: "Health & Wellness",
        iconKeys: ["local_hospital", "healing", "fitness_center", "spa"]
    ),
    CategoryIconSection(
        id: "people",
        titleKey: "People & Work",
        iconKeys: ["person", "work", "school"]
    ),
    CategoryIconSection(
        id: "general",
        titleKey: "General & Hobbies",
        iconKeys: ["pets", "category"]
    )
]

private func categorySystemImageName(_ iconKey: String?) -> String {
    switch normalizedCategoryIconKey(iconKey) {
    case "home":
        return "house.fill"
    case "receipt":
        return "receipt.fill"
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
        return "taxi.fill"
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
    case "fitness_center":
        return "figure.strengthtraining.traditional"
    case "spa":
        return "leaf.fill"
    case "person":
        return "person.fill"
    case "work":
        return "briefcase.fill"
    case "school":
        return "graduationcap.fill"
    case "pets":
        return "pawprint.fill"
    default:
        return "square.grid.2x2.fill"
    }
}

private func normalizedCategoryIconKey(_ iconKey: String?) -> String {
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
