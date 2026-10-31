import Foundation
import GRDB

actor GRDBKeyboardLayoutRepository: KeyboardLayoutRepository {
    private let database: MuxaDatabase

    init(database: MuxaDatabase) {
        self.database = database
    }

    func fetchAll() throws -> [KeyboardLayout] {
        try database.queue.read { database in
            try Row.fetchAll(
                database,
                sql: "SELECT document FROM keyboardLayouts ORDER BY builtin DESC, id COLLATE NOCASE"
            ).map { row in
                try JSONDecoder().decode(KeyboardLayout.self, from: row["document"])
            }
        }
    }

    func save(_ layout: KeyboardLayout) throws {
        let document = try JSONEncoder().encode(layout)
        try database.queue.write { database in
            try database.execute(
                sql: "INSERT OR REPLACE INTO keyboardLayouts (id, document, builtin, updatedAt) VALUES (?, ?, ?, ?)",
                arguments: [layout.id, document, layout.builtin, Date().timeIntervalSince1970]
            )
        }
    }

    func delete(id: String) throws {
        try database.queue.write { database in
            try database.execute(sql: "DELETE FROM keyboardLayouts WHERE id = ? AND builtin = 0", arguments: [id])
        }
    }
}
