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

enum TransactionInputDockLayout {
    static let actionHeight: CGFloat = 44
    static let horizontalPadding: CGFloat = 16
    static let topPadding: CGFloat = 8
    static let bottomPadding: CGFloat = 12
    static let dashboardBottomPadding: CGFloat = 6
    static let dashboardVerticalOffset: CGFloat = 24
    static let dashboardManualAddFrameWidth: CGFloat = 58
    static let dashboardManualAddActionHeight: CGFloat = 52
    static let dashboardManualAddIconSize: CGFloat = 22
    static let contentVerticalPadding: CGFloat = 4

    static var overlayHeight: CGFloat {
        actionHeight + (contentVerticalPadding * 2) + topPadding + bottomPadding
    }

    static var listBottomClearance: CGFloat {
        overlayHeight + 8
    }
}

struct TransactionInputDock: View {
    enum SecondaryActionStyle {
        case overflowMenu
        case directVoice
    }

    let onManualAdd: () -> Void
    let onVoiceInput: () -> Void
    let onImportScreenshot: () -> Void
    private let bottomPadding: CGFloat
    private let secondaryActionStyle: SecondaryActionStyle
    private let manualAddFrameWidth: CGFloat
    private let manualAddActionHeight: CGFloat
    private let manualAddIconSize: CGFloat

    init(
        onManualAdd: @escaping () -> Void,
        onVoiceInput: @escaping () -> Void,
        onImportScreenshot: @escaping () -> Void,
        bottomPadding: CGFloat = TransactionInputDockLayout.bottomPadding,
        secondaryActionStyle: SecondaryActionStyle = .overflowMenu,
        manualAddFrameWidth: CGFloat = 50,
        manualAddActionHeight: CGFloat = TransactionInputDockLayout.actionHeight,
        manualAddIconSize: CGFloat = 15
    ) {
        self.onManualAdd = onManualAdd
        self.onVoiceInput = onVoiceInput
        self.onImportScreenshot = onImportScreenshot
        self.bottomPadding = bottomPadding
        self.secondaryActionStyle = secondaryActionStyle
        self.manualAddFrameWidth = manualAddFrameWidth
        self.manualAddActionHeight = manualAddActionHeight
        self.manualAddIconSize = manualAddIconSize
    }

    var body: some View {
        HStack(spacing: 2) {
            Button(action: onManualAdd) {
                AppGlassBottomQuickActionIcon(systemName: "plus", iconSize: manualAddIconSize)
                    .frame(width: manualAddFrameWidth, height: manualAddActionHeight)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(appLocalized("Add transaction"))

            quickActionDivider

            secondaryAction
        }
        .padding(.horizontal, 5)
        .padding(.vertical, TransactionInputDockLayout.contentVerticalPadding)
        .foregroundStyle(AppThemePalette.onSurface)
        .appDashboardChromeSurface(cornerRadius: 28)
        .padding(.horizontal, TransactionInputDockLayout.horizontalPadding)
        .padding(.top, TransactionInputDockLayout.topPadding)
        .padding(.bottom, bottomPadding)
        .frame(maxWidth: .infinity, alignment: .center)
    }

    @ViewBuilder
    private var secondaryAction: some View {
        switch secondaryActionStyle {
        case .overflowMenu:
            Menu {
                Button {
                    performAfterMenuDismiss(onVoiceInput)
                } label: {
                    Label(appLocalized("Voice input"), systemImage: "mic")
                }
                .accessibilityLabel(appLocalized("Voice input"))

                Button {
                    performAfterMenuDismiss(onImportScreenshot)
                } label: {
                    Label(appLocalized("Import payment screenshot"), systemImage: "doc.text.viewfinder")
                }
                .accessibilityLabel(appLocalized("Import payment screenshot"))
            } label: {
                AppGlassBottomQuickActionIcon(systemName: "ellipsis")
                    .frame(width: 50, height: TransactionInputDockLayout.actionHeight)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(appLocalized("More transaction input options"))

        case .directVoice:
            Button(action: onVoiceInput) {
                AppGlassBottomQuickActionIcon(systemName: "mic")
                    .frame(width: 50, height: TransactionInputDockLayout.actionHeight)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(appLocalized("Voice input"))
        }
    }

    private func performAfterMenuDismiss(_ action: @escaping () -> Void) {
        DispatchQueue.main.async {
            action()
        }
    }

    private var quickActionDivider: some View {
        Rectangle()
            .fill(AppThemePalette.onSurface.opacity(0.18))
            .frame(width: 1, height: 24)
            .accessibilityHidden(true)
    }
}

private struct AppGlassBottomQuickActionIcon: View {
    let systemName: String
    var iconSize: CGFloat = 15

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: iconSize, weight: .semibold))
            .frame(width: 36, height: 36)
            .contentShape(Circle())
    }
}
