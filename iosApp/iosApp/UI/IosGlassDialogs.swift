import SwiftUI

struct AppGlassDialogOverlay<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.32)
                .ignoresSafeArea()

            VStack {
                Spacer()

                content
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
        .transition(.opacity)
        .zIndex(10)
    }
}

struct AppGlassDialogCard<Actions: View>: View {
    let title: String
    let message: String
    @ViewBuilder let actions: Actions

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.primary)

                Text(message)
                    .font(.body)
                    .foregroundStyle(.primary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(spacing: 10) {
                actions
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 18)
        .frame(maxWidth: 420)
        .appGlassSurface(cornerRadius: 28)
    }
}

struct AppGlassDialogButton: View {
    let title: String
    let isDestructive: Bool
    let action: () -> Void

    init(
        title: String,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.isDestructive = isDestructive
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.headline.weight(.medium))
                .foregroundStyle(isDestructive ? Color.red : Color.primary)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 54)
        }
        .buttonStyle(.plain)
        .appGlassSurface(cornerRadius: 22)
    }
}

struct AppGlassDeleteConfirmationDialog: View {
    let message: String
    let onDelete: () -> Void
    let onCancel: () -> Void

    var body: some View {
        AppGlassDialogCard(
            title: appLocalized("Delete"),
            message: message
        ) {
            AppGlassDialogButton(
                title: appLocalized("Delete"),
                isDestructive: true,
                action: onDelete
            )

            AppGlassDialogButton(
                title: appLocalized("Cancel"),
                action: onCancel
            )
        }
    }
}

struct AppGlassRecurringDeleteConfirmationDialog: View {
    let message: String
    let onDeleteInstance: () -> Void
    let onDeleteSeries: () -> Void
    let onCancel: () -> Void

    var body: some View {
        AppGlassDialogCard(
            title: appLocalized("Delete"),
            message: message
        ) {
            AppGlassDialogButton(
                title: appLocalized("This instance only"),
                isDestructive: true,
                action: onDeleteInstance
            )

            AppGlassDialogButton(
                title: appLocalized("Whole series"),
                isDestructive: true,
                action: onDeleteSeries
            )

            AppGlassDialogButton(
                title: appLocalized("Cancel"),
                action: onCancel
            )
        }
    }
}

struct AppGlassBottomQuickActionsBar: View {
    let addAccessibilityLabel: String
    let voiceAccessibilityLabel: String
    let onAdd: () -> Void
    let onVoice: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onAdd) {
                AppGlassBottomQuickActionIcon(systemName: "plus")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(addAccessibilityLabel)

            Rectangle()
                .fill(.primary.opacity(0.16))
                .frame(width: 1, height: 22)

            Button(action: onVoice) {
                AppGlassBottomQuickActionIcon(systemName: "mic.fill")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(voiceAccessibilityLabel)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 3)
        .appGlassSurface(cornerRadius: 24)
    }
}

private struct AppGlassBottomQuickActionIcon: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 14, weight: .semibold))
            .frame(width: 32, height: 32)
            .contentShape(Circle())
    }
}
