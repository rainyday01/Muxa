import SwiftUI
import UniformTypeIdentifiers

struct HostEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store: HostEditorStore
    @State private var isImportingPrivateKey = false
    private let onSaved: (Host) -> Void

    init(
        host: Host?,
        repository: any HostRepository,
        secretStore: any SecretStoring,
        onSaved: @escaping (Host) -> Void
    ) {
        _store = State(
            initialValue: HostEditorStore(host: host, repository: repository, secretStore: secretStore)
        )
        self.onSaved = onSaved
    }

    var body: some View {
        Form {
            identitySection
            authenticationSection
            advancedSection
            if let errorMessage = store.errorMessage {
                Section {
                    Label(errorMessage, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("host.editor.error")
                }
            }
        }
        .navigationTitle(store.isEditing ? "Edit Host" : "Add Host")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(store.isSaving)
        .fileImporter(
            isPresented: $isImportingPrivateKey,
            allowedContentTypes: [.plainText, .data],
            allowsMultipleSelection: false
        ) { result in
            if case let .success(urls) = result, let url = urls.first {
                store.importPrivateKey(from: url)
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
                    .disabled(store.isSaving)
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    Task {
                        if await store.save(), let host = store.savedHost {
                            onSaved(host)
                            dismiss()
                        }
                    }
                }
                .disabled(store.isSaving)
                .accessibilityIdentifier("host.editor.save")
            }
        }
    }

    private var identitySection: some View {
        Section("Identity") {
            TextField("Alias", text: $store.alias)
                .textContentType(.organizationName)
                .accessibilityIdentifier("host.editor.alias")
            TextField("Host or IP Address", text: $store.hostname)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .accessibilityIdentifier("host.editor.hostname")
            TextField("Username", text: $store.username)
                .textContentType(.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityIdentifier("host.editor.username")
        }
    }

    private var authenticationSection: some View {
        Section("Authentication") {
            Picker("Method", selection: $store.authentication) {
                ForEach(HostAuthenticationMethod.allCases) { method in
                    Text(method.title).tag(method)
                }
            }
            if store.authentication.usesPassword {
                SecureField(
                    store.isEditing ? "Password (leave blank to keep)" : "Password",
                    text: $store.password
                )
                // UI tests use disposable credentials; suppress the simulator's
                // system "Save Password" sheet so it cannot block the app tree.
                .textContentType(
                    ProcessInfo.processInfo.arguments.contains("-ui-testing") ? .oneTimeCode : .password
                )
                .accessibilityIdentifier("host.editor.password")
            }
            if store.authentication.usesPrivateKey {
                Button {
                    isImportingPrivateKey = true
                } label: {
                    Label("Import Private Key", systemImage: "doc.badge.plus")
                }
                .accessibilityIdentifier("host.editor.importPrivateKey")
                TextEditor(text: $store.privateKey)
                    .frame(minHeight: 110)
                    .font(.body.monospaced())
                    .accessibilityLabel("Private Key")
                    .accessibilityIdentifier("host.editor.privateKey")
                    .onChange(of: store.privateKey) {
                        store.inspectPrivateKey()
                    }
                if let inspection = store.privateKeyInspection {
                    Label(inspection.summary, systemImage: "key.horizontal")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("host.editor.privateKeyType")
                }
                SecureField("Key Passphrase (optional)", text: $store.passphrase)
                    .accessibilityIdentifier("host.editor.passphrase")
            }
        }
    }

    private var advancedSection: some View {
        Section("Advanced") {
            TextField("22", text: $store.port)
                .keyboardType(.numberPad)
                .accessibilityIdentifier("host.editor.port")
        }
    }
}
