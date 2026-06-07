@preconcurrency import ComposeApp
import SwiftUI
import UIKit


struct NativeExpensePickerRow: View {
    let label: String
    let value: String
    var iconKey: String? = nil
    var colorKey: String? = nil
    var systemImageName: String? = nil
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                NativeExpenseLeadingIcon(
                    iconKey: iconKey,
                    colorKey: colorKey,
                    systemImageName: systemImageName
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text(label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)

                    Text(value)
                        .font(.title3.weight(.medium))
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.leading)
                }

                Spacer()

                if enabled {
                    Image(systemName: "chevron.right")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}





struct NativeExpenseDescriptionField: View {
    @Binding var descriptionText: String
    let readOnly: Bool
    @FocusState private var isDescriptionFocused: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            NativeExpenseLeadingIcon(systemImageName: "line.3.horizontal")

            VStack(alignment: .leading, spacing: 8) {
                Text(appLocalized("Description"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)

                if readOnly {
                    Text(descriptionText.isEmpty ? " " : descriptionText)
                        .font(.title3.weight(.medium))
                        .foregroundStyle(.primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    TextField("", text: $descriptionText, axis: .vertical)
                        .font(.title3.weight(.medium))
                        .textInputAutocapitalization(.sentences)
                        .autocorrectionDisabled(false)
                        .focused($isDescriptionFocused)
                        .submitLabel(.done)
                        .lineLimit(3, reservesSpace: false)
                        .onSubmit {
                            dismissDescriptionKeyboard()
                        }
                }
            }
        }
    }

    private func dismissDescriptionKeyboard() {
        isDescriptionFocused = false
        appDismissKeyboard()
    }
}

struct NativeExpenseToggleRow: View {
    let label: String
    let systemImageName: String
    @Binding var isOn: Bool
    let enabled: Bool

    var body: some View {
        HStack(spacing: 12) {
            NativeExpenseLeadingIcon(systemImageName: systemImageName)

            Text(label)
                .font(.title3.weight(.medium))
                .foregroundStyle(.primary)

            Spacer()

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .disabled(!enabled)
        }
    }
}

struct NativeExpenseInfoCard: View {
    let systemImageName: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: systemImageName)
                .foregroundStyle(.secondary)
            Text(text)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .appGlassSurface(cornerRadius: 18)
    }
}

struct NativeExpenseLeadingIcon: View {
    var iconKey: String? = nil
    var colorKey: String? = nil
    var systemImageName: String? = nil

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.blue.opacity(0.28))
                .frame(width: 56, height: 56)

            if let iconKey {
                Image(systemName: nativeExpenseCategorySystemImageName(iconKey))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(categoryIconColor(colorKey))
            } else if let systemImageName {
                Image(systemName: systemImageName)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.9))
            }
        }
    }
}

struct NativeRecurringSaveConfirmationDialog: View {
    let onUpdateInstance: () -> Void
    let onUpdateSeries: () -> Void
    let onCancel: () -> Void

    var body: some View {
        AppGlassDialogCard(
            title: appLocalized("Update Expense"),
            message: appLocalized("Choose whether to update only this instance or the whole series.")
        ) {
            AppGlassDialogButton(
                title: appLocalized("This instance only"),
                action: onUpdateInstance
            )

            AppGlassDialogButton(
                title: appLocalized("Whole series"),
                action: onUpdateSeries
            )

            AppGlassDialogButton(
                title: appLocalized("Cancel"),
                action: onCancel
            )
        }
    }
}
