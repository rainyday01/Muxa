import Foundation
import LocalAuthentication
import Observation

@MainActor
@Observable
final class AppLockController {
    var isLocked: Bool
    var isAuthenticating = false
    var errorMessage: String?

    private let preferences: AppPreferencesStore

    init(preferences: AppPreferencesStore) {
        self.preferences = preferences
        isLocked = preferences.appLockEnabled
    }

    func lockIfNeeded() {
        guard preferences.appLockEnabled else {
            isLocked = false
            return
        }
        isLocked = true
    }

    func unlock() async {
        guard preferences.appLockEnabled else {
            isLocked = false
            return
        }
        isAuthenticating = true
        defer { isAuthenticating = false }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            errorMessage = error?.localizedDescription ?? "Device authentication is unavailable."
            return
        }
        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthentication,
                localizedReason: "Unlock Muxa"
            )
            if success {
                isLocked = false
                errorMessage = nil
            }
        } catch {
            errorMessage = "Muxa could not be unlocked."
        }
    }
}
