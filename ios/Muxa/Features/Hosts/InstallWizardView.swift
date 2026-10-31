import SwiftUI
import UIKit

struct InstallWizardView: View {
    let host: Host
    let snapshot: RemoteEnvironmentSnapshot?
    let onOpenInstallTerminal: (String) -> Void
    let onReprobe: () -> Void

    private var command: String {
        InstallCommands.command(for: snapshot?.packageManager ?? host.packageManager)
    }

    var body: some View {
        List {
            Section("Environment") {
                labeled("tmux", snapshot?.tmuxInstalled ?? host.tmuxInstalled == true ? "Installed" : "Missing")
                labeled("Transport", "SSH")
                if let snapshot {
                    labeled("System", systemLabel(snapshot))
                }
            }
            if snapshot?.operatingSystem == .macOS || host.operatingSystem == .macOS {
                Section("Same sessions on this Mac") {
                    Text("Add these lines to ~/.zshenv (bash: ~/.bashrc). Then open a new Terminal or SSH window. tmux ls uses the same server as Muxa.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text(InstallCommands.desktopTakeoverSnippet)
                        .font(.caption.monospaced())
                        .textSelection(.enabled)
                        .accessibilityIdentifier("install.takeover")
                    Button("Copy shell lines") {
                        UIPasteboard.general.string = InstallCommands.desktopTakeoverSnippet
                    }
                    .accessibilityIdentifier("install.takeover.copy")
                }
            }
            Section("Install command") {
                Text(InstallCommands.tip(
                    tmuxInstalled: snapshot?.tmuxInstalled ?? host.tmuxInstalled == true
                ))
                .font(.footnote)
                .foregroundStyle(.secondary)
                Text(command)
                    .font(.caption.monospaced())
                    .textSelection(.enabled)
                    .accessibilityIdentifier("install.command")
                Button("Copy command") {
                    UIPasteboard.general.string = command
                }
                .accessibilityIdentifier("install.copy")
                Button("Open install terminal") {
                    onOpenInstallTerminal(command)
                }
                .accessibilityIdentifier("install.open-terminal")
                Button("Probe again") {
                    onReprobe()
                }
                .accessibilityIdentifier("install.reprobe")
            }
        }
        .navigationTitle("Install · \(host.alias)")
    }

    private func systemLabel(_ snapshot: RemoteEnvironmentSnapshot) -> String {
        var parts = [snapshot.operatingSystem.rawValue, snapshot.packageManager.rawValue]
        parts.append("\(snapshot.latencyMilliseconds) ms")
        return parts.joined(separator: " · ")
    }

    private func labeled(_ title: String, _ value: String) -> some View {
        LabeledContent(title, value: value)
    }
}
