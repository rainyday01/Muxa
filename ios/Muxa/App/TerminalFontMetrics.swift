import SwiftUI

enum TerminalFontMetrics {
    static func dynamicTypeMultiplier(_ size: DynamicTypeSize) -> Double {
        switch size {
        case .xSmall:
            0.84
        case .small:
            0.92
        case .medium:
            0.96
        case .large:
            1
        case .xLarge:
            1.08
        case .xxLarge:
            1.16
        case .xxxLarge:
            1.26
        case .accessibility1:
            1.38
        case .accessibility2:
            1.50
        case .accessibility3:
            1.62
        case .accessibility4:
            1.74
        case .accessibility5:
            1.86
        @unknown default:
            1
        }
    }
}
