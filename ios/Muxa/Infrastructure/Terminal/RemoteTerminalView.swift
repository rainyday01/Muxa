import SwiftUI

struct RemoteTerminalView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.muxaTheme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var session: RemoteTerminalSession
    @State private var didLeave = false
    private let onLeaveSnapshot: ((String) -> Void)?

    init(
        host: Host,
        item: TmuxSessionCacheItem,
        transportFactory: any InteractiveTransportFactory,
        secretStore: any SecretStoring,
        preferences: AppPreferencesStore = .shared,
        snippetRepository: (any SnippetRepository)? = nil,
        historyRepository: (any CommandHistoryRepository)? = nil,
        pathCompleter: (any RemotePathCompleting)? = nil,
        keyboardLayoutRepository: (any KeyboardLayoutRepository)? = nil,
        tmuxController: (any TmuxControlling)? = nil,
        onLeaveSnapshot: ((String) -> Void)? = nil
    ) {
        let target = TmuxAttachTarget(
            hostID: item.session.hostID,
            socketPath: item.session.socketPath,
            sessionName: item.session.name
        )
        self.onLeaveSnapshot = onLeaveSnapshot
        _session = StateObject(
            wrappedValue: RemoteTerminalSession(
                host: host,
                target: target,
                preference: .ssh,
                initialSnapshot: item.session.snapshotText.map { Data($0.utf8) },
                transportFactory: transportFactory,
                secretStore: secretStore,
                preferences: preferences,
                snippetRepository: snippetRepository,
                historyRepository: historyRepository,
                pathCompleter: pathCompleter,
                keyboardLayoutRepository: keyboardLayoutRepository,
                tmuxController: tmuxController
            )
        )
    }

    init(
        launch: RemoteTerminalLaunch,
        transportFactory: any InteractiveTransportFactory,
        secretStore: any SecretStoring,
        preferences: AppPreferencesStore = .shared
    ) {
        onLeaveSnapshot = nil
        _session = StateObject(
            wrappedValue: RemoteTerminalSession(
                launch: launch,
                preference: .ssh,
                transportFactory: transportFactory,
                secretStore: secretStore,
                preferences: preferences
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            sessionChrome

            if !session.suggestions.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(session.suggestions) { suggestion in
                            Button(suggestion.label) {
                                session.applySuggestion(suggestion)
                            }
                            .font(.caption)
                            .foregroundStyle(Color(red: 229 / 255, green: 229 / 255, blue: 234 / 255))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("terminal.suggestion.\(suggestion.id)")
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                }
                .background(Color(red: 44 / 255, green: 44 / 255, blue: 46 / 255))
                .accessibilityIdentifier("terminal.suggestions")
            }

            TerminalViewport(
                controller: session.controller,
                focusOnAppear: false,
                terminalAccessibilityIdentifier: "remote.terminal",
                onGeometryChange: { geometry in
                    session.updateGeometry(geometry)
                },
                onFontStep: { delta in
                    adjustFont(by: delta)
                },
                onViewportWidth: { width in
                    let before = session.preferences.resolvedFontSize
                    session.preferences.noteTerminalWidth(Double(width))
                    if session.preferences.resolvedFontSize != before {
                        session.applyAppearance()
                    }
                },
                onHostReady: { host in
                    host.onAltFingerScroll = { scrollUp in
                        session.handleAltFingerScroll(scrollUp: scrollUp)
                    }
                }
            )
            .background(Color.black)
        }
        .background(Color.black)
        .preferredColorScheme(.dark)
        .navigationTitle(navigationTitleText)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .alert(
            "Terminal",
            isPresented: Binding(
                get: { session.errorMessage != nil },
                set: {
                    if !$0 {
                        session.clearError()
                    }
                }
            ),
            presenting: session.errorMessage
        ) { _ in
            Button("OK", role: .cancel) {}
        } message: { message in
            Text(message)
        }
        .task {
            await session.start()
        }
        .onDisappear {
            leave()
        }
        .onChange(of: scenePhase) { _, phase in
            Task { await session.scenePhaseChanged(phase) }
        }
        .onChange(of: session.preferences.resolvedFontSize) { _, _ in
            session.applyAppearance()
        }
    }

    private var sessionChrome: some View {
        HStack(spacing: 6) {
            Button(action: leave) {
                Text("Done")
                    .font(.subheadline.weight(.medium))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("terminal.done")

            Text(chromeTitle)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .truncationMode(.middle)
                .foregroundStyle(theme.onSurface)

            Spacer(minLength: 4)

            Button {
                adjustFont(by: -1)
            } label: {
                Image(systemName: "textformat.size.smaller")
                    .font(.caption)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Smaller font")
            .accessibilityIdentifier("terminal.font.decrease")
            Text("\(session.preferences.resolvedFontSize)")
                .font(.caption2.monospacedDigit())
                .foregroundStyle(theme.onSurfaceMuted)
                .accessibilityIdentifier("terminal.font.size")
            Button {
                adjustFont(by: 1)
            } label: {
                Image(systemName: "textformat.size.larger")
                    .font(.caption)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Larger font")
            .accessibilityIdentifier("terminal.font.increase")
            Image(systemName: session.networkStatus.systemImage)
                .font(.caption2)
                .foregroundStyle(session.networkStatus == .unavailable ? Color.orange : theme.onSurfaceMuted)
                .accessibilityLabel(session.networkStatus.title)
                .accessibilityIdentifier("remote.network-status")
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 2)
        .frame(maxWidth: .infinity, minHeight: 30)
        .background(theme.surface)
    }

    private var chromeTitle: String {
        switch session.transportState {
        case .connected:
            navigationTitleText
        default:
            session.statusText
        }
    }

    private var navigationTitleText: String {
        let name = session.title
        let alias = session.host.alias.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !alias.isEmpty, alias != name else { return name }
        return "\(alias) · \(name)"
    }

    private func adjustFont(by delta: Int) {
        session.preferences.nudgeResolvedFont(by: delta)
        session.applyAppearance()
    }

    private func leave() {
        guard !didLeave else { return }
        didLeave = true
        if let text = session.leaveSnapshotText() {
            onLeaveSnapshot?(text)
        }
        Task { await session.close() }
        dismiss()
    }
}
