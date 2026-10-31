import SwiftUI
import UniformTypeIdentifiers

struct ConfigBackupView: View {
    let service: ConfigBackupService
    @State private var password = ""
    @State private var includePrivateKeys = false
    @State private var confirmIncludeKeys = false
    @State private var isExporting = false
    @State private var isImporting = false
    @State private var exportDocument = BackupFileDocument(data: Data())
    @State private var message: String?

    var body: some View {
        Form {
            Section("Password") {
                SecureField("Archive password", text: $password)
                    .textContentType(.password)
                    .accessibilityIdentifier("backup.password")
                Text("Uses the Android Muxa v1 archive format so the two apps can exchange hosts and snippets.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section("Export") {
                Toggle("Include private keys", isOn: $includePrivateKeys)
                    .accessibilityIdentifier("backup.include-keys")
                    .onChange(of: includePrivateKeys) { _, enabled in
                        if enabled {
                            confirmIncludeKeys = true
                        }
                    }
                Button("Export encrypted archive") {
                    Task { await exportArchive() }
                }
                .disabled(password.trimmingCharacters(in: .whitespaces).isEmpty)
                .accessibilityIdentifier("backup.export")
            }
            Section("Import") {
                Button("Import archive") {
                    isImporting = true
                }
                .disabled(password.trimmingCharacters(in: .whitespaces).isEmpty)
                .accessibilityIdentifier("backup.import")
            }
        }
        .navigationTitle("Backup")
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "muxa-config"
        ) { result in
            if case .failure = result {
                message = "The archive could not be saved."
            }
        }
        .fileImporter(
            isPresented: $isImporting,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            guard case let .success(urls) = result, let url = urls.first else { return }
            Task { await importArchive(from: url) }
        }
        .confirmationDialog(
            "Include private keys in the export?",
            isPresented: $confirmIncludeKeys,
            titleVisibility: .visible
        ) {
            Button("Include keys") { includePrivateKeys = true }
            Button("Exclude keys", role: .cancel) { includePrivateKeys = false }
        } message: {
            Text("Keys stay in the encrypted file. Anyone with the password can read them.")
        }
        .alert("Backup", isPresented: Binding(
            get: { message != nil },
            set: {
                if !$0 {
                    message = nil
                }
            }
        )) {
            Button("OK") { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    private func exportArchive() async {
        do {
            exportDocument = try await BackupFileDocument(
                data: service.exportArchive(password: password, includePrivateKeys: includePrivateKeys)
            )
            isExporting = true
        } catch {
            message = "The configuration could not be exported."
        }
    }

    private func importArchive(from url: URL) async {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed {
                url.stopAccessingSecurityScopedResource()
            }
        }
        do {
            let data = try Data(contentsOf: url)
            let result = try await service.importArchive(data, password: password)
            message = "Imported \(result.hosts) hosts and \(result.snippets) snippets."
        } catch {
            message = "The archive could not be imported. Check the password."
        }
    }
}

private struct BackupFileDocument: FileDocument {
    static let readableContentTypes = [UTType.json]
    var data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration _: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
