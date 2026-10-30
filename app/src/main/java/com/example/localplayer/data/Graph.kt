package com.example.localplayer.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import com.example.localplayer.playback.PlayerController
import kotlinx.coroutines.*

val Context.playbackStore by preferencesDataStore("playback")

object PrefKeys {
    val INDEX = intPreferencesKey("index")
    val POSITION = longPreferencesKey("position")
    val REPEAT = intPreferencesKey("repeat")
    val SHUFFLE = booleanPreferencesKey("shuffle")
    val LIKED = stringSetPreferencesKey("liked")      // favourite song ids
    val RECENT = stringPreferencesKey("recent")       // comma-separated recently played song ids, newest first
}

/** Tiny service locator shared by the UI process parts and the playback service. */
object AppGraph {
    lateinit var app: Application
    lateinit var db: AppDb
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val player by lazy { PlayerController(app) }

    fun init(application: Application) {
        app = application
        db = Room.databaseBuilder(application, AppDb::class.java, "player.db").build()
    }
}

fun SongEntity.toMediaItem(): MediaItem {
    val md = MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setIsBrowsable(false).setIsPlayable(true)
    val art = Art.file(uri)
    if (art.exists()) md.setArtworkUri(Uri.fromFile(art))   // used by notification + lock screen
    return MediaItem.Builder().setMediaId(id.toString()).setUri(uri).setMediaMetadata(md.build()).build()
}

/** Reads tags without copying the file. Returns null if the file is unreadable/invalid. */
fun readSong(ctx: Context, uri: Uri): SongEntity? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?.substringBeforeLast('.') ?: "Unknown"
        val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: name
        SongEntity(
            uri = uri.toString(), title = title,
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: "Unknown album",
            durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        )
    } catch (e: Exception) { null } finally { try { r.release() } catch (_: Exception) {} }
}

/** File name as shown by the file picker (e.g. "10. Name.mp3"); used to import in the order the files are named. */
fun displayName(ctx: Context, uri: Uri): String =
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null } ?: uri.lastPathSegment.orEmpty()

private val chunkRegex = Regex("\\d+|\\D+")

/** Natural, case-insensitive order: "2. B" < "10. A" (plain alphabetical would put 10 before 2). */
val NaturalOrder: Comparator<String> = Comparator { x, y ->
    val a = chunkRegex.findAll(x.lowercase()).map { it.value }.toList()
    val b = chunkRegex.findAll(y.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(a.size, b.size)) {
        val p = a[i]; val q = b[i]
        val c = if (p[0].isDigit() && q[0].isDigit()) {
            val pn = p.trimStart('0'); val qn = q.trimStart('0')
            if (pn.length != qn.length) pn.length.compareTo(qn.length) else pn.compareTo(qn)
        } else p.compareTo(q)
        if (c != 0) return@Comparator c
    }
    a.size.compareTo(b.size)
}
