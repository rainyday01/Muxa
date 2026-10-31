import Foundation
import GRDB

actor GRDBCommandHistoryRepository: CommandHistoryRepository {
    private let database: MuxaDatabase

    init(database: MuxaDatabase) {
        self.database = database
    }

    func record(hostID: String, command: String, usedAt: Date) throws {
        try database.queue.write { database in
            try database.execute(
                sql: "INSERT INTO commandHistory (hostID, command, usedAt) VALUES (?, ?, ?)",
                arguments: [hostID, command, usedAt.timeIntervalSince1970]
            )
        }
    }

    func fetchRecent(hostID: String, limit: Int) throws -> [CommandHistoryEntry] {
        try database.queue.read { database in
            try Row.fetchAll(
                database,
                sql: "SELECT id, hostID, command, usedAt FROM commandHistory " +
                    "WHERE hostID = ? ORDER BY usedAt DESC, id DESC LIMIT ?",
                arguments: [hostID, max(0, limit)]
            ).map { row in
                CommandHistoryEntry(
                    id: row["id"],
                    hostID: row["hostID"],
                    command: row["command"],
                    usedAt: Date(timeIntervalSince1970: row["usedAt"])
                )
            }
        }
    }

    func prune(hostID: String, keeping limit: Int) throws {
        try database.queue.write { database in
            try database.execute(
                sql: "DELETE FROM commandHistory WHERE hostID = ? AND id NOT IN " +
                    "(SELECT id FROM commandHistory WHERE hostID = ? ORDER BY usedAt DESC, id DESC LIMIT ?)",
                arguments: [hostID, hostID, max(0, limit)]
            )
        }
    }
}

actor GRDBSessionSnapshotRepository: SessionSnapshotRepository {
    private let database: MuxaDatabase

    init(database: MuxaDatabase) {
        self.database = database
    }

    func fetch(hostID: String, sessionName: String) throws -> SessionSnapshot? {
        try database.queue.read { database in
            guard let row = try Row.fetchOne(
                database,
                sql: "SELECT hostID, sessionName, text, updatedAt FROM sessionSnapshots WHERE key = ?",
                arguments: [Self.key(hostID: hostID, sessionName: sessionName)]
            ) else { return nil }
            return SessionSnapshot(
                hostID: row["hostID"],
                sessionName: row["sessionName"],
                text: row["text"],
                updatedAt: Date(timeIntervalSince1970: row["updatedAt"])
            )
        }
    }

    func save(_ snapshot: SessionSnapshot) throws {
        try database.queue.write { database in
            try database.execute(
                sql: "INSERT OR REPLACE INTO sessionSnapshots " +
                    "(key, hostID, sessionName, text, updatedAt) VALUES (?, ?, ?, ?, ?)",
                arguments: [
                    snapshot.id,
                    snapshot.hostID,
                    snapshot.sessionName,
                    snapshot.text,
                    snapshot.updatedAt.timeIntervalSince1970
                ]
            )
        }
    }

    func deleteAll(hostID: String) throws {
        try database.queue.write { database in
            try database.execute(sql: "DELETE FROM sessionSnapshots WHERE hostID = ?", arguments: [hostID])
        }
    }

    private static func key(hostID: String, sessionName: String) -> String {
        "\(hostID)\t\(sessionName)"
    }
}

actor GRDBCachedSessionRepository: CachedSessionRepository {
    private let database: MuxaDatabase

    init(database: MuxaDatabase) {
        self.database = database
    }

    func fetch(hostID: String) throws -> [TmuxSession] {
        try database.queue.read { database in
            try Row.fetchAll(
                database,
                sql: "SELECT * FROM cachedSessions WHERE hostID = ? ORDER BY name COLLATE NOCASE",
                arguments: [hostID]
            ).map(Self.session)
        }
    }

    func replace(hostID: String, sessions: [TmuxSession], listedAt: Date) throws {
        try database.queue.write { database in
            try database.execute(sql: "DELETE FROM cachedSessions WHERE hostID = ?", arguments: [hostID])
            for session in sessions {
                try database.execute(
                    sql: "INSERT INTO cachedSessions " +
                        "(key, hostID, name, windowCount, attached, hostAlias, socketPath, listedAt) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arguments: [
                        session.id,
                        hostID,
                        session.name,
                        session.windowCount,
                        session.attached,
                        session.hostAlias,
                        session.socketPath,
                        listedAt.timeIntervalSince1970
                    ]
                )
            }
        }
    }

    func deleteAll(hostID: String) throws {
        try database.queue.write { database in
            try database.execute(sql: "DELETE FROM cachedSessions WHERE hostID = ?", arguments: [hostID])
        }
    }

    private static func session(row: Row) -> TmuxSession {
        TmuxSession(
            hostID: row["hostID"],
            name: row["name"],
            windowCount: row["windowCount"],
            attached: row["attached"],
            createdAt: nil,
            lastActivityAt: nil,
            snapshotText: nil,
            snapshotUpdatedAt: nil,
            hostAlias: row["hostAlias"],
            socketPath: row["socketPath"],
            windows: []
        )
    }
}
