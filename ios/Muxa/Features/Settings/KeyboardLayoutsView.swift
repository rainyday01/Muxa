import SwiftUI
import UniformTypeIdentifiers

struct KeyboardLayoutsView: View {
    @StateObject private var store: KeyboardLayoutStore
    @State private var isShowingNewLayout = false
    @State private var isShowingImporter = false
    @State private var isShowingExporter = false
    @State private var exportDocument = KeyboardLayoutFileDocument(data: Data())
    @State private var alertMessage: String?

    init(repository: any KeyboardLayoutRepository) {
        _store = StateObject(wrappedValue: KeyboardLayoutStore(repository: repository))
    }

    var body: some View {
        List {
            Section("Built-in") {
                ForEach(store.builtInLayouts) { layout in
                    layoutRow(layout, editable: false)
                        .contextMenu {
                            Button("Duplicate", systemImage: "plus.square.on.square") {
                                duplicate(layout)
                            }
                        }
                }
            }
            Section("Custom") {
                if store.customLayouts.isEmpty {
                    ContentUnavailableView(
                        "No Custom Layouts",
                        systemImage: "rectangle.3.group",
                        description: Text("Create or import a layout to customize the shortcut bar.")
                    )
                    .accessibilityIdentifier("keyboard-layouts.empty")
                } else {
                    ForEach(store.customLayouts) { layout in
                        NavigationLink {
                            KeyboardLayoutEditorView(layout: layout) { updated in
                                update(updated)
                            }
                        } label: {
                            layoutRow(layout, editable: true)
                        }
                        .accessibilityIdentifier("keyboard-layout.row.\(layout.id)")
                        .contextMenu {
                            Button("Duplicate", systemImage: "plus.square.on.square") {
                                duplicate(layout)
                            }
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                delete(layout)
                            }
                        }
                    }
                    .onDelete { offsets in
                        for index in offsets {
                            guard index < store.customLayouts.count else { continue }
                            delete(store.customLayouts[index])
                        }
                    }
                    .onMove { offsets, destination in
                        Task {
                            do {
                                try await store.moveCustom(fromOffsets: offsets, toOffset: destination)
                            } catch {
                                alertMessage = "The keyboard layout order could not be saved."
                            }
                        }
                    }
                }
            }
        }
        .overlay {
            if store.isLoading {
                ProgressView()
                    .accessibilityIdentifier("keyboard-layouts.loading")
            }
        }
        .navigationTitle("Keyboard Layouts")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                EditButton()
                    .accessibilityIdentifier("keyboard-layouts.edit")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("New Layout", systemImage: "plus") {
                        isShowingNewLayout = true
                    }
                    Button("Import JSON", systemImage: "square.and.arrow.down") {
                        isShowingImporter = true
                    }
                    Button("Export JSON", systemImage: "square.and.arrow.up") {
                        exportLayouts()
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel("Keyboard layout actions")
                .accessibilityIdentifier("keyboard-layouts.actions")
            }
        }
        .task {
            await store.start()
        }
        .sheet(isPresented: $isShowingNewLayout) {
            NewKeyboardLayoutView { name in
                create(name: name)
            }
        }
        .fileImporter(
            isPresented: $isShowingImporter,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            guard case let .success(urls) = result, let url = urls.first else { return }
            do {
                let data = try Data(contentsOf: url)
                Task {
                    do {
                        _ = try await store.importJSON(data)
                    } catch {
                        alertMessage = "The keyboard layout JSON could not be imported."
                    }
                }
            } catch {
                alertMessage = "The selected file could not be read."
            }
        }
        .fileExporter(
            isPresented: $isShowingExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "muxa-keyboard-layouts"
        ) { result in
            if case .failure = result {
                alertMessage = "The keyboard layouts could not be exported."
            }
        }
        .alert("Keyboard Layouts", isPresented: Binding(
            get: { alertMessage != nil },
            set: {
                if !$0 {
                    alertMessage = nil
                }
            }
        )) {
            Button("OK") { alertMessage = nil }
        } message: {
            Text(alertMessage ?? "")
        }
    }

    private func layoutRow(_ layout: KeyboardLayout, editable: Bool) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(layout.name)
                Text(
                    editable ? "Custom · \(layout.barKeys.count) bar keys" :
                        "Built-in · \(layout.barKeys.count) bar keys"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer()
            if layout.builtin {
                Image(systemName: "lock.fill")
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Built-in layout")
            }
        }
    }

    private func create(name: String) {
        Task {
            do {
                _ = try await store.create(name: name)
            } catch {
                alertMessage = "A layout name is required."
            }
        }
    }

    private func duplicate(_ layout: KeyboardLayout) {
        Task {
            do {
                _ = try await store.duplicate(layout)
            } catch {
                alertMessage = "The keyboard layout could not be duplicated."
            }
        }
    }

    private func update(_ layout: KeyboardLayout) {
        Task {
            do {
                try await store.update(layout)
            } catch {
                alertMessage = "Built-in layouts cannot be edited."
            }
        }
    }

    private func delete(_ layout: KeyboardLayout) {
        Task {
            do {
                try await store.delete(id: layout.id)
            } catch {
                alertMessage = "Built-in layouts cannot be deleted."
            }
        }
    }

    private func exportLayouts() {
        do {
            exportDocument = try KeyboardLayoutFileDocument(data: store.exportJSON())
            isShowingExporter = true
        } catch {
            alertMessage = "The keyboard layouts could not be exported."
        }
    }
}

private struct NewKeyboardLayoutView: View {
    @Environment(\.dismiss) private var dismiss
    let onCreate: (String) -> Void
    @State private var name = "My Layout"

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                    .textInputAutocapitalization(.words)
                    .accessibilityIdentifier("keyboard-layout.new.name")
            }
            .navigationTitle("New Layout")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        onCreate(name)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier("keyboard-layout.new.create")
                }
            }
        }
    }
}

private struct KeyboardLayoutEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var layout: KeyboardLayout
    let onSave: (KeyboardLayout) -> Void

    init(layout: KeyboardLayout, onSave: @escaping (KeyboardLayout) -> Void) {
        _layout = State(initialValue: layout)
        self.onSave = onSave
    }

    var body: some View {
        Form {
            Section("Layout") {
                TextField("Name", text: $layout.name)
                    .textInputAutocapitalization(.words)
                    .accessibilityIdentifier("keyboard-layout.editor.name")
            }
            keySection(title: "Shortcut Bar", keys: $layout.barKeys)
            keySection(title: "Expanded", keys: $layout.expandedKeys)
        }
        .navigationTitle(layout.name)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    onSave(layout)
                    dismiss()
                }
                .accessibilityIdentifier("keyboard-layout.editor.save")
            }
        }
    }

    private func keySection(title: String, keys: Binding<[KeyboardKey]>) -> some View {
        Section(title) {
            ForEach(keys) { key in
                KeyboardKeyEditorRow(key: key)
            }
            .onDelete { offsets in
                keys.wrappedValue.remove(atOffsets: offsets)
            }
            .onMove { offsets, destination in
                keys.wrappedValue.move(fromOffsets: offsets, toOffset: destination)
            }
            Button("Add Key", systemImage: "plus") {
                keys.wrappedValue.append(
                    KeyboardKey(
                        id: UUID().uuidString,
                        label: "New",
                        action: KeyAction(type: .text, keyCode: nil, text: "", keys: nil, modifier: nil)
                    )
                )
            }
        }
    }
}

private struct KeyboardKeyEditorRow: View {
    @Binding var key: KeyboardKey

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("Label", text: $key.label)
            Picker("Action", selection: actionType) {
                ForEach(KeyActionType.allCases, id: \.self) { type in
                    Text(type.rawValue).tag(type)
                }
            }
            switch key.action.type {
            case .key:
                TextField("Key code", text: optionalText(\.keyCode))
                    .textInputAutocapitalization(.never)
            case .text:
                TextField("Text", text: optionalText(\.text))
            case .chord:
                TextField("Keys (comma separated)", text: chordText)
                    .textInputAutocapitalization(.never)
            case .modifierLatch:
                TextField("Modifier", text: optionalText(\.modifier))
                    .textInputAutocapitalization(.never)
            }
        }
    }

    private var actionType: Binding<KeyActionType> {
        Binding(
            get: { key.action.type },
            set: { key.action.type = $0 }
        )
    }

    private var chordText: Binding<String> {
        Binding(
            get: { key.action.keys?.joined(separator: ",") ?? "" },
            set: { key.action.keys = $0.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) } }
        )
    }

    private func optionalText(_ keyPath: WritableKeyPath<KeyAction, String?>) -> Binding<String> {
        Binding(
            get: { key.action[keyPath: keyPath] ?? "" },
            set: { key.action[keyPath: keyPath] = $0.isEmpty ? nil : $0 }
        )
    }
}

private struct KeyboardLayoutFileDocument: FileDocument {
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
