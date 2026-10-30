package com.example.localplayer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Albums/artists are derived from the tags of imported songs; nothing extra is stored. */
data class AlbumGroup(val name: String, val artist: String, val songs: List<SongEntity>)
data class ArtistGroup(val name: String, val songs: List<SongEntity>, val albumCount: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppGraph.db.dao()
    private val store = app.playbackStore

    val songs = dao.observeSongs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = dao.observePlaylists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val importing = MutableStateFlow(false)

    val albums: StateFlow<List<AlbumGroup>> = songs.map { list ->
        list.groupBy { it.album }.map { (name, ss) ->
            val artists = ss.map { it.artist }.distinct()
            // Import order is the best available proxy for track order (no track-number tag is stored).
            AlbumGroup(name, if (artists.size == 1) artists[0] else "Various artists", ss.sortedBy { it.id })
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<ArtistGroup>> = songs.map { list ->
        list.groupBy { it.artist }.map { (name, ss) -> ArtistGroup(name, ss, ss.map { it.album }.distinct().size) }
            .sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val liked: StateFlow<Set<Long>> = store.data
        .map { p -> p[PrefKeys.LIKED]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val recentIds: StateFlow<List<Long>> = store.data
        .map { p -> p[PrefKeys.RECENT]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleLike(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        store.edit { p ->
            val cur = p[PrefKeys.LIKED] ?: emptySet()
            val s = id.toString()
            p[PrefKeys.LIKED] = if (s in cur) cur - s else cur + s
        }
    }

    fun setLiked(ids: List<Long>, like: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        store.edit { p ->
            val cur = p[PrefKeys.LIKED] ?: emptySet()
            val strs = ids.map { it.toString() }
            p[PrefKeys.LIKED] = if (like) cur + strs else cur - strs.toSet()
        }
    }
    fun removeEntries(entryIds: List<Long>) = viewModelScope.launch(Dispatchers.IO) { entryIds.forEach { dao.removeEntry(it) } }
    fun removeSongs(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) { ids.forEach { dao.deleteSong(it) } }

    fun playlistItems(pid: Long): Flow<List<PlaylistItem>> = dao.observePlaylistItems(pid)
    fun playlistName(pid: Long): Flow<String?> = dao.observePlaylistName(pid)
    private fun toast(s: String) = Toast.makeText(getApplication(), s, Toast.LENGTH_SHORT).show()

    private suspend fun readAll(uris: List<Uri>): Pair<List<SongEntity>, Int> = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val ok = ArrayList<SongEntity>(); var bad = 0
        // The picker returns files in no guaranteed order, so sort by file name ("1. …", "2. …", "10. …") before inserting.
        val ordered = uris.sortedWith(compareBy(NaturalOrder) { displayName(ctx, it) })
        for (u in ordered) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val s = readSong(ctx, u); if (s != null) { ok += s; Art.ensure(ctx, s.uri) } else bad++
        }
        ok to bad
    }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        importing.value = true
        val (songs, bad) = readAll(uris)
        Art.version.update { it + 1 }
        val added = withContext(Dispatchers.IO) { dao.insertSongs(songs).count { it != -1L } }
        importing.value = false
        toast("Imported $added new song(s)" + (if (bad > 0) ", $bad unreadable skipped" else "") +
            (if (songs.size - added > 0) ", ${songs.size - added} already in library" else ""))
    }

    /** Import files and append them straight to a playlist, ordered by file name (natural numeric order). */
    fun importInto(pid: Long, uris: List<Uri>) = viewModelScope.launch {
        importing.value = true
        val (songs, bad) = readAll(uris)
        Art.version.update { it + 1 }
        val added = withContext(Dispatchers.IO) {
            dao.insertSongs(songs)
            val byUri = HashMap<String, Long>()
            songs.map { it.uri }.chunked(500).forEach { chunk -> dao.idsForUris(chunk).forEach { byUri[it.uri] = it.id } }
            dao.addSongs(pid, songs.mapNotNull { byUri[it.uri] })
        }
        importing.value = false
        toast("Added $added song(s) to the playlist" + (if (bad > 0) ", $bad unreadable skipped" else ""))
    }

    fun relocate(songId: Long, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        try { dao.relocate(songId, uri.toString()); Art.ensure(getApplication(), uri.toString()); Art.version.update { it + 1 } } catch (e: Exception) {
            withContext(Dispatchers.Main) { toast("That file is already in the library") }
        }
    }
    fun removeSong(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteSong(id) }

    fun createPlaylist(name: String) = viewModelScope.launch(Dispatchers.IO) { dao.insertPlaylist(PlaylistEntity(name = name.trim())) }
    fun rename(id: Long, name: String) = viewModelScope.launch(Dispatchers.IO) { dao.renamePlaylist(id, name.trim()) }
    fun deletePlaylist(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deletePlaylist(id) }
    fun addToPlaylists(pids: Collection<Long>, songIds: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        for (p in pids) dao.addSongs(p, songIds)
    }
    fun reorder(entryIds: List<Long>) = viewModelScope.launch(Dispatchers.IO) { dao.reorder(entryIds) }
    fun removeEntry(entryId: Long) = viewModelScope.launch(Dispatchers.IO) { dao.removeEntry(entryId) }
    suspend fun itemsOf(pid: Long) = withContext(Dispatchers.IO) { dao.getPlaylistItems(pid).map { it.song } }
}
