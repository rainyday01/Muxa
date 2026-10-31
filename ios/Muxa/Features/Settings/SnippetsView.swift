import SwiftUI

@MainActor
@Observable
final class SnippetStore {
    private(set) var snippets = [Snippet]()
    var errorMessage: String?
    private let repository: any SnippetRepository

    init(repository: any SnippetRepository) {
        self.repository = repository
    }

    func start() async {
        await reload()
    }

    func reload() async {
        do {
            snippets = try await repository.fetchAll()
            errorMessage = nil
        } catch {
            errorMessage = "Snippets could not be loaded."
        }
    }

    func save(_ snippet: Snippet) async {
        do {
            try await repository.save(snippet)
            await reload()
        } catch {
            errorMessage = "The snippet could not be saved."
        }
    }

    func delete(id: String) async {
        do {
            try await repository.delete(id: id)
            await reload()
        } catch {
            errorMessage = "The snippet could not be deleted."
        }
    }
}

struct SnippetsView: View {
    @State private var store: SnippetStore
    @State private var draft = Snippet(name: "", body: "")
    @State private var isEditing = false

    init(repository: any SnippetRepository) {
        _store = State(initialValue: SnippetStore(repository: repository))
    }

    var body: some View {
        List {
            if store.snippets.isEmpty {
                ContentUnavailableView(
                    "No Snippets",
                    systemImage: "text.badge.plus",
                    description: Text("Save shell snippets and insert them from the terminal.")
                )
                .accessibilityIdentifier("snippets.empty")
            } else {
                ForEach(store.snippets) { snippet in
                    Button {
                        draft = snippet
                        isEditing = true
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(snippet.name)
                            Text(snippet.body)
                                .font(.caption.monospaced())
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                        }
                    }
                    .accessibilityIdentifier("snippets.row.\(snippet.id)")
                }
                .onDelete { offsets in
                    for index in offsets where index < store.snippets.count {
                        Task { await store.delete(id: store.snippets[index].id) }
                    }
                }
            }
        }
        .navigationTitle("Snippets")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    draft = Snippet(name: "", body: "")
                    isEditing = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityIdentifier("snippets.add")
            }
        }
        .task { await store.start() }
        .sheet(isPresented: $isEditing) {
            SnippetEditorView(snippet: $draft) { snippet in
                isEditing = false
                Task { await store.save(snippet) }
            }
        }
        .alert("Snippets", isPresented: Binding(
            get: { store.errorMessage != nil },
            set: {
                if !$0 {
                    store.errorMessage = nil
                }
            }
        )) {
            Button("OK") { store.errorMessage = nil }
        } message: {
            Text(store.errorMessage ?? "")
        }
    }
}

private struct SnippetEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var snippet: Snippet
    let onSave: (Snippet) -> Void
    @State private var tagsText = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $snippet.name)
                    .accessibilityIdentifier("snippet.editor.name")
                TextField("Command", text: $snippet.body, axis: .vertical)
                    .font(.body.monospaced())
                    .lineLimit(3 ... 8)
                    .accessibilityIdentifier("snippet.editor.body")
                TextField("Tags", text: $tagsText)
                    .textInputAutocapitalization(.never)
                Toggle("Run after insert", isOn: $snippet.runOnSelect)
                Text("Use {{host}}, {{user}}, {{port}}, and {{session}} for substitution.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .navigationTitle(snippet.name.isEmpty ? "New Snippet" : snippet.name)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        snippet.tags = tagsText.split(separator: ",")
                            .map { $0.trimmingCharacters(in: .whitespaces) }
                            .filter { !$0.isEmpty }
                        onSave(snippet)
                    }
                    .disabled(snippet.name.trimmingCharacters(in: .whitespaces).isEmpty
                        || snippet.body.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityIdentifier("snippet.editor.save")
                }
            }
            .onAppear {
                tagsText = snippet.tags.joined(separator: ", ")
            }
        }
    }
}
