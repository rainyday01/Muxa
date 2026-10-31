import CryptoKit
import Foundation

enum HostKeyFingerprint {
    static func sha256(_ key: Data) -> String {
        let digest = SHA256.hash(data: key)
        let encoded = Data(digest).base64EncodedString().replacingOccurrences(of: "=", with: "")
        return "SHA256:\(encoded)"
    }
}
