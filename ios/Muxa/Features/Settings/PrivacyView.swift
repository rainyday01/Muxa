import SwiftUI

struct PrivacyView: View {
    static let policyURL = URL(string: "https://muxaprivacy.xcodeuk.com")!

    var hostDeletionService: HostDeletionService?
    var preferences: AppPreferencesStore?
    var diagnostics: (any DiagnosticLogging)?
    @State private var confirmDeleteAll = false
    @State private var deleteMessage: String?

    var body: some View {
        List {
            Section {
                Text("Muxa")
                    .font(.title.weight(.semibold))
                    .accessibilityIdentifier("privacy.title")
                Text(AppVersion.display)
                    .foregroundStyle(.secondary)
            }
            section(
                "What we collect",
                "By default, nothing. Muxa has no accounts and no Muxa-operated servers. " +
                    "Hosts, snippets, keyboard layouts, and preferences stay on this device. " +
                    "Passwords, private keys, and related secrets stay in the iOS Keychain " +
                    "(This Device Only, no iCloud Keychain sharing)."
            )
            section(
                "Network",
                "Session traffic goes only between this device and the SSH hosts you configure. " +
                    "Session content does not pass through Muxa-operated servers. " +
                    "Upload log is separate: it sends a redacted diagnostic package only when you tap it."
            )
            section(
                "Optional diagnostics",
                "Upload log sends the redacted local log, app version, device identifier, and your note. " +
                    "Export or share still creates a file on this device and you choose where it goes. " +
                    "Passwords and private keys are removed before either path."
            )
            section(
                "Third parties and ads",
                "Muxa does not sell personal data and does not embed advertising or analytics SDKs."
            )
            section(
                "Deletion",
                "Deleting a host removes that host’s Keychain secrets and cached session data. " +
                    "Delete All Data on This Device removes saved hosts, credentials, known-host records, " +
                    "session snapshots, command history, custom snippets, custom keyboard layouts, " +
                    "diagnostic entries, and preferences. Built-in keyboard layouts remain available. " +
                    "This action does not delete data on your SSH servers. " +
                    "Uninstalling Muxa also removes the This-Device-Only Keychain items because " +
                    "they are not in a shared Keychain access group."
            )
            Section("Privacy policy") {
                Link("Open privacy policy", destination: Self.policyURL)
                    .accessibilityIdentifier("privacy.policy-link")
                Text(Self.policyURL.absoluteString)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
            if hostDeletionService != nil {
                Section("This device") {
                    Button("Delete all data on this device", role: .destructive) {
                        confirmDeleteAll = true
                    }
                    .accessibilityIdentifier("privacy.delete-all")
                }
            }
        }
        .navigationTitle("Privacy")
        .confirmationDialog(
            "Delete all Muxa data on this device? Saved hosts, credentials, history, and preferences will be removed.",
            isPresented: $confirmDeleteAll,
            titleVisibility: .visible
        ) {
            Button("Delete all data", role: .destructive) {
                Task { await wipeAll() }
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Privacy", isPresented: Binding(
            get: { deleteMessage != nil },
            set: {
                if !$0 {
                    deleteMessage = nil
                }
            }
        )) {
            Button("OK") { deleteMessage = nil }
        } message: {
            Text(deleteMessage ?? "")
        }
    }

    private func wipeAll() async {
        guard let hostDeletionService else { return }
        do {
            try await hostDeletionService.deleteAllData()
            preferences?.resetToDefaults()
            await diagnostics?.clear()
            deleteMessage = "All Muxa data on this device was deleted."
        } catch {
            deleteMessage = "Muxa could not delete all local data."
        }
    }

    private func section(_ title: String, _ body: String) -> some View {
        Section(title) {
            Text(body)
                .font(.body)
                .foregroundStyle(.secondary)
        }
    }
}
