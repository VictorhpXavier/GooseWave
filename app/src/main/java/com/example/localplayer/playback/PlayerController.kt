package com.example.localplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.localplayer.data.AppGraph
import com.example.localplayer.data.PlaylistItem
import com.example.localplayer.data.PrefKeys
import com.example.localplayer.data.SongEntity
import com.example.localplayer.data.playbackStore
import com.example.localplayer.data.toMediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class QueueItem(val mediaId: String, val title: String, val artist: String, val uri: String)

/** Structural playback state. Changes rarely, so screens that read it don't recompose every tick. */
data class PlayerUi(
    val queue: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f
) { val current: QueueItem? get() = queue.getOrNull(index) }

fun repeatLabel(mode: Int) = when (mode) {
    Player.REPEAT_MODE_ALL -> "Repeating the playlist"
    Player.REPEAT_MODE_ONE -> "Repeating this song"
    else -> "Autoplay: random songs when the playlist ends"
}

/** High-frequency position, kept in its own flow. */
data class Progress(val positionMs: Long = 0, val durationMs: Long = 0)

/** UI-side handle to the service's player via a MediaController. */
class PlayerController(private val ctx: Context) {
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress
    private var c: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastRecorded: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({
            c = f.get(); c?.addListener(listener); refresh()
        }, ContextCompat.getMainExecutor(ctx))
        scope.launch {
            while (true) {
                delay(250)
                val p = c ?: continue
                if (p.isPlaying) _progress.value = Progress(p.currentPosition, p.duration.coerceAtLeast(0))
            }
        }
    }

    private fun refresh() {
        val p = c ?: return
        val q = (0 until p.mediaItemCount).map {
            val m = p.getMediaItemAt(it)
            QueueItem(m.mediaId, m.mediaMetadata.title?.toString() ?: "?", m.mediaMetadata.artist?.toString() ?: "",
                m.localConfiguration?.uri?.toString() ?: "")
        }
        _state.value = PlayerUi(q, if (q.isEmpty()) -1 else p.currentMediaItemIndex, p.isPlaying,
            p.shuffleModeEnabled, p.repeatMode, p.playbackParameters.speed)
        _progress.value = Progress(p.currentPosition, p.duration.coerceAtLeast(0))
        if (p.isPlaying) recordRecent(p.currentMediaItem?.mediaId)
    }

    /** Keeps the last 20 played song ids for the Home screen. */
    private fun recordRecent(id: String?) {
        if (id == null || id == lastRecorded) return
        lastRecorded = id
        AppGraph.appScope.launch {
            ctx.playbackStore.edit { prefs ->
                val old = prefs[PrefKeys.RECENT]?.split(",")?.filter { x -> x.isNotEmpty() } ?: emptyList()
                prefs[PrefKeys.RECENT] = (listOf(id) + old.filter { x -> x != id }).take(20).joinToString(",")
            }
        }
    }

    // ---- queue building ----
    fun playSongs(songs: List<SongEntity>, start: Int = 0) {
        val p = c ?: return
        if (songs.isEmpty()) return
        p.setMediaItems(songs.map { it.toMediaItem() }, start.coerceIn(0, songs.size - 1), 0)
        p.prepare(); p.play()
    }
    fun playShuffled(songs: List<SongEntity>) {
        val p = c ?: return
        if (songs.isEmpty()) return
        p.shuffleModeEnabled = true
        playSongs(songs, songs.indices.random())
    }
    fun playItems(items: List<PlaylistItem>, start: Int = 0) = playSongs(items.map { it.song }, start)

    fun playNext(songs: List<SongEntity>) {
        val p = c ?: return
        if (p.mediaItemCount == 0) { playSongs(songs); return }
        p.addMediaItems(p.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }
    fun addToQueue(songs: List<SongEntity>) {
        val p = c ?: return
        val wasEmpty = p.mediaItemCount == 0
        p.addMediaItems(songs.map { it.toMediaItem() })
        if (wasEmpty) p.prepare()
    }

    // ---- queue editing ----
    fun moveItem(from: Int, to: Int) { c?.moveMediaItem(from, to) }
    fun removeItem(i: Int) { c?.removeMediaItem(i) }
    fun clearQueue() { c?.clearMediaItems() }
    fun skipTo(i: Int) { c?.apply { seekToDefaultPosition(i); prepare(); play() } }

    // ---- transport ----
    fun togglePlay() { c?.let { if (it.isPlaying) it.pause() else { it.prepare(); it.play() } } }
    fun next() {
        val p = c ?: return
        // At the last song with repeat off, "next" behaves like the song ending, so autoplay continues with random songs.
        if (!p.hasNextMediaItem() && p.repeatMode == Player.REPEAT_MODE_OFF && p.mediaItemCount > 0 && p.duration > 0) p.seekTo(p.duration)
        else p.seekToNextMediaItem()
    }
    fun previous() { c?.seekToPrevious() }
    fun seekTo(ms: Long) { c?.seekTo(ms); _progress.update { it.copy(positionMs = ms) } }
    fun toggleShuffle() { c?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun setSpeed(speed: Float) { c?.setPlaybackSpeed(speed) }
    fun cycleRepeat() {
        c?.let {
            // 1 tap: loop the playlist, 2 taps: loop the song, 3 taps: back to autoplay (random songs when it ends).
            val m = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = m
            android.widget.Toast.makeText(ctx, repeatLabel(m), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
