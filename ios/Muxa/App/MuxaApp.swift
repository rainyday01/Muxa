import SwiftUI

@main
struct MuxaApp: App {
    @Environment(\.scenePhase) private var scenePhase
    private let container: AppContainer?
    private let startupError: String?
    @State private var appLock: AppLockController

    init() {
        #if DEBUG
            SSHLiveProbeRunner.runIfConfigured()
        #endif
        let loaded: AppContainer?
        let loadError: String?
        do {
            if ProcessInfo.processInfo.arguments.contains("-ui-testing") {
                loaded = try AppContainer.testing()
            } else {
                loaded = try AppContainer.live()
            }
            loadError = nil
        } catch {
            loaded = nil
            loadError = "Muxa could not open its local database."
        }
        container = loaded
        startupError = loadError
        _appLock = State(
            initialValue: AppLockController(preferences: loaded?.preferences ?? AppPreferencesStore.shared)
        )
    }

    var body: some Scene {
        WindowGroup {
            if let container {
                RootView(
                    hostRepository: container.hostRepository,
                    secretStore: container.secretStore,
                    knownHostStore: container.knownHostStore,
                    sshControlPlane: container.sshControlPlane,
                    environmentProbe: container.environmentProbe,
                    hostDeletionService: container.hostDeletionService,
                    tmuxSessionCache: container.tmuxSessionCache,
                    tmuxController: container.tmuxController,
                    tmuxAttachmentCoordinator: container.tmuxAttachmentCoordinator,
                    keyboardLayoutRepository: container.keyboardLayoutRepository,
                    interactiveTransportFactory: container.interactiveTransportFactory,
                    snippetRepository: container.snippetRepository,
                    historyRepository: container.historyRepository,
                    diagnostics: container.diagnostics,
                    backupService: container.backupService,
                    pathCompleter: container.pathCompleter,
                    preferences: container.preferences,
                    appLock: appLock
                )
                .overlay {
                    if appLock.isLocked {
                        AppLockCover(controller: appLock)
                    }
                }
                .muxaAppearance(container.preferences)
            } else {
                ContentUnavailableView(
                    "Muxa Unavailable",
                    systemImage: "externaldrive.badge.exclamationmark",
                    description: Text(startupError ?? "Local storage is unavailable.")
                )
            }
        }
        .onChange(of: scenePhase) { _, phase in
            guard let container else { return }
            Task {
                if phase == .background {
                    await container.connectionManager.suspend()
                    appLock.lockIfNeeded()
                }
            }
        }
    }
}

private struct AppLockCover: View {
    let controller: AppLockController

    var body: some View {
        ZStack {
            Color.black.opacity(0.92).ignoresSafeArea()
            VStack(spacing: 16) {
                Text("Muxa")
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(.white)
                    .accessibilityIdentifier("app.lock.title")
                Text("Unlock to continue")
                    .foregroundStyle(.white.opacity(0.7))
                Button("Unlock") {
                    Task { await controller.unlock() }
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("app.lock.unlock")
                if let error = controller.errorMessage {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }
        }
        .privacySensitive()
    }
}
