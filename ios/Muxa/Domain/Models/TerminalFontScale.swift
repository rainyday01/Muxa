import Foundation

/// Terminal point size that follows the viewport and the system text-size ramp.
///
/// A phone around 390pt wide prefers about 12pt, which is the size that stays
/// readable on iPhone 15 without crowding the shortcut bar. Wider canvases
/// (iPhone Duo inner display, iPad) aim for more columns instead of growing
/// the glyphs without limit. Dynamic Type shifts that fitted size.
enum TerminalFontScale {
    struct Spec: Equatable, Sendable {
        var minimum: Int
        var maximum: Int
        var preferred: Int
    }

    /// Monospace advance relative to the font's point size (SF Mono / Menlo).
    static let advanceRatio = 0.60

    static func spec(viewportWidth: Double, dynamicTypeMultiplier: Double = 1) -> Spec {
        let width = max(viewportWidth, 280)
        let columns: Double
        if width < 520 {
            columns = 54
        } else if width < 840 {
            columns = 96
        } else {
            columns = 120
        }
        let multiplier = min(2, max(0.75, dynamicTypeMultiplier))
        let fitted = (width / (columns * advanceRatio)) * multiplier
        let preferred = Int(fitted.rounded())
        let minimum = max(AppPreferenceDefaults.minimumFontSize, preferred - 5)
        let maximum = min(AppPreferenceDefaults.maximumFontSize, max(preferred + 12, minimum + 6))
        let resolved = min(maximum, max(minimum, preferred))
        return Spec(minimum: minimum, maximum: maximum, preferred: resolved)
    }
}
