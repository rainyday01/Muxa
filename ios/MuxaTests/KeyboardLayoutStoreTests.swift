@testable import Muxa
import XCTest

@MainActor
final class KeyboardLayoutStoreTests: XCTestCase {
    func testSeedsBuiltInsAndSupportsCreateDuplicateEditReorderDelete() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let store = KeyboardLayoutStore(repository: GRDBKeyboardLayoutRepository(database: database))

        await store.start()
        XCTAssertEqual(store.builtInLayouts.map(\.id), ["general", "tmux", "cli"])

        let first = try await store.create(name: "Ops")
        let second = try await store.duplicate(first)
        XCTAssertEqual(second.name, "Ops Copy")
        XCTAssertEqual(store.customLayouts.map(\.name), ["Ops", "Ops Copy"])

        var edited = first
        edited.name = "Operations"
        try await store.update(edited)
        XCTAssertEqual(store.customLayouts.map(\.name), ["Operations", "Ops Copy"])

        try await store.moveCustom(fromOffsets: IndexSet(integer: 1), toOffset: 0)
        XCTAssertEqual(store.customLayouts.map(\.name), ["Ops Copy", "Operations"])

        try await store.delete(id: second.id)
        XCTAssertEqual(store.customLayouts.map(\.name), ["Operations"])
    }

    func testJSONExportImportProtectsBuiltInsAndRejectsInvalidInput() async throws {
        let database = try MuxaDatabase(inMemory: true)
        let store = KeyboardLayoutStore(repository: GRDBKeyboardLayoutRepository(database: database))
        await store.start()
        let custom = try await store.create(name: "Imported")

        let exported = try store.exportJSON()
        let document = try JSONDecoder().decode(KeyboardLayoutExportDocument.self, from: exported)
        XCTAssertEqual(document.version, 1)
        XCTAssertTrue(document.layouts.contains(where: { $0.id == custom.id }))

        let imported = try await store.importJSON(
            JSONEncoder().encode(KeyboardLayoutExportDocument(layouts: [KeyboardLayout.general, custom]))
        )
        XCTAssertEqual(imported.count, 2)
        XCTAssertTrue(imported.allSatisfy { !$0.builtin })
        XCTAssertNotEqual(imported.last?.id, custom.id)
        do {
            try await store.delete(id: "general")
            XCTFail("Built-in layouts should not be deletable")
        } catch {
            XCTAssertEqual(error as? KeyboardLayoutStoreError, .cannotDeleteBuiltIn)
        }
        do {
            _ = try await store.importJSON(Data("not-json".utf8))
            XCTFail("Invalid JSON should be rejected")
        } catch {
            XCTAssertEqual(error as? KeyboardLayoutStoreError, .invalidJSON)
        }
    }
}
