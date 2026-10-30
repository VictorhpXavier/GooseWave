package com.example.localplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/*
 * FOUNDATION FOR THE FUTURE "ONLINE SEARCH + AUTHORIZED DOWNLOAD" FEATURE.
 * Nothing here is wired into a screen yet, so the app shows no search/download UI that does not work.
 *
 * Rules the types enforce:
 *  - Online results are NOT SongEntity and cannot be handed to `player`. Only [TrackAvailability.InLibrary] and
 *    [TrackAvailability.Downloaded] carry a Room song id; that id exists only after the file was saved AND inserted into the library.
 *  - A source must be one that permits downloading (e.g. Creative Commons / public-domain catalogues, artist-provided downloads).
 *    [OnlineTrack.sourceName] and [OnlineTrack.license] are shown on every result so terms stay visible.
 *
 * Integration plan: pass a `online` slot to SearchScreen (see its parameter) that renders a "From the web" section under the
 * local "In your library" results, using [OnlineTrackRow]; drive [TrackAvailability] from a download manager exposed by the ViewModel.
 */
sealed interface TrackAvailability {
    data object Online : TrackAvailability                                   // found remotely, not on this device
    data class Downloading(val progress: Float?) : TrackAvailability         // null = indeterminate
    data class Downloaded(val songId: Long) : TrackAvailability              // saved + added to library (briefly shown, then InLibrary)
    data class InLibrary(val songId: Long) : TrackAvailability               // already local
    data class Failed(val reason: String, val retryable: Boolean) : TrackAvailability
    data class Unavailable(val reason: String) : TrackAvailability           // e.g. licence forbids download, removed upstream
}

data class OnlineTrack(
    val remoteId: String, val title: String, val artist: String, val artworkUrl: String?,
    val sourceName: String, val license: String?, val availability: TrackAvailability
) {
    /** Non-null only when the file really is in the local library. The player may only ever be given this id. */
    val playableSongId: Long? get() = when (val a = availability) { is TrackAvailability.InLibrary -> a.songId; is TrackAvailability.Downloaded -> a.songId; else -> null }
}

@Composable
fun OnlineTrackRow(track: OnlineTrack, onPlayLocal: (Long) -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val a = track.availability
    Row(modifier.fillMaxWidth().heightIn(min = Dimens.row).padding(horizontal = Dimens.screen, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Dimens.thumb).clip(RoundedCornerShape(Dimens.radiusS))) {
            if (track.artworkUrl != null) AsyncImage(ImageRequest.Builder(LocalContext.current).data(track.artworkUrl).crossfade(true).build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else ArtThumb(null, Modifier.fillMaxSize(), seed = track.title)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val status = when (a) {
                is TrackAvailability.Online -> listOfNotNull(track.sourceName, track.license).joinToString(" · ")
                is TrackAvailability.Downloading -> "Downloading…"
                is TrackAvailability.Downloaded -> "Added to your library"
                is TrackAvailability.InLibrary -> "In your library"
                is TrackAvailability.Failed -> a.reason
                is TrackAvailability.Unavailable -> a.reason
            }
            Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = if (a is TrackAvailability.Failed || a is TrackAvailability.Unavailable) cs.error else cs.onSurfaceVariant)
            if (a is TrackAvailability.Downloading) {
                Spacer(Modifier.height(4.dp))
                if (a.progress != null) LinearProgressIndicator({ a.progress }, Modifier.fillMaxWidth(), color = cs.primary)
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary)
            }
        }
        when (a) {
            is TrackAvailability.Online -> IconAction(Icons.Default.Download, "Download ${track.title}", onDownload)
            is TrackAvailability.Downloading -> IconAction(Icons.Default.Close, "Cancel download of ${track.title}", onCancel)
            is TrackAvailability.Downloaded -> IconAction(Icons.Default.PlayArrow, "Play ${track.title}", { onPlayLocal(a.songId) })
            is TrackAvailability.InLibrary -> IconAction(Icons.Default.PlayArrow, "Play ${track.title}", { onPlayLocal(a.songId) })
            is TrackAvailability.Failed -> if (a.retryable) IconAction(Icons.Default.Refresh, "Retry download of ${track.title}", onRetry)
            is TrackAvailability.Unavailable -> Icon(Icons.Default.Block, "Unavailable", Modifier.padding(12.dp), tint = cs.onSurfaceVariant)
        }
    }
}
