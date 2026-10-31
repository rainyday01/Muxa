import Combine
import Foundation

struct KeyboardLayoutExportDocument: Codable, Equatable, Sendable {
    let version: Int
    let layouts: [KeyboardLayout]

    init(version: Int = 1, layouts: [KeyboardLayout]) {
        self.version = version
        self.layouts = layouts
    }
}

enum KeyboardLayoutStoreError: Error, Equatable, Sendable {
    case invalidName
    case invalidJSON
    case cannotDeleteBuiltIn
    case cannotEditBuiltIn
    case layoutNotFound
}

@MainActor
final class KeyboardLayoutStore: ObservableObject {
    @Published private(set) var layouts = [KeyboardLayout]()
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private let repository: any KeyboardLayoutRepository
    private var hasStarted = false

    init(repository: any KeyboardLayoutRepository) {
        self.repository = repository
    }

    var builtInLayouts: [KeyboardLayout] {
        layouts.filter(\.builtin)
    }

    var customLayouts: [KeyboardLayout] {
        layouts.filter { !$0.builtin }
    }

    func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        await reload()
    }

    func reload() async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await ensureBuiltIns()
            layouts = try await repository.fetchAll().sorted(by: Self.sortLayouts)
            errorMessage = nil
        } catch {
            errorMessage = "Keyboard layouts could not be loaded."
        }
    }

    func create(name: String, copying source: KeyboardLayout? = nil) async throws -> KeyboardLayout {
        let trimmedName = try validName(name)
        let order = customLayouts.count
        let layout = KeyboardLayout(
            id: UUID().uuidString,
            name: trimmedName,
            version: source?.version ?? 1,
            barKeys: source?.barKeys ?? KeyboardLayout.general.barKeys,
            expandedKeys: source?.expandedKeys ?? KeyboardLayout.general.expandedKeys,
            builtin: false,
            sortOrder: order
        )
        try await repository.save(layout)
        await reload()
        return layout
    }

    func duplicate(_ layout: KeyboardLayout) async throws -> KeyboardLayout {
        try await create(name: "\(layout.name) Copy", copying: layout)
    }

    func update(_ layout: KeyboardLayout) async throws {
        guard !layout.builtin else { throw KeyboardLayoutStoreError.cannotEditBuiltIn }
        _ = try validName(layout.name)
        try await repository.save(layout)
        await reload()
    }

    func delete(id: String) async throws {
        guard let layout = layouts.first(where: { $0.id == id }) else {
            throw KeyboardLayoutStoreError.layoutNotFound
        }
        guard !layout.builtin else { throw KeyboardLayoutStoreError.cannotDeleteBuiltIn }
        try await repository.delete(id: id)
        await reload()
    }

    func moveCustom(fromOffsets offsets: IndexSet, toOffset destination: Int) async throws {
        var custom = customLayouts
        let moving = offsets.sorted().map { custom[$0] }
        let remaining = custom.enumerated().compactMap { index, layout in
            offsets.contains(index) ? nil : layout
        }
        let adjustment = offsets.filter { $0 < destination }.count
        let insertionIndex = min(max(0, destination - adjustment), remaining.count)
        custom = Array(remaining[..<insertionIndex]) + moving + Array(remaining[insertionIndex...])

        for (index, layout) in custom.enumerated() {
            var updated = layout
            updated.sortOrder = index
            try await repository.save(updated)
        }
        await reload()
    }

    func exportJSON() throws -> Data {
        let document = KeyboardLayoutExportDocument(layouts: layouts)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(document)
    }

    @discardableResult
    func importJSON(_ data: Data) async throws -> [KeyboardLayout] {
        let decoder = JSONDecoder()
        let document: KeyboardLayoutExportDocument
        if let decoded = try? decoder.decode(KeyboardLayoutExportDocument.self, from: data) {
            document = decoded
        } else if let layouts = try? decoder.decode([KeyboardLayout].self, from: data) {
            document = KeyboardLayoutExportDocument(layouts: layouts)
        } else if let layout = try? decoder.decode(KeyboardLayout.self, from: data) {
            document = KeyboardLayoutExportDocument(layouts: [layout])
        } else {
            throw KeyboardLayoutStoreError.invalidJSON
        }

        var existingIDs = Set(layouts.map(\.id))
        var imported = [KeyboardLayout]()
        var nextOrder = customLayouts.count
        for layout in document.layouts {
            let id = existingIDs.contains(layout.id) || layout.builtin ? UUID().uuidString : layout.id
            existingIDs.insert(id)
            let sanitized = KeyboardLayout(
                id: id,
                name: (try? validName(layout.name)) ?? "Imported Layout",
                version: layout.version,
                barKeys: layout.barKeys,
                expandedKeys: layout.expandedKeys,
                builtin: false,
                sortOrder: nextOrder
            )
            nextOrder += 1
            try await repository.save(sanitized)
            imported.append(sanitized)
        }
        await reload()
        return imported
    }

    private func ensureBuiltIns() async throws {
        let existing = try await repository.fetchAll()
        var existingIDs = Set(existing.map(\.id))
        for (index, layout) in KeyboardLayout.builtIns.enumerated() {
            // Re-seed a built-in when the shipped definition is newer than the
            // stored copy, so layout revisions reach existing installs. Custom
            // copies keep the version they were duplicated from.
            if let stored = existing.first(where: { $0.id == layout.id }), stored.version >= layout.version {
                continue
            }
            var seeded = layout
            seeded.sortOrder = index
            try await repository.save(seeded)
            existingIDs.insert(layout.id)
        }
    }

    private func validName(_ name: String) throws -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 80 else {
            throw KeyboardLayoutStoreError.invalidName
        }
        return trimmed
    }

    private static func sortLayouts(_ lhs: KeyboardLayout, _ rhs: KeyboardLayout) -> Bool {
        if lhs.builtin != rhs.builtin {
            return lhs.builtin
        }
        if lhs.builtin {
            return builtInOrder(lhs) < builtInOrder(rhs)
        }
        if lhs.sortOrder != rhs.sortOrder {
            return lhs.sortOrder < rhs.sortOrder
        }
        return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
    }

    private static func builtInOrder(_ layout: KeyboardLayout) -> Int {
        KeyboardLayout.builtIns.firstIndex(where: { $0.id == layout.id }) ?? Int.max
    }
}
