import Foundation
import GRDB

final class MuxaDatabase: @unchecked Sendable {
    let queue: DatabaseQueue

    init(path: String) throws {
        queue = try DatabaseQueue(path: path, configuration: Self.configuration)
        try Self.migrator.migrate(queue)
    }

    init(inMemory: Bool) throws {
        precondition(inMemory)
        queue = try DatabaseQueue(configuration: Self.configuration)
        try Self.migrator.migrate(queue)
    }

    /// Removes local records that are not owned by a saved host. Host-owned
    /// rows are removed by GRDBHostRepository so its observers stay in sync.
    func deleteAllNonHostData() throws {
        try queue.write { database in
            try database.execute(sql: "DELETE FROM knownHosts")
            try database.execute(sql: "DELETE FROM snippets")
            try database.execute(sql: "DELETE FROM keyboardLayouts WHERE builtin = 0")
        }
    }

    private static var configuration: Configuration {
        var configuration = Configuration()
        configuration.prepareDatabase { database in
            try database.execute(sql: "PRAGMA foreign_keys = ON")
        }
        return configuration
    }

    private static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        migrator.registerMigration("v1-initial") { database in
            try createHosts(in: database)
            try createSnippets(in: database)
            try createHistory(in: database)
            try createSnapshots(in: database)
            try createCachedSessions(in: database)
            try createKeyboardLayouts(in: database)
        }
        migrator.registerMigration("v2-known-hosts") { database in
            try createKnownHosts(in: database)
        }
        migrator.registerMigration("v3-environment-probe") { database in
            try database.alter(table: "hosts") { table in
                table.add(column: "operatingSystem", .text)
                table.add(column: "packageManager", .text)
            }
        }
        migrator.registerMigration("v4-host-resources") { database in
            // Adds last-seen mem/disk columns without wiping hosts.
            try database.alter(table: "hosts") { table in
                table.add(column: "lastMemAvailableKB", .integer)
                table.add(column: "lastMemTotalKB", .integer)
                table.add(column: "lastDiskAvailableKB", .integer)
                table.add(column: "lastDiskTotalKB", .integer)
                table.add(column: "lastStatsAt", .double)
            }
        }
        return migrator
    }

    private static func createHosts(in database: Database) throws {
        try database.create(table: "hosts") { table in
            table.column("id", .text).primaryKey()
            table.column("alias", .text).notNull()
            table.column("hostname", .text).notNull()
            table.column("port", .integer).notNull()
            table.column("username", .text).notNull()
            table.column("transport", .text).notNull()
            table.column("hasPrivateKey", .boolean).notNull().defaults(to: false)
            table.column("hasPassword", .boolean).notNull().defaults(to: false)
            table.column("tmuxInstalled", .boolean)
            table.column("lastLatencyMilliseconds", .integer)
            table.column("lastCheckedAt", .double)
        }
    }

    private static func createSnippets(in database: Database) throws {
        try database.create(table: "snippets") { table in
            table.column("id", .text).primaryKey()
            table.column("name", .text).notNull()
            table.column("body", .text).notNull()
            table.column("tagsJSON", .text).notNull().defaults(to: "[]")
            table.column("runOnSelect", .boolean).notNull().defaults(to: false)
        }
    }

    private static func createHistory(in database: Database) throws {
        try database.create(table: "commandHistory") { table in
            table.autoIncrementedPrimaryKey("id")
            table.column("hostID", .text).notNull().indexed().references("hosts", onDelete: .cascade)
            table.column("command", .text).notNull()
            table.column("usedAt", .double).notNull().indexed()
        }
    }

    private static func createSnapshots(in database: Database) throws {
        try database.create(table: "sessionSnapshots") { table in
            table.column("key", .text).primaryKey()
            table.column("hostID", .text).notNull().indexed().references("hosts", onDelete: .cascade)
            table.column("sessionName", .text).notNull()
            table.column("text", .text).notNull()
            table.column("updatedAt", .double).notNull()
        }
    }

    private static func createCachedSessions(in database: Database) throws {
        try database.create(table: "cachedSessions") { table in
            table.column("key", .text).primaryKey()
            table.column("hostID", .text).notNull().indexed().references("hosts", onDelete: .cascade)
            table.column("name", .text).notNull()
            table.column("windowCount", .integer).notNull()
            table.column("attached", .boolean).notNull()
            table.column("hostAlias", .text)
            table.column("socketPath", .text)
            table.column("listedAt", .double).notNull()
        }
    }

    private static func createKeyboardLayouts(in database: Database) throws {
        try database.create(table: "keyboardLayouts") { table in
            table.column("id", .text).primaryKey()
            table.column("document", .blob).notNull()
            table.column("builtin", .boolean).notNull().defaults(to: false)
            table.column("updatedAt", .double).notNull()
        }
    }

    private static func createKnownHosts(in database: Database) throws {
        try database.create(table: "knownHosts") { table in
            table.column("hostname", .text).notNull()
            table.column("port", .integer).notNull()
            table.column("keyType", .text).notNull()
            table.column("fingerprint", .text).notNull()
            table.column("firstSeenAt", .double).notNull()
            table.column("lastSeenAt", .double).notNull()
            table.primaryKey(["hostname", "port", "keyType"])
        }
    }
}
