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
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .background(Color.white.opacity(0.18), in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(.white.opacity(0.42), lineWidth: 1)
            }
            .shadow(color: .white.opacity(0.20), radius: 1, y: 1)
            .shadow(color: .black.opacity(0.12), radius: 24, y: 10)
    }
}

extension View {
    func appGlassSurface(cornerRadius: CGFloat = 20) -> some View {
        modifier(AppGlassSurfaceModifier(cornerRadius: cornerRadius))
    }

    func appGlassHostedScreenChrome() -> some View {
        background(AppGlassBackdrop())
            .toolbarBackground(.hidden, for: .navigationBar)
    }

    func appGlassSheetChrome() -> some View {
        background(AppGlassBackdrop())
            .scrollContentBackground(.hidden)
            .presentationBackground(.thinMaterial)
    }
}

struct AppGlassToolbarIcon: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 15, weight: .semibold))
            .frame(width: 36, height: 36)
            .contentShape(Circle())
            .appGlassSurface(cornerRadius: 18)
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
        HStack(spacing: 10) {
            content
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .appGlassSurface(cornerRadius: 22)
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
