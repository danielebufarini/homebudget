import SwiftUI
import Observation

// Shared SwiftUI chrome helpers for the iOS-native shell and sheets.

enum AppThemePalette {
    static var background: Color {
        Color(uiColor: UIColor { traits in
            switch traits.userInterfaceStyle {
            case .dark:
                UIColor(red: 16.0 / 255.0, green: 24.0 / 255.0, blue: 32.0 / 255.0, alpha: 1.0)
            default:
                UIColor(red: 247.0 / 255.0, green: 250.0 / 255.0, blue: 255.0 / 255.0, alpha: 1.0)
            }
        })
    }

    static var chromeSurface: Color {
        Color(uiColor: UIColor { traits in
            switch traits.userInterfaceStyle {
            case .dark:
                UIColor(red: 36.0 / 255.0, green: 38.0 / 255.0, blue: 47.0 / 255.0, alpha: 0.68)
            default:
                UIColor(red: 232.0 / 255.0, green: 239.0 / 255.0, blue: 248.0 / 255.0, alpha: 0.86)
            }
        })
    }

    static var chromeStroke: Color {
        Color(uiColor: UIColor { traits in
            switch traits.userInterfaceStyle {
            case .dark:
                UIColor(red: 225.0 / 255.0, green: 232.0 / 255.0, blue: 240.0 / 255.0, alpha: 0.12)
            default:
                UIColor(red: 21.0 / 255.0, green: 28.0 / 255.0, blue: 36.0 / 255.0, alpha: 0.12)
            }
        })
    }

    static var onSurface: Color {
        Color(uiColor: UIColor { traits in
            switch traits.userInterfaceStyle {
            case .dark:
                UIColor(red: 225.0 / 255.0, green: 232.0 / 255.0, blue: 240.0 / 255.0, alpha: 1.0)
            default:
                UIColor(red: 21.0 / 255.0, green: 28.0 / 255.0, blue: 36.0 / 255.0, alpha: 1.0)
            }
        })
    }
}

struct AppGlassBackdrop: View {
    var body: some View {
        AppThemePalette.background.ignoresSafeArea()
    }
}

private struct AppGlassSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .glassEffect(in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

private struct AppDashboardChromeSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .background(
                AppThemePalette.chromeSurface,
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(AppThemePalette.chromeStroke, lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.16), radius: 16, x: 0, y: 8)
    }
}

private struct AppDashboardMonthHeaderSurfaceModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .background(
                AppThemePalette.background.opacity(0.72),
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(AppThemePalette.chromeStroke, lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.16), radius: 12, x: 0, y: 8)
    }
}

extension View {
    func appGlassSurface(cornerRadius: CGFloat = 20) -> some View {
        modifier(AppGlassSurfaceModifier(cornerRadius: cornerRadius))
    }

    func appDashboardChromeSurface(cornerRadius: CGFloat = 20) -> some View {
        modifier(AppDashboardChromeSurfaceModifier(cornerRadius: cornerRadius))
    }

    func appDashboardMonthHeaderSurface(cornerRadius: CGFloat = 22) -> some View {
        modifier(AppDashboardMonthHeaderSurfaceModifier(cornerRadius: cornerRadius))
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

struct AppDashboardToolbarIcon: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 28, weight: .semibold))
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
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
@Observable
final class AppGlassBannerPresenter {
    var banner: AppGlassFeedbackBannerState?

    @ObservationIgnored private var dismissTask: Task<Void, Never>?

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
    var presenter: AppGlassBannerPresenter

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
