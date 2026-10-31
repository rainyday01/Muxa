import Foundation

/// In-app UI language. `.system` follows the device, mirroring the Android
/// `AppLanguage` preference values ("system" / "zh" / "en").
enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system = "SYSTEM"
    case simplifiedChinese = "CHINESE"
    case english = "ENGLISH"

    var id: String {
        rawValue
    }

    var prefValue: String {
        switch self {
        case .system: "system"
        case .simplifiedChinese: "zh"
        case .english: "en"
        }
    }

    static func fromPref(_ raw: String?) -> AppLanguage {
        switch raw?.lowercased() {
        case "zh", "zh-cn", "zh-hans", "chinese":
            .simplifiedChinese
        case "en", "english":
            .english
        case "system":
            .system
        default:
            // First launch and unknown values: English, not the device locale.
            .english
        }
    }

    /// Locale used to resolve localized strings; `nil` keeps the device locale.
    var localeIdentifier: String? {
        switch self {
        case .system: nil
        case .simplifiedChinese: "zh-Hans"
        case .english: "en"
        }
    }
}
