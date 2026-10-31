import SwiftUI

struct SettingsView: View {
    @Bindable var preferences: AppPreferencesStore
    let keyboardLayoutRepository: any KeyboardLayoutRepository
    let snippetRepository: any SnippetRepository
    let diagnostics: any DiagnosticLogging
    let backupService: ConfigBackupService
    let appLock: AppLockController
    var hostDeletionService: HostDeletionService?

    var body: some View {
        List {
            Section("Appearance") {
                Picker("Appearance", selection: $preferences.appearanceMode) {
                    ForEach(AppearanceMode.allCases) { mode in
                        Text(LocalizedStringKey(mode.title)).tag(mode)
                    }
                }
                .accessibilityIdentifier("settings.appearance")
                if preferences.appearanceMode == .custom {
                    ForEach(MuxaTheme.all) { theme in
                        Button {
                            preferences.customThemeID = theme.id
                        } label: {
                            HStack {
                                Circle()
                                    .fill(theme.primary)
                                    .frame(width: 14, height: 14)
                                Text(theme.displayName)
                                    .foregroundStyle(theme.onSurface)
                                Spacer()
                                if theme.id == preferences.customThemeID {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(theme.primary)
                                }
                            }
                        }
                        .accessibilityIdentifier("settings.theme.\(theme.id)")
                    }
                }
            }
            Section("Language") {
                Picker("Language", selection: $preferences.appLanguage) {
                    ForEach(AppLanguage.allCases) { language in
                        languageLabel(language).tag(language)
                    }
                }
                .accessibilityIdentifier("settings.language")
            }
            Section("Terminal") {
                NavigationLink {
                    TerminalAppearanceView(preferences: preferences)
                } label: {
                    LabeledContent("Font, colors, size", value: "\(preferences.resolvedFontSize) pt")
                }
                .accessibilityIdentifier("settings.terminal-appearance")
                Picker("TERM", selection: $preferences.termType) {
                    ForEach(AppPreferenceDefaults.termTypes, id: \.self) { type in
                        Text(type).tag(type)
                    }
                }
                .accessibilityIdentifier("settings.term-type")
                HStack {
                    Stepper(
                        value: $preferences.bufferLines,
                        in: AppPreferenceDefaults.minimumBufferLines
                            ... AppPreferenceDefaults.maximumBufferLines,
                        step: 500
                    ) {
                        Text("Scrollback")
                    }
                    TextField(
                        "Lines",
                        value: $preferences.bufferLines,
                        format: .number
                    )
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 88)
                    .accessibilityIdentifier("settings.scrollback")
                    Text("lines")
                        .foregroundStyle(.secondary)
                }
                Toggle("Keep screen awake", isOn: $preferences.preventSleeping)
                    .accessibilityIdentifier("settings.prevent-sleep")
                Toggle("Command autocomplete", isOn: $preferences.autocompleteEnabled)
                    .accessibilityIdentifier("settings.autocomplete")
                Toggle("Open last session on launch", isOn: $preferences.openLastSession)
                    .accessibilityIdentifier("settings.open-last")
                NavigationLink("Command snippets") {
                    SnippetsView(repository: snippetRepository)
                }
                .accessibilityIdentifier("settings.snippets")
                NavigationLink("Keyboard layouts") {
                    KeyboardLayoutsView(repository: keyboardLayoutRepository)
                }
                .accessibilityIdentifier("settings.keyboards")
            }
            Section("Security") {
                Toggle("Require Face ID / passcode", isOn: $preferences.appLockEnabled)
                    .accessibilityIdentifier("settings.app-lock")
                    .onChange(of: preferences.appLockEnabled) { _, enabled in
                        if enabled {
                            appLock.lockIfNeeded()
                        } else {
                            appLock.isLocked = false
                        }
                    }
            }
            Section("Backup") {
                NavigationLink("Export / import") {
                    ConfigBackupView(service: backupService)
                }
                .accessibilityIdentifier("settings.backup")
            }
            Section("Diagnostics") {
                NavigationLink("Logs and feedback") {
                    DiagnosticsView(logger: diagnostics)
                }
                .accessibilityIdentifier("settings.diagnostics")
            }
            Section("About") {
                LabeledContent("Muxa", value: AppVersion.display)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("settings.about")
                NavigationLink("Privacy") {
                    PrivacyView(
                        hostDeletionService: hostDeletionService,
                        preferences: preferences,
                        diagnostics: diagnostics
                    )
                }
                .accessibilityIdentifier("settings.privacy")
                Text("Not affiliated with the tmux project.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("settings.disclaimer")
            }
        }
        .navigationTitle("Settings")
        .muxaScreen()
    }

    /// Concrete languages keep their native names so they stay recognizable
    /// no matter which language is currently active.
    private func languageLabel(_ language: AppLanguage) -> some View {
        switch language {
        case .system:
            Text("Follow System")
        case .simplifiedChinese:
            Text(verbatim: "简体中文")
        case .english:
            Text(verbatim: "English")
        }
    }
}
