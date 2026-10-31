import Foundation
import GRDB

actor GRDBKnownHostsStore: KnownHostStoring {
    private let database: MuxaDatabase

    init(database: MuxaDatabase) {
        self.database = database
    }

    func identities(hostname: String, port: UInt16) throws -> [HostKeyIdentity] {
        try database.queue.read { database in
            try Row.fetchAll(
                database,
                sql: "SELECT hostname, port, keyType, fingerprint FROM knownHosts " +
                    "WHERE hostname = ? AND port = ?",
                arguments: [hostname, port]
            ).map { row in
                HostKeyIdentity(
                    hostname: row["hostname"],
                    port: row["port"],
                    keyType: row["keyType"],
                    sha256Fingerprint: row["fingerprint"]
                )
            }
        }
    }

    func verify(_ identity: HostKeyIdentity, seenAt: Date) throws -> HostKeyVerification {
        try database.queue.write { database in
            guard let stored = try Self.fetch(identity, database: database) else {
                return .unknown(identity)
            }
            guard stored.sha256Fingerprint == identity.sha256Fingerprint else {
                return .mismatch(expected: stored, actual: identity)
            }
            try database.execute(
                sql: "UPDATE knownHosts SET lastSeenAt = ? WHERE hostname = ? AND port = ? AND keyType = ?",
                arguments: [seenAt.timeIntervalSince1970, identity.hostname, identity.port, identity.keyType]
            )
            return .trusted(identity)
        }
    }

    func acceptFirst(_ identity: HostKeyIdentity, seenAt: Date) throws {
        try database.queue.write { database in
            if let stored = try Self.fetch(identity, database: database) {
                guard stored.sha256Fingerprint == identity.sha256Fingerprint else {
                    throw KnownHostsError.keyChanged(expected: stored, actual: identity)
                }
            }
            try Self.upsert(identity, seenAt: seenAt, database: database)
        }
    }

    func replaceAfterConfirmation(_ identity: HostKeyIdentity, seenAt: Date) throws {
        try database.queue.write { database in
            try Self.upsert(identity, seenAt: seenAt, database: database)
        }
    }

    func remove(hostname: String, port: UInt16) throws {
        try database.queue.write { database in
            try database.execute(
                sql: "DELETE FROM knownHosts WHERE hostname = ? AND port = ?",
                arguments: [hostname, port]
            )
        }
    }

    private static func fetch(_ identity: HostKeyIdentity, database: Database) throws -> HostKeyIdentity? {
        guard let row = try Row.fetchOne(
            database,
            sql: "SELECT hostname, port, keyType, fingerprint FROM knownHosts " +
                "WHERE hostname = ? AND port = ? AND keyType = ?",
            arguments: [identity.hostname, identity.port, identity.keyType]
        ) else { return nil }
        return HostKeyIdentity(
            hostname: row["hostname"],
            port: row["port"],
            keyType: row["keyType"],
            sha256Fingerprint: row["fingerprint"]
        )
    }

    private static func upsert(_ identity: HostKeyIdentity, seenAt: Date, database: Database) throws {
        try database.execute(
            sql: "INSERT INTO knownHosts (hostname, port, keyType, fingerprint, firstSeenAt, lastSeenAt) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(hostname, port, keyType) DO UPDATE SET " +
                "fingerprint = excluded.fingerprint, lastSeenAt = excluded.lastSeenAt",
            arguments: [
                identity.hostname,
                identity.port,
                identity.keyType,
                identity.sha256Fingerprint,
                seenAt.timeIntervalSince1970,
                seenAt.timeIntervalSince1970
            ]
        )
    }
}
