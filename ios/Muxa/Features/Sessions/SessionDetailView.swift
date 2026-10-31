import SwiftUI

struct SessionDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.muxaTheme) private var theme
    let item: TmuxSessionCacheItem
    let attachCommand: String
    let onCopyAttachCommand: () -> Void
    let onOpenTerminal: () -> Void
    let onRename: (String) -> Void
    let onTerminate: () -> Void
    let onRefresh: () -> Void
    let windows: [TmuxWindow]
    let onMoveSession: ((Bool) -> Void)?
    let onMoveHost: ((Bool) -> Void)?

    @State private var isRenaming = false
    @State private var editedName: String

    init(
        item: TmuxSessionCacheItem,
        attachCommand: String,
        onCopyAttachCommand: @escaping () -> Void,
        onOpenTerminal: @escaping () -> Void,
        onRename: @escaping (String) -> Void,
        onTerminate: @escaping () -> Void,
        onRefresh: @escaping () -> Void,
        windows: [TmuxWindow] = [],
        onMoveSession: ((Bool) -> Void)? = nil,
        onMoveHost: ((Bool) -> Void)? = nil
    ) {
        self.item = item
        self.attachCommand = attachCommand
        self.onCopyAttachCommand = onCopyAttachCommand
        self.onOpenTerminal = onOpenTerminal
        self.onRename = onRename
        self.onTerminate = onTerminate
        self.onRefresh = onRefresh
        self.windows = windows
        self.onMoveSession = onMoveSession
        self.onMoveHost = onMoveHost
        _editedName = State(initialValue: item.session.name)
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Session") {
                    if isRenaming {
                        TextField("Name", text: $editedName)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .accessibilityIdentifier("session.detail.name")
                        Button("Save Name") {
                            onRename(editedName)
                            isRenaming = false
                        }
                        .accessibilityIdentifier("session.detail.save-rename")
                    } else {
                        LabeledContent("Name", value: item.session.name)
                        LabeledContent("Windows", value: String(item.session.windowCount))
                        Button("Rename") {
                            isRenaming = true
                        }
                        .accessibilityIdentifier("session.detail.rename")
                    }
                    Button("Refresh Snapshot") {
                        onRefresh()
                    }
                    .accessibilityIdentifier("session.detail.refresh")
                }
                Section("Snapshot") {
                    if let snapshot = item.session.snapshotText, !snapshot.isEmpty {
                        ScrollView([.horizontal, .vertical]) {
                            Text(SnapshotText.forStore(snapshot) ?? snapshot)
                                .font(.system(size: 11, weight: .regular, design: .monospaced))
                                .foregroundStyle(theme.isDark ? theme.onSurface : Color(hex: 0xA9B1D6))
                                .fixedSize(horizontal: true, vertical: true)
                                .textSelection(.enabled)
                                .padding(10)
                                .accessibilityIdentifier("session.detail.snapshot")
                        }
                        .frame(maxWidth: .infinity, minHeight: 72, maxHeight: 260, alignment: .leading)
                        .background(
                            theme.isDark ? Color(hex: 0x0E0E12) : Color(hex: 0x1A1B26),
                            in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                        )
                    } else {
                        Text("No snapshot available.")
                            .foregroundStyle(.secondary)
                    }
                    if item.isStale {
                        Label("Snapshot is stale", systemImage: "clock.arrow.circlepath")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
                Section("Attach") {
                    Text(attachCommand)
                        .font(.caption.monospaced())
                        .textSelection(.enabled)
                        .lineLimit(2)
                        .accessibilityIdentifier("session.detail.attach-command")
                    Button("Copy Attach Command") {
                        onCopyAttachCommand()
                    }
                    .accessibilityIdentifier("session.detail.copy-attach")
                    Button("Open Terminal") {
                        onOpenTerminal()
                    }
                    .accessibilityIdentifier("session.detail.open-terminal")
                }
                Section {
                    Button("Terminate Session", role: .destructive) {
                        onTerminate()
                    }
                    .accessibilityIdentifier("session.detail.terminate")
                }
                Section("Windows") {
                    if windows.isEmpty {
                        Text("No windows listed.")
                            .foregroundStyle(.secondary)
                            .accessibilityIdentifier("session.detail.windows.empty")
                    } else {
                        ForEach(windows) { window in
                            LabeledContent(
                                "\(window.index): \(window.name)",
                                value: "\(window.paneCount) panes\(window.active ? " · active" : "")"
                            )
                            .accessibilityIdentifier("session.detail.window.\(window.index)")
                        }
                    }
                }
                if onMoveSession != nil || onMoveHost != nil {
                    Section("Order") {
                        if let onMoveSession {
                            Button("Move Session Up") { onMoveSession(true) }
                                .accessibilityIdentifier("session.detail.move-session-up")
                            Button("Move Session Down") { onMoveSession(false) }
                                .accessibilityIdentifier("session.detail.move-session-down")
                        }
                        if let onMoveHost {
                            Button("Move Host Up") { onMoveHost(true) }
                                .accessibilityIdentifier("session.detail.move-host-up")
                            Button("Move Host Down") { onMoveHost(false) }
                                .accessibilityIdentifier("session.detail.move-host-down")
                        }
                    }
                }
            }
            .muxaScreen()
            .navigationTitle(item.session.name)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                        .accessibilityIdentifier("session.detail.done")
                }
            }
        }
    }
}
