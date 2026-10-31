package app.mtmux.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HostEntity::class,
        SnippetEntity::class,
        CommandHistoryEntity::class,
        SnapshotEntity::class,
        CachedSessionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MtmuxDatabase : RoomDatabase() {
    abstract fun hosts(): HostDao
    abstract fun snippets(): SnippetDao
    abstract fun history(): HistoryDao
    abstract fun snapshots(): SnapshotDao
    abstract fun cachedSessions(): CachedSessionDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hosts ADD COLUMN lastMemAvailKb INTEGER")
        db.execSQL("ALTER TABLE hosts ADD COLUMN lastMemTotalKb INTEGER")
        db.execSQL("ALTER TABLE hosts ADD COLUMN lastDiskAvailKb INTEGER")
        db.execSQL("ALTER TABLE hosts ADD COLUMN lastDiskTotalKb INTEGER")
        db.execSQL("ALTER TABLE hosts ADD COLUMN lastStatsAtEpochMs INTEGER")
    }
}
