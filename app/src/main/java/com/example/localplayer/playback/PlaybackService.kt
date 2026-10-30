package com.example.localplayer.playback

import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.localplayer.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Owns the ExoPlayer and MediaSession. The player's own timeline IS the active queue;
 * saved playlists live in Room and are only *copied* into the queue when played.
 * Queue + position + modes are snapshotted so they survive process death.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var restored = false
    private var consecutiveErrors = 0
    private val dao get() = AppGraph.db.dao()

    private val ticker = object : Runnable {
        override fun run() { saveState(); handler.postDelayed(this, 5000) }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)       // pause when headphones unplug
            .setWakeMode(C.WAKE_MODE_LOCAL)          // player-managed, released when paused
            .build()
        player.addListener(listener)
        session = MediaSession.Builder(this, player).build()
        restore()
    }

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { saveQueue() }
        override fun onPlaybackStateChanged(state: Int) {
            // Repeat OFF == "autoplay": when the queue ends, keep going with random library songs.
            if (state == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF && player.mediaItemCount > 0) autoplayRandom()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { saveState() }
        override fun onRepeatModeChanged(repeatMode: Int) { saveState() }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { saveState() }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(ticker)
            if (isPlaying) { consecutiveErrors = 0; handler.postDelayed(ticker, 5000) }
            saveState()
        }
        override fun onPlayerError(error: PlaybackException) {
            // Missing / revoked / corrupt file: flag it and skip instead of crashing.
            player.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                AppGraph.appScope.launch { dao.setAvailable(id, false) }
            }
            consecutiveErrors++
            if (player.hasNextMediaItem() && consecutiveErrors <= player.mediaItemCount) {
                player.seekToNextMediaItem(); player.prepare(); player.play()
            } else { player.stop() }
        }
    }

    private fun autoplayRandom() {
        main.launch {
            val all = withContext(Dispatchers.IO) { dao.allSongs() }.filter { it.available }
            val inQueue = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }.toSet()
            val lastId = player.currentMediaItem?.mediaId?.toLongOrNull()
            var pool = all.filter { it.id !in inQueue }                 // prefer songs that were not just played
            if (pool.isEmpty()) pool = all.filter { it.id != lastId }
            if (pool.isEmpty()) return@launch                           // nothing else to play
            // The user may have pressed repeat / started something else while the library was being read.
            if (player.playbackState != Player.STATE_ENDED || player.repeatMode != Player.REPEAT_MODE_OFF) return@launch
            val start = player.mediaItemCount
            player.addMediaItems(pool.shuffled().take(10).map { it.toMediaItem() })
            player.seekToDefaultPosition(start)
            player.prepare(); player.play()
            // Keep the queue from growing forever: drop the oldest played entries.
            val extra = player.mediaItemCount - 50
            if (extra > 0 && player.currentMediaItemIndex >= extra) player.removeMediaItems(0, extra)
        }
    }

    private fun restore() {
        main.launch {
            val ids = withContext(Dispatchers.IO) { dao.getQueue().map { it.songId } }
            val byId = withContext(Dispatchers.IO) { dao.getSongs(ids).associateBy { it.id } }
            val items = ids.mapNotNull { byId[it]?.toMediaItem() }
            val prefs = applicationContext.playbackStore.data.first()
            // If the UI already set a queue before restore finished, don't clobber it.
            if (player.mediaItemCount == 0 && items.isNotEmpty()) {
                player.repeatMode = prefs[PrefKeys.REPEAT] ?: Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = prefs[PrefKeys.SHUFFLE] ?: false
                val idx = (prefs[PrefKeys.INDEX] ?: 0).coerceIn(0, items.size - 1)
                player.setMediaItems(items, idx, prefs[PrefKeys.POSITION] ?: 0L)
                player.playWhenReady = false          // never auto-start after reboot / restore
                player.prepare()
            }
            restored = true
        }
    }

    private fun saveQueue() {
        if (!restored) return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        AppGraph.appScope.launch { dao.replaceQueue(ids) }
        saveState()
    }

    private fun saveState() {
        if (!restored) return
        val idx = player.currentMediaItemIndex.coerceAtLeast(0)
        val pos = player.currentPosition.coerceAtLeast(0)
        val rep = player.repeatMode
        val shuf = player.shuffleModeEnabled
        AppGraph.appScope.launch {
            applicationContext.playbackStore.edit {
                it[PrefKeys.INDEX] = idx; it[PrefKeys.POSITION] = pos
                it[PrefKeys.REPEAT] = rep; it[PrefKeys.SHUFFLE] = shuf
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        saveState()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()  // keep playing if playing
    }

    override fun onDestroy() {
        saveState()
        handler.removeCallbacks(ticker)
        session?.run { player.release(); release() }
        session = null
        main.cancel()
        super.onDestroy()
    }
}
