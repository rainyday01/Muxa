import Foundation

struct SSHObservedHostKey: Equatable, Sendable {
    let fingerprint: String
    let keyType: String
}
