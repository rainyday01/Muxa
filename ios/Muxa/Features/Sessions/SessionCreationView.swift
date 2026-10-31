import SwiftUI

struct SessionCreationView: View {
    @Environment(\.dismiss) private var dismiss
    let hosts: [Host]
    let onCreate: (Host, String, TmuxSessionTemplate) -> Void

    @State private var selectedHostID: String
    @State private var sessionName = "workspace"
    @State private var selectedKind: TmuxSessionTemplateKind = .empty
    @State private var customCommand = ""
    @State private var directory = ""
    @State private var directoryMode: TmuxSessionDirectoryMode = .createAndEnter
    @State private var skipDangerousPermissions = false
    @State private var yolo = false

    init(hosts: [Host], onCreate: @escaping (Host, String, TmuxSessionTemplate) -> Void) {
        self.hosts = hosts
        self.onCreate = onCreate
        _selectedHostID = State(initialValue: hosts.first?.id ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Host") {
                    Picker("Host", selection: $selectedHostID) {
                        ForEach(hosts) { host in
                            Text(host.alias).tag(host.id)
                        }
                    }
                    .accessibilityIdentifier("session.creation.host")
                }
                Section("Session") {
                    TextField("Name", text: $sessionName)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("session.creation.name")
                    Picker("Template", selection: $selectedKind) {
                        ForEach(TmuxSessionTemplateKind.allCases) { kind in
                            Text(kind.title).tag(kind)
                        }
                    }
                    .accessibilityIdentifier("session.creation.template")
                    if selectedKind == .claude || selectedKind == .agy {
                        Toggle("Skip permission checks", isOn: $skipDangerousPermissions)
                            .accessibilityIdentifier("session.creation.skip-permissions")
                    }
                    if selectedKind == .codex {
                        Toggle("Full auto-approve", isOn: $yolo)
                            .accessibilityIdentifier("session.creation.yolo")
                    }
                    if selectedKind != .empty {
                        Text(
                            "Templates launch commands on the selected SSH host. " +
                                "Muxa does not bundle or provide these third-party CLIs."
                        )
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    }
                    if selectedKind == .claude || selectedKind == .agy, skipDangerousPermissions {
                        Text(
                            "This adds a permission-bypass flag to the remote command. " +
                                "Use it only on a host you control."
                        )
                        .font(.footnote)
                        .foregroundStyle(.orange)
                    }
                    if selectedKind == .codex, yolo {
                        Text(
                            "This enables full auto-approve on the remote Codex CLI. " +
                                "Use it only on a host you control."
                        )
                        .font(.footnote)
                        .foregroundStyle(.orange)
                    }
                    if selectedKind == .custom {
                        TextField("Command", text: $customCommand, axis: .vertical)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .accessibilityIdentifier("session.creation.command")
                    }
                }
                Section("Directory") {
                    TextField("Working directory", text: $directory)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("session.creation.directory")
                    Picker("Directory mode", selection: $directoryMode) {
                        ForEach(TmuxSessionDirectoryMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .accessibilityIdentifier("session.creation.directory-mode")
                }
            }
            .navigationTitle("New Session")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .accessibilityIdentifier("session.creation.cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        guard let host = hosts.first(where: { $0.id == selectedHostID }) else { return }
                        onCreate(
                            host,
                            sessionName,
                            TmuxSessionTemplate(
                                kind: selectedKind,
                                customCommand: customCommand,
                                directory: directory,
                                directoryMode: directoryMode,
                                skipDangerousPermissions: skipDangerousPermissions,
                                yolo: yolo
                            )
                        )
                    }
                    .disabled(!canCreate)
                    .accessibilityIdentifier("session.creation.save")
                }
            }
            .onChange(of: selectedKind) { _, _ in
                suggestSessionName()
                if selectedKind != .codex {
                    yolo = false
                }
                if selectedKind != .claude, selectedKind != .agy {
                    skipDangerousPermissions = false
                }
            }
            .onChange(of: directory) { _, _ in
                suggestSessionName()
            }
        }
    }

    private var canCreate: Bool {
        guard !selectedHostID.isEmpty,
              !sessionName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return false
        }
        if selectedKind == .custom {
            return !customCommand.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        return true
    }

    private func suggestSessionName() {
        let trimmedDirectory = directory.trimmingCharacters(in: .whitespacesAndNewlines)
        let base = trimmedDirectory
            .trimmingCharacters(in: CharacterSet(charactersIn: "/\\"))
            .split(whereSeparator: { $0 == "/" || $0 == "\\" })
            .last
            .map(String.init)
            .flatMap { $0 == "~" ? nil : $0 }
            ?? selectedKind.defaultCommand
            ?? ""
        guard !base.isEmpty else { return }
        let defaultNames = ["workspace", "work", "claude", "codex", "grok", "agy"]
        let normalizedName = sessionName.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedName.isEmpty || defaultNames.contains(normalizedName) {
            sessionName = base
        }
    }
}
