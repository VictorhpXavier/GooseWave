package com.example.localplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Late night"
}

@Composable
fun HomeScreen(env: Env) {
    val vm = env.vm
    val songs by vm.songs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val recentIds by vm.recentIds.collectAsState()
    val importing by vm.importing.collectAsState()
    val cs = MaterialTheme.colorScheme
    val avail = remember(songs) { songs.filter { it.available } }
    val byId = remember(songs) { songs.associateBy { it.id } }
    val recent = remember(recentIds, byId) { recentIds.mapNotNull { byId[it] }.filter { it.available } }
    val added = remember(songs) { songs.sortedByDescending { it.addedAt }.take(5) }

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            ScreenTitle(greeting(), if (songs.isEmpty()) "Your music, on your device" else "${songs.size} songs in your library") {
                IconAction(Icons.Default.Add, "Import music", env.import)
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen), color = cs.primary)
        }
        if (songs.isEmpty()) {
            item {
                Empty(Icons.Default.LibraryMusic, "Bring your music", "Import MP3 files from your phone. Titles, artists, albums and cover art are read automatically.") {
                    Button(env.import, shape = CircleShape) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Import MP3s") }
                }
            }
            return@LazyColumn
        }

        // Continue listening
        item {
            val cur = env.ps.current
            val fallback = recent.firstOrNull()
            val title = cur?.title ?: fallback?.title
            if (title != null) {
                val artist = cur?.artist ?: fallback?.artist.orEmpty()
                val uri = cur?.uri ?: fallback?.uri
                val playingNow = env.ps.isPlaying
                val start = { if (fallback != null) player.playSongs(avail, avail.indexOf(fallback)) }
                Row(
                    Modifier.padding(horizontal = Dimens.screen).fillMaxWidth().clip(RoundedCornerShape(Dimens.radiusL)).background(cs.surfaceContainerHigh)
                        .clickable(onClickLabel = "Continue listening") { if (cur != null) env.nav.player() else start() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtThumb(uri, Modifier.size(76.dp), RoundedCornerShape(Dimens.radiusM), title)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("CONTINUE LISTENING", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PlayFab(if (playingNow && cur != null) "Pause" else "Play", { if (cur != null) player.togglePlay() else start() },
                        size = 52.dp, icon = if (playingNow && cur != null) Icons.Default.Pause else Icons.Default.PlayArrow)
                }
            }
        }

        // Shortcuts into the library
        item {
            Row(Modifier.padding(top = 20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.screen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Liked songs", false, { env.nav.library(4) }, Icons.Default.Favorite)
                Pill("Albums", false, { env.nav.library(1) }, Icons.Default.Album)
                Pill("Artists", false, { env.nav.library(2) }, Icons.Default.Person)
                Pill("Playlists", false, { env.nav.library(3) }, Icons.Default.PlaylistPlay)
            }
        }

        // Recently played
        if (recent.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Dimens.screen), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent.take(10), key = { it.id }) { s ->
                        CoverCard(s.uri, s.title, s.artist, { player.playSongs(recent, recent.indexOf(s)) }, Modifier.width(136.dp), seed = s.album)
                    }
                }
            }
        }

        // Playlists (compact tiles)
        if (playlists.isNotEmpty()) {
            item { SectionTitle("Your playlists") }
            items(playlists.take(6).chunked(2)) { pair ->
                Row(Modifier.padding(horizontal = Dimens.screen).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p ->
                        Row(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(Dimens.radiusS)).background(cs.surfaceContainer)
                                .clickable(onClickLabel = "Open ${p.name}") { env.nav.playlist(p.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArtThumb(p.coverUri, Modifier.size(56.dp), RoundedCornerShape(0.dp), p.name, 24.dp)
                            Text(p.name, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // Recently added
        item { SectionTitle("Recently added") }
        items(added, key = { "added${it.id}" }) { s ->
            SongRow(s, env, onClick = { if (s.available) player.playSongs(avail, avail.indexOf(s)) else env.locate(s.id) })
        }
    }
}
