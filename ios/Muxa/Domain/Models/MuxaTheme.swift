import SwiftUI

struct MuxaTheme: Equatable, Identifiable, Sendable {
    let id: String
    let displayName: String
    let isDark: Bool
    let cardRadius: CGFloat
    let background: Color
    let surface: Color
    let onSurface: Color
    let onSurfaceMuted: Color
    let primary: Color
    let outline: Color
    let card: Color

    static let materialYouDark = MuxaTheme(
        id: "sequoia",
        displayName: "Material You",
        isDark: true,
        cardRadius: 16,
        background: Color(hex: 0x131318),
        surface: Color(hex: 0x1D1B20),
        onSurface: Color(hex: 0xE4E1E9),
        onSurfaceMuted: Color(hex: 0xCAC4D0),
        primary: Color(hex: 0xD0BCFF),
        outline: Color(hex: 0x938F99),
        card: Color(hex: 0x1D1B20)
    )

    static let materialYouLight = MuxaTheme(
        id: "bigsur",
        displayName: "Material You Light",
        isDark: false,
        cardRadius: 16,
        background: Color(hex: 0xFEF7FF),
        surface: Color(hex: 0xFFFBFE),
        onSurface: Color(hex: 0x1D1B20),
        onSurfaceMuted: Color(hex: 0x49454F),
        primary: Color(hex: 0x6750A4),
        outline: Color(hex: 0x79747E),
        card: Color(hex: 0xFFFBFE)
    )

    static let spaceGrayPro = MuxaTheme(
        id: "space",
        displayName: "Space Gray Pro",
        isDark: true,
        cardRadius: 12,
        background: .black,
        surface: Color(hex: 0x1C1C1E),
        onSurface: Color(hex: 0xF5F5F7),
        onSurfaceMuted: Color(hex: 0x8E8E93),
        primary: Color(hex: 0x0A84FF),
        outline: Color(hex: 0x2C2C2E),
        card: Color(hex: 0x1C1C1E)
    )

    static let terminalPure = MuxaTheme(
        id: "terminal",
        displayName: "Terminal Pure",
        isDark: true,
        cardRadius: 8,
        background: Color(hex: 0x0C0C0C),
        surface: Color(hex: 0x161B22),
        onSurface: Color(hex: 0xE6EDF3),
        onSurfaceMuted: Color(hex: 0x7D8590),
        primary: Color(hex: 0x3FB950),
        outline: Color(hex: 0x30363D),
        card: Color(hex: 0x161B22)
    )

    static let visionLayers = MuxaTheme(
        id: "vision",
        displayName: "Vision Layers",
        isDark: true,
        cardRadius: 18,
        background: Color(hex: 0x0A0A12),
        surface: Color(hex: 0x16162A),
        onSurface: .white,
        onSurfaceMuted: Color.white.opacity(0.6),
        primary: Color(hex: 0x78A0FF),
        outline: Color.white.opacity(0.16),
        card: Color(hex: 0x16162A)
    )

    static let paperEditorial = MuxaTheme(
        id: "paper",
        displayName: "Paper Editorial",
        isDark: false,
        cardRadius: 4,
        background: Color(hex: 0xF7F4EF),
        surface: .white,
        onSurface: Color(hex: 0x1A1A1A),
        onSurfaceMuted: Color(hex: 0x8A8278),
        primary: Color(hex: 0x1A1A1A),
        outline: Color(hex: 0xE5DDD0),
        card: .white
    )

    static let all = [
        materialYouDark,
        materialYouLight,
        spaceGrayPro,
        terminalPure,
        visionLayers,
        paperEditorial
    ]

    static func named(_ id: String) -> MuxaTheme {
        all.first(where: { $0.id == id }) ?? materialYouDark
    }

    static func resolved(
        mode: AppearanceMode,
        customID: String,
        system: ColorScheme
    ) -> MuxaTheme {
        switch mode {
        case .light:
            materialYouLight
        case .dark:
            materialYouDark
        case .custom:
            named(customID)
        case .system:
            system == .dark ? materialYouDark : materialYouLight
        }
    }
}

extension EnvironmentValues {
    @Entry var muxaTheme: MuxaTheme = .materialYouDark
}

struct MuxaAppearance: ViewModifier {
    @Environment(\.colorScheme) private var systemScheme
    let preferences: AppPreferencesStore

    func body(content: Content) -> some View {
        let theme = MuxaTheme.resolved(
            mode: preferences.appearanceMode,
            customID: preferences.customThemeID,
            system: systemScheme
        )
        content
            .tint(theme.primary)
            .preferredColorScheme(forcedScheme)
            .environment(\.muxaTheme, theme)
            .environment(\.locale, resolvedLocale)
    }

    /// In-app language override; falls back to the device locale.
    private var resolvedLocale: Locale {
        preferences.appLanguage.localeIdentifier.map(Locale.init(identifier:)) ?? .current
    }

    private var forcedScheme: ColorScheme? {
        switch preferences.appearanceMode {
        case .system:
            nil
        case .light:
            .light
        case .dark:
            .dark
        case .custom:
            MuxaTheme.named(preferences.customThemeID).isDark ? .dark : .light
        }
    }
}

struct MuxaScreenBackground: ViewModifier {
    @Environment(\.muxaTheme) private var theme

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(theme.background)
            .toolbarBackground(theme.surface, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
    }
}

extension View {
    func muxaAppearance(_ preferences: AppPreferencesStore) -> some View {
        modifier(MuxaAppearance(preferences: preferences))
    }

    func muxaScreen() -> some View {
        modifier(MuxaScreenBackground())
    }

    func muxaListRow() -> some View {
        modifier(MuxaListRowBackground())
    }
}

private struct MuxaListRowBackground: ViewModifier {
    @Environment(\.muxaTheme) private var theme

    func body(content: Content) -> some View {
        content.listRowBackground(theme.card)
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}
