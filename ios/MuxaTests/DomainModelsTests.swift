@testable import Muxa
import XCTest

final class DomainModelsTests: XCTestCase {
    func testHostAndTmuxModelsRoundTrip() throws {
        let pane = TmuxPane(
            sessionName: "work",
            windowIndex: 0,
            index: 1,
            active: true,
            title: "editor",
            currentCommand: "vim",
            currentPath: "/srv/app",
            width: 120,
            height: 40,
            processID: 42
        )
        let window = TmuxWindow(
            sessionName: "work",
            index: 0,
            name: "code",
            active: true,
            layout: nil,
            paneCount: 1,
            panes: [pane]
        )
        let session = TmuxSession(
            hostID: "host-1",
            name: "work",
            windowCount: 1,
            attached: true,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            lastActivityAt: nil,
            snapshotText: "$ git status",
            snapshotUpdatedAt: nil,
            hostAlias: "Lab",
            socketPath: "/tmp/tmux-1000/default",
            windows: [window]
        )

        let encoded = try JSONEncoder().encode(session)
        XCTAssertEqual(try JSONDecoder().decode(TmuxSession.self, from: encoded), session)
        XCTAssertEqual(session.windows.first?.panes.first?.id, "work\t0\t1")
    }

    func testKeyboardWireFormatMatchesSharedProtocol() throws {
        let fixture = Data(
            #"""
            {
              "id": "general",
              "name": "General",
              "version": 1,
              "barKeys": [
                {"id": "ctrl", "label": "Ctrl", "action": {"type": "modifier_latch", "modifier": "ctrl"}}
              ],
              "expandedKeys": []
            }
            """#.utf8
        )

        let layout = try JSONDecoder().decode(KeyboardLayout.self, from: fixture)

        XCTAssertEqual(layout.barKeys.first?.action.type, .modifierLatch)
        XCTAssertEqual(layout.barKeys.first?.action.modifier, "ctrl")
        XCTAssertFalse(layout.builtin)
        XCTAssertNoThrow(try JSONEncoder().encode(layout))
    }

    func testBuiltInLayoutsLatchShiftAndKeepClaudeCodexChords() {
        for layout in KeyboardLayout.builtIns {
            XCTAssertTrue(
                layout.barKeys.contains {
                    $0.action.type == .modifierLatch && $0.action.modifier?.lowercased() == "shift"
                },
                "\(layout.id) bar should latch Shift"
            )
            XCTAssertTrue(
                layout.barKeys.contains {
                    $0.action.type == .modifierLatch && $0.action.modifier?.lowercased() == "ctrl"
                },
                "\(layout.id) bar should latch Ctrl"
            )
        }
        XCTAssertEqual(KeyboardLayout.general.version, 4)
        XCTAssertEqual(KeyboardLayout.general.barKeys.dropFirst().first?.id, "enter")
        XCTAssertEqual(KeyboardLayout.cli.barKeys.dropFirst().first?.id, "enter")
        XCTAssertEqual(KeyboardLayout.tmux.barKeys.first?.id, "enter")
        XCTAssertEqual(
            KeyboardLayout.general.expandedKeys.first(where: { $0.id == "stab" })?.action.keys,
            ["Shift", "Tab"]
        )
        XCTAssertEqual(
            KeyboardLayout.general.expandedKeys.first(where: { $0.id == "ctrl-j" })?.action.keys,
            ["Control", "j"]
        )
        // Rarely used combos stay trimmed out of the expanded rows.
        XCTAssertFalse(KeyboardLayout.general.expandedKeys.contains { $0.id == "ctrl-z" })
        XCTAssertFalse(KeyboardLayout.cli.expandedKeys.contains { $0.id == "ctrl-w" })
    }

    func testSnippetAndSuggestionPreserveInsertionSemantics() {
        let snippet = Snippet(name: "Deploy", body: "deploy ${target}", tags: ["ops"], runOnSelect: false)
        let suggestion = Suggestion(
            id: snippet.id,
            label: snippet.name,
            insertion: snippet.body,
            source: .snippet,
            runAfterInsert: snippet.runOnSelect,
            score: 0.9
        )

        XCTAssertEqual(suggestion.insertion, "deploy ${target}")
        XCTAssertFalse(suggestion.runAfterInsert)
    }

    func testComposeHistoryKeepsTenNewestAndRoundTripsNewlines() {
        var items: [String] = []
        for n in 1 ... 12 {
            items = ComposeHistory.remember(items, text: "cmd\(n)")
        }
        items = ComposeHistory.remember(items, text: "cmd12")
        XCTAssertEqual(items.count, 10)
        XCTAssertEqual(items.first, "cmd12")
        XCTAssertEqual(items.last, "cmd3")
        XCTAssertEqual(ComposeHistory.payload("ls"), "ls")
        XCTAssertEqual(ComposeHistory.payload("ls\n"), "ls")
        XCTAssertEqual(ComposeHistory.payload("echo a\necho b\n"), "echo a\necho b")
        let multiline = ComposeHistory.remember([], text: "echo a\necho b")
        XCTAssertEqual(ComposeHistory.decode(ComposeHistory.encode(multiline)), multiline)

        let keys = [
            KeyboardKey(id: "esc", label: "Esc", action: KeyAction(type: .key, keyCode: "escape", text: nil, keys: nil, modifier: nil)),
            KeyboardKey(id: "enter", label: "Enter", action: KeyAction(type: .key, keyCode: "enter", text: nil, keys: nil, modifier: nil)),
            KeyboardKey(id: "tab", label: "Tab", action: KeyAction(type: .key, keyCode: "tab", text: nil, keys: nil, modifier: nil))
        ]
        let ordered = ShortcutOrder.apply(keys, order: ["enter", "missing", "esc"]) { $0.id }
        XCTAssertEqual(ordered.map(\.id), ["enter", "esc", "tab"])
        XCTAssertEqual(ShortcutOrder.move(ordered.map(\.id), index: 2, delta: -2), ["tab", "enter", "esc"])
    }

    func testBuiltInKeyboardLayoutsHaveStableNamesAndUniqueKeys() throws {
        let layouts = KeyboardLayout.builtIns

        XCTAssertEqual(layouts.map(\.name), ["General", "tmux", "CLI"])
        XCTAssertEqual(Set(layouts.map(\.id)).count, layouts.count)
        XCTAssertTrue(layouts.allSatisfy(\.builtin))
        XCTAssertTrue(layouts.allSatisfy { !$0.barKeys.isEmpty && !$0.expandedKeys.isEmpty })

        for layout in layouts {
            let data = try JSONEncoder().encode(layout)
            XCTAssertEqual(try JSONDecoder().decode(KeyboardLayout.self, from: data), layout)
        }
    }
}
