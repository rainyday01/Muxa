import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct DiagnosticsView: View {
    let logger: any DiagnosticLogging
    @State private var entries = [DiagnosticEntry]()
    @State private var note = ""
    @State private var message: String?
    @State private var isUploading = false
    @State private var isExporting = false
    @State private var exportDocument = DiagnosticFileDocument(data: Data())
    @State private var shareURL: URL?

    var body: some View {
        List {
            Section("Feedback") {
                TextField("What happened?", text: $note, axis: .vertical)
                    .lineLimit(2 ... 5)
                    .accessibilityIdentifier("diagnostics.note")
                Button(isUploading ? "Uploading…" : "Upload log") {
                    Task { await uploadLog() }
                }
                .disabled(isUploading)
                .accessibilityIdentifier("diagnostics.upload")
                Text(
                    "Upload sends a redacted log, the app version, and this note. " +
                        "Passwords and private keys are removed first."
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
            Section("Local log") {
                Button("Export JSON") {
                    Task { await exportJSON() }
                }
                .accessibilityIdentifier("diagnostics.export")
                Button("Share redacted log") {
                    Task { await prepareShare() }
                }
                .accessibilityIdentifier("diagnostics.share")
                if let shareURL {
                    ShareLink(item: shareURL, preview: SharePreview("Muxa diagnostics"))
                        .accessibilityIdentifier("diagnostics.share-link")
                }
                Button("Clear log", role: .destructive) {
                    Task {
                        await logger.clear()
                        shareURL = nil
                        await reload()
                    }
                }
                .accessibilityIdentifier("diagnostics.clear")
            }
            Section("Entries") {
                if entries.isEmpty {
                    Text("No diagnostic entries yet.")
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("diagnostics.empty")
                } else {
                    ForEach(entries.reversed()) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(entry.level.rawValue) · \(entry.category)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(entry.message)
                                .font(.footnote.monospaced())
                        }
                    }
                }
            }
        }
        .navigationTitle("Diagnostics")
        .task { await reload() }
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "muxa-diagnostics"
        ) { _ in }
        .alert("Diagnostics", isPresented: Binding(
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

    private func reload() async {
        entries = await logger.entries()
    }

    private func exportJSON() async {
        do {
            exportDocument = try await DiagnosticFileDocument(data: logger.export())
            isExporting = true
        } catch {
            message = "The diagnostic log could not be exported."
        }
    }

    private func uploadLog() async {
        isUploading = true
        defer { isUploading = false }
        do {
            let raw = try await String(data: logger.export(), encoding: .utf8) ?? ""
            let vendor = UIDevice.current.identifierForVendor?.uuidString ?? "unknown"
            let filtered = vendor.filter { $0.isLetter || $0.isNumber }
            let suffix = filtered.isEmpty ? "unknown" : String(filtered.prefix(16))
            let result = try await LogHubClient.upload(
                LogHubClient.Payload(
                    logText: raw,
                    note: note.trimmingCharacters(in: .whitespacesAndNewlines),
                    appVersion: AppVersion.display,
                    deviceID: "muxa-ios-\(suffix)",
                    osVersion: UIDevice.current.systemVersion,
                    model: UIDevice.current.model
                )
            )
            message = result.id.isEmpty ? "Log uploaded." : "Log uploaded. Reference \(result.id)."
        } catch let error as LogHubClientError {
            message = error.userMessage
        } catch {
            message = "Log upload failed."
        }
    }

    private func prepareShare() async {
        do {
            let raw = try await String(data: logger.export(), encoding: .utf8) ?? ""
            let redacted = DiagnosticRedactor.redact(message: raw)
            let trimmedNote = DiagnosticRedactor.redact(message: note.trimmingCharacters(in: .whitespacesAndNewlines))
            var body = "Muxa diagnostics\n"
            body += "version: \(AppVersion.display)\n"
            if !trimmedNote.isEmpty {
                body += "note: \(trimmedNote)\n"
            }
            body += "\n\(redacted)\n"
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("muxa-diagnostics.txt")
            try Data(body.utf8).write(to: url, options: .atomic)
            shareURL = url
        } catch {
            message = "The diagnostic package could not be prepared."
        }
    }
}

private struct DiagnosticFileDocument: FileDocument {
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
