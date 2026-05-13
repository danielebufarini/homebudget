import SwiftUI

// Shared SwiftUI chrome helpers for the iOS-native shell and sheets.

struct AppGlassBackdrop: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(uiColor: .systemGroupedBackground),
                Color(uiColor: .secondarySystemGroupedBackground),
                Color(uiColor: .systemBackground)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
}

private struct AppGlassSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .glassEffect(in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

extension View {
    func appGlassSurface(cornerRadius: CGFloat = 20) -> some View {
        modifier(AppGlassSurfaceModifier(cornerRadius: cornerRadius))
    }

    func appGlassHostedScreenChrome() -> some View {
        background(AppGlassBackdrop())
            .toolbarBackground(.hidden, for: .navigationBar)
            .scrollEdgeEffectStyle(.soft, for: .top)
    }

    func appGlassSheetChrome() -> some View {
        background(AppGlassBackdrop())
            .scrollContentBackground(.hidden)
            .presentationBackground(.thinMaterial)
            .scrollEdgeEffectStyle(.soft, for: .top)
    }

    func appGlassSheetPresentation(
        detents: Set<PresentationDetent> = [.medium]
    ) -> some View {
        self
            .presentationDetents(detents)
            .presentationCornerRadius(28)
            .presentationDragIndicator(.visible)
    }
}

struct AppGlassToolbarIcon: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 15, weight: .semibold))
            .frame(width: 36, height: 36)
            .contentShape(Circle())
    }
}

struct AppGlassBackButton: View {
    var body: some View {
        AppGlassToolbarIcon(systemName: "chevron.left")
    }
}

struct AppGlassToolbarCluster<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 10) {
                content
            }
        }
    }
}

struct AppGlassToolbarTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.headline.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .appGlassSurface(cornerRadius: 22)
    }
}

struct AppGlassSheetActionBar<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        AppGlassToolbarCluster {
            HStack(spacing: 10) {
                content
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .background(.clear)
    }
}

enum AppGlassBannerStyle {
    case success
    case error

    var iconName: String {
        switch self {
        case .success:
            return "checkmark.circle.fill"
        case .error:
            return "exclamationmark.triangle.fill"
        }
    }

    var tint: Color {
        switch self {
        case .success:
            return .green
        case .error:
            return .red
        }
    }
}

struct AppGlassFeedbackBannerState: Identifiable, Equatable {
    let id = UUID()
    let message: String
    let style: AppGlassBannerStyle
}

@MainActor
final class AppGlassBannerPresenter: ObservableObject {
    @Published var banner: AppGlassFeedbackBannerState?

    private var dismissTask: Task<Void, Never>?

    deinit {
        dismissTask?.cancel()
    }

    func show(
        _ message: String,
        style: AppGlassBannerStyle,
        autoDismissAfter delay: Duration = .seconds(3)
    ) {
        dismissTask?.cancel()

        let nextBanner = AppGlassFeedbackBannerState(message: message, style: style)
        withAnimation(.easeInOut(duration: 0.2)) {
            banner = nextBanner
        }

        dismissTask = Task { @MainActor in
            try? await Task.sleep(for: delay)
            dismiss(id: nextBanner.id)
        }
    }

    func dismiss(id: UUID) {
        guard banner?.id == id else {
            return
        }

        dismissTask?.cancel()
        dismissTask = nil
        withAnimation(.easeInOut(duration: 0.2)) {
            banner = nil
        }
    }
}

struct AppGlassBanner: View {
    let message: String
    let style: AppGlassBannerStyle
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: style.iconName)
                .font(.title3.weight(.semibold))
                .foregroundStyle(style.tint)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.glass)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .appGlassSurface(cornerRadius: 22)
    }
}

struct AppGlassBannerOverlay: View {
    @ObservedObject var presenter: AppGlassBannerPresenter

    var body: some View {
        if let banner = presenter.banner {
            AppGlassBanner(
                message: banner.message,
                style: banner.style,
                onClose: { presenter.dismiss(id: banner.id) }
            )
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .transition(.move(edge: .top).combined(with: .opacity))
            .zIndex(1)
        }
    }
}

struct AppGlassListCard<Content: View>: View {
    let verticalPadding: CGFloat
    @ViewBuilder let content: Content

    init(
        verticalPadding: CGFloat = 12,
        @ViewBuilder content: () -> Content
    ) {
        self.verticalPadding = verticalPadding
        self.content = content()
    }

    var body: some View {
        content
            .padding(.horizontal, 14)
            .padding(.vertical, verticalPadding)
            .appGlassSurface(cornerRadius: 18)
    }
}

struct AppGlassSheetSection<Content: View>: View {
    let title: String?
    let spacing: CGFloat
    let verticalPadding: CGFloat
    @ViewBuilder let content: Content

    init(
        title: String? = nil,
        spacing: CGFloat = 10,
        verticalPadding: CGFloat = 12,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.spacing = spacing
        self.verticalPadding = verticalPadding
        self.content = content()
    }

    var body: some View {
        AppGlassListCard(verticalPadding: verticalPadding) {
            VStack(alignment: .leading, spacing: spacing) {
                if let title {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                content
            }
        }
    }
}

struct AppGlassSheetContentScrollView<Content: View>: View {
    let alignment: HorizontalAlignment
    let spacing: CGFloat
    let horizontalPadding: CGFloat
    let topPadding: CGFloat
    let bottomPadding: CGFloat
    @ViewBuilder let content: Content

    init(
        alignment: HorizontalAlignment = .leading,
        spacing: CGFloat = 16,
        horizontalPadding: CGFloat = 16,
        topPadding: CGFloat? = nil,
        bottomPadding: CGFloat = 108,
        @ViewBuilder content: () -> Content
    ) {
        self.alignment = alignment
        self.spacing = spacing
        self.horizontalPadding = horizontalPadding
        self.topPadding = topPadding ?? horizontalPadding
        self.bottomPadding = bottomPadding
        self.content = content()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: alignment, spacing: spacing) {
                content
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.top, topPadding)
            .padding(.bottom, bottomPadding)
        }
    }
}

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
        GlassEffectContainer(spacing: 8) {
            HStack(spacing: 8) {
                Button(action: onAdd) {
                    AppGlassBottomQuickActionIcon(systemName: "plus")
                }
                .buttonStyle(.glass)
                .accessibilityLabel(addAccessibilityLabel)

                Button(action: onVoice) {
                    AppGlassBottomQuickActionIcon(systemName: "waveform.badge.mic")
                }
                .buttonStyle(.glass)
                .accessibilityLabel(voiceAccessibilityLabel)
            }
        }
        .background(.clear)
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
