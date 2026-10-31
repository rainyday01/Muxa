package app.mtmux.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY alias COLLATE NOCASE")
    fun observeAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun get(id: String): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(host: HostEntity)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM hosts")
    suspend fun all(): List<HostEntity>
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: SnippetEntity)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM snippets")
    suspend fun all(): List<SnippetEntity>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(row: CommandHistoryEntity)

    @Query(
        """
        SELECT * FROM command_history
        WHERE hostId = :hostId AND command LIKE :prefix || '%'
        ORDER BY usedAtEpochMs DESC LIMIT :limit
        """,
    )
    suspend fun match(hostId: String, prefix: String, limit: Int = 20): List<CommandHistoryEntity>

    @Query(
        """
        DELETE FROM command_history WHERE id NOT IN (
          SELECT id FROM command_history ORDER BY usedAtEpochMs DESC LIMIT 500
        )
        """,
    )
    suspend fun trim()
}

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SnapshotEntity)

    @Query("SELECT * FROM session_snapshots WHERE `key` = :key")
    suspend fun get(key: String): SnapshotEntity?

    @Query("SELECT * FROM session_snapshots")
    suspend fun all(): List<SnapshotEntity>
}

@Dao
interface CachedSessionDao {
    @Query("SELECT * FROM cached_sessions ORDER BY hostAlias COLLATE NOCASE, name COLLATE NOCASE")
    suspend fun all(): List<CachedSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CachedSessionEntity>)

    @Query("DELETE FROM cached_sessions WHERE hostId = :hostId")
    suspend fun deleteForHost(hostId: String)

    @Query("DELETE FROM cached_sessions")
    suspend fun clear()

    @Query("DELETE FROM cached_sessions WHERE `key` = :key")
    suspend fun delete(key: String)
}
