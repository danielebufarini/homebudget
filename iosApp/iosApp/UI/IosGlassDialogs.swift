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
    let screenshotAccessibilityLabel: String?
    let onAdd: () -> Void
    let onVoice: () -> Void
    let onScreenshot: (() -> Void)?

    init(
        addAccessibilityLabel: String,
        voiceAccessibilityLabel: String,
        screenshotAccessibilityLabel: String? = nil,
        onAdd: @escaping () -> Void,
        onVoice: @escaping () -> Void,
        onScreenshot: (() -> Void)? = nil
    ) {
        self.addAccessibilityLabel = addAccessibilityLabel
        self.voiceAccessibilityLabel = voiceAccessibilityLabel
        self.screenshotAccessibilityLabel = screenshotAccessibilityLabel
        self.onAdd = onAdd
        self.onVoice = onVoice
        self.onScreenshot = onScreenshot
    }

    var body: some View {
        HStack(spacing: 3) {
            Button(action: onAdd) {
                AppGlassBottomQuickActionIcon(systemName: "plus")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(addAccessibilityLabel)

            quickActionDivider

            Button(action: onVoice) {
                AppGlassBottomQuickActionIcon(systemName: "mic")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(voiceAccessibilityLabel)

            if let onScreenshot, let screenshotAccessibilityLabel {
                quickActionDivider

                Button(action: onScreenshot) {
                    AppGlassBottomQuickActionIcon(systemName: "doc.text.viewfinder")
                }
                .buttonStyle(.plain)
                .accessibilityLabel(screenshotAccessibilityLabel)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 3)
        .foregroundStyle(AppThemePalette.onSurface)
        .appDashboardChromeSurface(cornerRadius: 24)
    }

    private var quickActionDivider: some View {
        Rectangle()
            .fill(AppThemePalette.onSurface.opacity(0.18))
            .frame(width: 1, height: 22)
    }
}

private struct AppGlassBottomQuickActionIcon: View {
    let systemName: String

    var body: some View {
        AppGlassToolbarIcon(systemName: systemName)
    }
}
