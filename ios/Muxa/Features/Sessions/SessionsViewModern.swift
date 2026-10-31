import SwiftUI
import UIKit

/// Material-style session dashboard matching the Android card hierarchy on iOS.
struct SessionsView: View {
    @Environment(\.muxaTheme) private var theme
    @State private var store: SessionListStore
    @State private var isShowingNewSession = false
    @State private var selectedItem: TmuxSessionCacheItem?
    @State private var terminalItem: TmuxSessionCacheItem?
    @State private var terminationCandidate: TmuxSessionCacheItem?
    @State private var message: String?
    @State private var detailWindows = [TmuxWindow]()
    private let interactiveTransportFactory: any InteractiveTransportFactory
    private let secretStore: any SecretStoring
    private let preferences: AppPreferencesStore
    private let snippetRepository: any SnippetRepository
    private let historyRepository: any CommandHistoryRepository
    private let pathCompleter: any RemotePathCompleting
    private let keyboardLayoutRepository: any KeyboardLayoutRepository
    private let tmuxController: any TmuxControlling
    private let onAddHost: (() -> Void)?
    /// Regular-width layouts open the terminal in the detail column instead of a cover.
    private let onOpenTerminal: ((Host, TmuxSessionCacheItem) -> Void)?

    private var shouldOpenRecentAtLaunch: Bool {
        preferences.openLastSession && !ProcessInfo.processInfo.arguments.contains("-ui-testing-no-recent")
    }

    init(
        store: SessionListStore,
        controller: any TmuxControlling,
        interactiveTransportFactory: any InteractiveTransportFactory,
        secretStore: any SecretStoring,
        preferences: AppPreferencesStore = .shared,
        snippetRepository: any SnippetRepository = EmptySnippetRepository(),
        historyRepository: any CommandHistoryRepository = EmptyCommandHistoryRepository(),
        pathCompleter: any RemotePathCompleting = EmptyRemotePathCompleter(),
        keyboardLayoutRepository: any KeyboardLayoutRepository = EmptyKeyboardLayoutRepository(),
        onAddHost: (() -> Void)? = nil,
        onOpenTerminal: ((Host, TmuxSessionCacheItem) -> Void)? = nil
    ) {
        _store = State(initialValue: store)
        self.interactiveTransportFactory = interactiveTransportFactory
        self.secretStore = secretStore
        self.preferences = preferences
        self.snippetRepository = snippetRepository
        self.historyRepository = historyRepository
        self.pathCompleter = pathCompleter
        self.keyboardLayoutRepository = keyboardLayoutRepository
        tmuxController = controller
        self.onAddHost = onAddHost
        self.onOpenTerminal = onOpenTerminal
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    sessionsHeader

                    if let errorMessage = store.errorMessage {
                        Label(errorMessage, systemImage: "exclamationmark.triangle")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 8)
                            .accessibilityIdentifier("sessions.error")
                    } else if let statusMessage = store.statusMessage {
                        Text(statusMessage)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 8)
                            .accessibilityIdentifier("sessions.status")
                    }

                    if store.viewMode == .byHost {
                        ForEach(store.hostGroups) { group in
                            VStack(alignment: .leading, spacing: 10) {
                                ModernHostGroupHeader(
                                    title: group.title,
                                    resources: store.hosts.first(where: { $0.id == group.hostID })?.lastResources
                                )
                                .padding(.horizontal, 16)

                                ForEach(group.items, content: sessionCard)
                            }
                            .padding(.bottom, 10)
                        }
                    } else {
                        ForEach(store.sortedItems, content: sessionCard)
                    }

                    // Keep the last card above the floating action button and tab bar.
                    Color.clear.frame(height: 108)
                }
            }
            .id(store.orderRevision)
            .refreshable {
                await store.refresh()
            }

            if store.isLoading {
                ProgressView()
            } else if store.sortedItems.isEmpty, store.errorMessage == nil {
                if store.hosts.isEmpty {
                    ContentUnavailableView {
                        Label("No Hosts", systemImage: "server.rack")
                    } description: {
                        Text("Add an SSH host to discover its tmux sessions.")
                    } actions: {
                        Button("Add Host") { onAddHost?() }
                            .accessibilityIdentifier("sessions.add-host")
                    }
                    .accessibilityIdentifier("sessions.empty")
                } else {
                    ContentUnavailableView(
                        "No Sessions",
                        systemImage: "rectangle.stack",
                        description: Text("Saving a host loads its tmux sessions. Pull to refresh.")
                    )
                    .accessibilityIdentifier("sessions.empty")
                }
            }

            newSessionButton
        }
        .background(theme.background.ignoresSafeArea())
        .task {
            guard let item = await store.start(openRecent: shouldOpenRecentAtLaunch) else { return }
            openTerminal(item)
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .muxaScreen()
        .sheet(isPresented: $isShowingNewSession) {
            SessionCreationView(hosts: store.hosts) { host, name, template in
                isShowingNewSession = false
                Task {
                    do {
                        try await store.createSession(hostID: host.id, name: name, template: template)
                        message = "Created \(name)."
                    } catch let error as SessionListStoreError {
                        message = error.userMessage
                    } catch let error as TmuxControlError {
                        message = error.summary
                    } catch {
                        message = "The session could not be created."
                    }
                }
            }
        }
        .alert(
            "Session",
            isPresented: Binding(
                get: { message != nil },
                set: {
                    if !$0 {
                        message = nil
                    }
                }
            )
        ) {
            Button("OK") { message = nil }
        } message: {
            Text(message ?? "")
        }
        .sheet(item: $selectedItem, content: sessionDetail)
        .fullScreenCover(item: $terminalItem) { item in
            NavigationStack {
                if let host = store.host(for: item) {
                    RemoteTerminalView(
                        host: host,
                        item: item,
                        transportFactory: interactiveTransportFactory,
                        secretStore: secretStore,
                        preferences: preferences,
                        snippetRepository: snippetRepository,
                        historyRepository: historyRepository,
                        pathCompleter: pathCompleter,
                        keyboardLayoutRepository: keyboardLayoutRepository,
                        tmuxController: tmuxController,
                        onLeaveSnapshot: { text in
                            Task { await store.saveLeaveSnapshot(for: item, text: text) }
                        }
                    )
                } else {
                    ContentUnavailableView(
                        "Host Unavailable",
                        systemImage: "server.rack",
                        description: Text("The host for this session is no longer available.")
                    )
                }
            }
        }
        .confirmationDialog(
            "Terminate \(terminationCandidate?.session.name ?? "session")?",
            isPresented: Binding(
                get: { terminationCandidate != nil },
                set: {
                    if !$0 {
                        terminationCandidate = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            Button("Terminate Session", role: .destructive) {
                guard let item = terminationCandidate else { return }
                terminationCandidate = nil
                Task {
                    do {
                        try await store.terminateSession(item)
                        message = "Terminated \(item.session.name)."
                    } catch {
                        message = "The session could not be terminated."
                    }
                }
            }
            Button("Cancel", role: .cancel) {
                terminationCandidate = nil
            }
        }
    }
}

private extension SessionsView {
    var sessionsHeader: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center) {
                Text("Sessions")
                    .font(.largeTitle.weight(.regular))
                    .foregroundStyle(theme.onSurface)
                Spacer(minLength: 12)
                Button {
                    Task { selectedItem = await store.launchMostRecent() }
                } label: {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                .foregroundStyle(theme.onSurfaceMuted)
                .accessibilityLabel("Open Recent Session")
                .accessibilityIdentifier("sessions.open-recent")
                Button {
                    Task { await store.refresh() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                .foregroundStyle(theme.primary)
                .accessibilityLabel("Refresh Sessions")
                .accessibilityIdentifier("sessions.refresh")
            }

            HStack(spacing: 8) {
                ModernSessionModeMenuChip(selection: $store.viewMode)
                    .accessibilityIdentifier("sessions.view-mode")
                ModernSessionModeChip(
                    title: "Recent",
                    systemImage: "list.bullet",
                    isSelected: store.viewMode == .recent
                ) {
                    store.viewMode = .recent
                }
                .accessibilityIdentifier("sessions.recent-chip")
                .accessibilityLabel("Recent view")
            }

            Text("Local snapshot · pull to sync remote")
                .font(.caption)
                .foregroundStyle(theme.onSurfaceMuted)
                .accessibilityIdentifier("sessions.snapshot-hint")
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 14)
    }

    var newSessionButton: some View {
        Button(action: showNewSession) {
            Image(systemName: "plus")
                .font(.system(size: 20, weight: .semibold))
                .frame(width: 56, height: 56)
        }
        .buttonStyle(.plain)
        .foregroundStyle(theme.isDark ? Color(hex: 0xEADDFF) : Color(hex: 0x21005D))
        .background(
            theme.isDark ? Color(hex: 0x4F378B) : Color(hex: 0xEADDFF),
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .shadow(color: .black.opacity(theme.isDark ? 0.34 : 0.20), radius: 8, y: 4)
        .padding(.trailing, 20)
        .padding(.bottom, 18)
        .accessibilityLabel("New Session")
        .accessibilityIdentifier("sessions.new")
    }

    private func showNewSession() {
        Task {
            await store.reload()
            isShowingNewSession = true
        }
    }

    func sessionDetail(_ item: TmuxSessionCacheItem) -> some View {
        SessionDetailView(
            item: item,
            attachCommand: store.attachCommand(for: item),
            onCopyAttachCommand: {
                UIPasteboard.general.string = store.attachCommand(for: item)
                selectedItem = nil
                message = "Attach command copied."
            },
            onOpenTerminal: {
                openTerminal(item)
            },
            onRename: { name in
                Task {
                    do {
                        try await store.renameSession(item, to: name)
                        selectedItem = nil
                        message = "Renamed \(name)."
                    } catch let error as SessionListStoreError {
                        message = error.userMessage
                    } catch {
                        message = "The session could not be renamed."
                    }
                }
            },
            onTerminate: {
                selectedItem = nil
                terminationCandidate = item
            },
            onRefresh: {
                Task {
                    await store.refresh()
                    selectedItem = nil
                    message = "Snapshot refreshed."
                }
            },
            windows: detailWindows,
            onMoveSession: { movingUp in
                store.moveSession(item, movingUp: movingUp)
                message = movingUp ? "Moved session up." : "Moved session down."
            },
            onMoveHost: { movingUp in
                store.moveHost(id: item.session.hostID, movingUp: movingUp)
                message = movingUp ? "Moved host up." : "Moved host down."
            }
        )
        .task {
            detailWindows = await (try? store.listWindows(for: item)) ?? []
        }
    }

    func sessionCard(_ item: TmuxSessionCacheItem) -> some View {
        ModernSessionCard(
            item: item,
            isRecent: store.viewMode == .recent,
            resources: store.host(for: item)?.lastResources,
            onSelect: { selectedItem = item },
            onEnter: { openTerminal(item) },
            onMore: { selectedItem = item },
            recencyLabel: sessionRecencyLabel(since: store.recentDate(for: item))
        )
    }

    private func openTerminal(_ item: TmuxSessionCacheItem) {
        store.markSessionOpened(item)
        selectedItem = nil
        if let onOpenTerminal, let host = store.host(for: item) {
            onOpenTerminal(host, item)
        } else {
            terminalItem = item
        }
    }

    private func sessionRecencyLabel(since date: Date) -> String {
        let seconds = max(0, Date().timeIntervalSince(date))
        if seconds < 60 {
            return "Just now"
        }
        if seconds < 3600 {
            return "\(Int(seconds / 60))m ago"
        }
        if seconds < 86400 {
            return "\(Int(seconds / 3600))h ago"
        }
        return "\(Int(seconds / 86400))d ago"
    }
}

private extension SessionListStoreError {
    var userMessage: String {
        switch self {
        case .invalidSessionName:
            "Enter a session name."
        case .hostNotFound:
            "The selected host is no longer available."
        case .recentSessionUnavailable:
            "The recent session is unavailable. Showing the session list."
        }
    }
}
