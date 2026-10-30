package com.example.localplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs", indices = [Index("uri", unique = true)])
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,   // stable id, independent of filename
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val available: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SongEntity::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("playlistId"), Index("songId")]
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int          // explicit, persisted order. Never re-sorted automatically.
)

/** Snapshot of the live playback queue (song ids in queue order). */
@Entity(tableName = "queue_entries")
data class QueueEntryEntity(@PrimaryKey val position: Int, val songId: Long)

data class PlaylistRow(val id: Long, val name: String, val songCount: Int, val coverUri: String?)
data class UriId(val id: Long, val uri: String)
data class PlaylistItem(val entryId: Long, @Embedded val song: SongEntity)

@Dao
abstract class PlayerDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC, id ASC")
    abstract fun observeSongs(): Flow<List<SongEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongs(songs: List<SongEntity>): List<Long>
    @Query("SELECT id, uri FROM songs WHERE uri IN (:uris)")
    abstract suspend fun idsForUris(uris: List<String>): List<UriId>
    @Query("SELECT * FROM songs")
    abstract suspend fun allSongs(): List<SongEntity>
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract suspend fun getSongs(ids: List<Long>): List<SongEntity>
    @Query("UPDATE songs SET available = :a WHERE id = :id")
    abstract suspend fun setAvailable(id: Long, a: Boolean)
    @Query("UPDATE songs SET uri = :uri, available = 1 WHERE id = :id")
    abstract suspend fun relocate(id: Long, uri: String)
    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun deleteSong(id: Long)

    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount, (SELECT s.uri FROM playlist_entries e2 JOIN songs s ON s.id = e2.songId WHERE e2.playlistId = p.id ORDER BY e2.position ASC, e2.entryId ASC LIMIT 1) AS coverUri FROM playlists p ORDER BY p.name COLLATE NOCASE")
    abstract fun observePlaylists(): Flow<List<PlaylistRow>>
    @Query("SELECT e.entryId AS entryId, s.* FROM playlist_entries e JOIN songs s ON s.id = e.songId WHERE e.playlistId = :pid ORDER BY e.position ASC, e.entryId ASC")
    abstract fun observePlaylistItems(pid: Long): Flow<List<PlaylistItem>>
    @Query("SELECT e.entryId AS entryId, s.* FROM playlist_entries e JOIN songs s ON s.id = e.songId WHERE e.playlistId = :pid ORDER BY e.position ASC, e.entryId ASC")
    abstract suspend fun getPlaylistItems(pid: Long): List<PlaylistItem>
    @Query("SELECT name FROM playlists WHERE id = :pid")
    abstract fun observePlaylistName(pid: Long): Flow<String?>

    @Insert abstract suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :pid")
    abstract suspend fun maxPosition(pid: Long): Int
    @Query("SELECT songId FROM playlist_entries WHERE playlistId = :pid")
    abstract suspend fun songIdsIn(pid: Long): List<Long>
    @Insert abstract suspend fun insertEntries(list: List<PlaylistEntryEntity>)
    @Query("UPDATE playlist_entries SET position = :pos WHERE entryId = :entryId")
    abstract suspend fun setPosition(entryId: Long, pos: Int)
    @Query("DELETE FROM playlist_entries WHERE entryId = :entryId")
    abstract suspend fun removeEntry(entryId: Long)

    /** Appends songs at the end, skipping songs already in that playlist. */
    @Transaction
    open suspend fun addSongs(pid: Long, songIds: List<Long>): Int {
        val existing = songIdsIn(pid).toSet()
        val fresh = songIds.filter { it !in existing }.distinct()
        var p = maxPosition(pid) + 1
        insertEntries(fresh.map { PlaylistEntryEntity(playlistId = pid, songId = it, position = p++) })
        return fresh.size
    }

    @Transaction
    open suspend fun reorder(orderedEntryIds: List<Long>) {
        orderedEntryIds.forEachIndexed { i, id -> setPosition(id, i) }
    }

    @Query("SELECT * FROM queue_entries ORDER BY position ASC")
    abstract suspend fun getQueue(): List<QueueEntryEntity>
    @Query("DELETE FROM queue_entries")
    abstract suspend fun clearQueue()
    @Insert abstract suspend fun insertQueue(list: List<QueueEntryEntity>)

    @Transaction
    open suspend fun replaceQueue(ids: List<Long>) {
        clearQueue()
        insertQueue(ids.mapIndexed { i, id -> QueueEntryEntity(i, id) })
    }
}

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class, QueueEntryEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDb : RoomDatabase() { abstract fun dao(): PlayerDao }
