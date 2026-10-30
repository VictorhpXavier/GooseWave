package com.example.localplayer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.data.NaturalOrder
import com.example.localplayer.data.PlaylistItem
import com.example.localplayer.playback.QueueItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ============================ ALBUM ============================
@Composable
fun AlbumScreen(env: Env, name: String) {
    val albums by env.vm.albums.collectAsState()
    val album = albums.firstOrNull { it.name == name }
    val cs = MaterialTheme.colorScheme
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val exit = { selecting = false; selected = emptySet() }
    BackHandler(selecting) { exit() }
    Column(Modifier.fillMaxSize()) {
        val chosen = album?.songs?.filter { it.id in selected }.orEmpty()
        val chosenIds = chosen.map { it.id }
        if (selecting && album != null) {
            SelectionBar(selected.size, { exit() }, { selected = album.songs.map { it.id }.toSet() }, listOf(
                SelAction(Icons.Default.PlaylistAdd, "Add to playlist") { env.addToPlaylist(chosenIds); exit() },
                SelAction(Icons.Default.Queue, "Add to queue") { player.addToQueue(chosen.filter { it.available }); exit() },
                SelAction(Icons.Default.PlaylistPlay, "Play next") { player.playNext(chosen.filter { it.available }); exit() },
                SelAction(Icons.Default.Favorite, "Add to Liked songs") { env.vm.setLiked(chosenIds, true); exit() }
            ))
        } else DetailTopBar(env.nav.back) {
            if (album != null) IconAction(Icons.Default.Checklist, "Select songs", { selecting = true })
        }
        if (album == null) { Empty(Icons.Default.Album, "Album not found", "It may have been removed from your library."); return@Column }
        val avail = remember(album) { album.songs.filter { it.available } }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtThumb(
                        album.songs.first().uri,
                        Modifier.fillMaxWidth(0.62f).aspectRatio(1f).shadow(16.dp, RoundedCornerShape(Dimens.radiusL)),
                        RoundedCornerShape(Dimens.radiusL), name, 64.dp
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (album.artist != "Various artists") {
                        Text(
                            album.artist, Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Open artist ${album.artist}") { env.nav.artist(album.artist) }.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleMedium, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    } else Text(album.artist, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    Text("Album · ${album.songs.size} songs · ${fmtTotal(album.songs.sumOf { it.durationMs })}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconAction(Icons.Default.Shuffle, "Shuffle album", { player.playShuffled(avail) })
                        PlayFab("Play album", { player.playSongs(avail) }, size = 64.dp)
                        IconAction(Icons.Default.Queue, "Add album to queue", { player.addToQueue(avail) })
                        IconAction(Icons.Default.PlaylistAdd, "Add album to playlist", { env.addToPlaylist(album.songs.map { it.id }) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            itemsIndexed(album.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s, env, leading = "${i + 1}", showArt = false, selecting = selecting, selected = s.id in selected,
                    onLongClick = { selecting = true; selected = selected + s.id },
                    onClick = {
                        if (selecting) { selected = if (s.id in selected) selected - s.id else selected + s.id }
                        else if (s.available) { player.playSongs(avail, avail.indexOf(s)) }
                        else { env.locate(s.id) }
                    }
                )
            }
        }
    }
}

// ============================ ARTIST ============================
@Composable
fun ArtistScreen(env: Env, name: String) {
    val artists by env.vm.artists.collectAsState()
    val albums by env.vm.albums.collectAsState()
    val artist = artists.firstOrNull { it.name == name }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(env.nav.back)
        if (artist == null) { Empty(Icons.Default.Person, "Artist not found", "They may have been removed from your library."); return@Column }
        val songs = remember(artist) { artist.songs.sortedBy { it.title.lowercase() } }
        val avail = remember(songs) { songs.filter { it.available } }
        val theirAlbums = remember(albums, name) { albums.filter { a -> a.songs.any { it.artist == name } } }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtThumb(songs.first().uri, Modifier.size(150.dp).shadow(12.dp, CircleShape), CircleShape, name, 56.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} songs · ${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconAction(Icons.Default.Shuffle, "Shuffle $name", { player.playShuffled(avail) })
                        PlayFab("Play $name", { player.playSongs(avail) }, size = 64.dp)
                        IconAction(Icons.Default.Queue, "Add all to queue", { player.addToQueue(avail) })
                    }
                }
            }
            if (theirAlbums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = Dimens.screen), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(theirAlbums, key = { it.name }) { a -> CoverCard(a.songs.first().uri, a.name, "${a.songs.size} songs", { env.nav.album(a.name) }, Modifier.width(136.dp)) }
                    }
                }
            }
            item { SectionTitle("Songs") }
            items(songs, key = { it.id }) { s -> SongRow(s, env, onClick = { if (s.available) player.playSongs(avail, avail.indexOf(s)) else env.locate(s.id) }) }
        }
    }
}

// ============================ PLAYLIST ============================
@Composable
fun PlaylistScreen(env: Env, pid: Long) {
    val vm = env.vm
    val items by vm.playlistItems(pid).collectAsState(emptyList())
    val name by vm.playlistName(pid).collectAsState(null)
    val importing by vm.importing.collectAsState()
    val local = remember { mutableStateListOf<PlaylistItem>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) { local.clear(); local.addAll(items) } }
    val lazy = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // The header is now part of the list (it scrolls away and gives the songs the whole screen), so move by KEY, not by lazy index.
    val rs = rememberReorderableLazyListState(lazy) { from, to ->
        val a = local.indexOfFirst { it.entryId == from.key }
        val b = local.indexOfFirst { it.entryId == to.key }
        if (a >= 0 && b >= 0) local.add(b, local.removeAt(a))
    }
    var adding by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }          // playlist entry ids
    var picker by remember { mutableStateOf<Int?>(null) }               // 0 = copy to, 1 = move to
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.importInto(pid, it) }
    val cs = MaterialTheme.colorScheme
    val playable = local.filter { it.song.available }.map { it.song }
    val exit = { selecting = false; selected = emptySet() }
    BackHandler(selecting) { exit() }
    val chosen = local.filter { it.entryId in selected }
    val currentIdx = local.indexOfFirst { it.song.id.toString() == env.ps.current?.mediaId }
    val leadItems = 2   // "header" + "sub" items precede the songs

    fun moveSelected(toTop: Boolean) {
        val rest = local.filter { it.entryId !in selected }
        val list = if (toTop) chosen + rest else rest + chosen
        local.clear(); local.addAll(list)
        vm.reorder(list.map { it.entryId })
    }
    fun moveBy(i: Int, d: Int) {            // non-drag reordering (screen readers, keyboards)
        val j = i + d
        if (j in local.indices) { local.add(j, local.removeAt(i)); vm.reorder(local.map { it.entryId }) }
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(selected.size, { exit() }, { selected = local.map { it.entryId }.toSet() }, listOf(
                SelAction(Icons.Default.DriveFileMove, "Move to playlist…") { picker = 1 },
                SelAction(Icons.Default.Queue, "Add to queue") { player.addToQueue(chosen.map { it.song }.filter { it.available }); exit() },
                SelAction(Icons.Default.PlaylistPlay, "Play next") { player.playNext(chosen.map { it.song }.filter { it.available }); exit() },
                SelAction(Icons.Default.VerticalAlignTop, "Move to top") { moveSelected(true); exit() },
                SelAction(Icons.Default.VerticalAlignBottom, "Move to end") { moveSelected(false); exit() },
                SelAction(Icons.Default.PlaylistAdd, "Copy to playlist…") { picker = 0 },
                SelAction(Icons.Default.RemoveCircleOutline, "Remove from this playlist") { vm.removeEntries(chosen.map { it.entryId }); exit() }
            ))
        } else DetailTopBar(env.nav.back, name) {
            // Jump back to whatever is playing, however far down the list it is.
            if (currentIdx >= 0) IconAction(Icons.Default.MyLocation, "Scroll to the playing song", { scope.launch { lazy.animateScrollToItem(leadItems + currentIdx) } })
            if (local.isNotEmpty()) IconAction(Icons.Default.Checklist, "Select songs", { selecting = true })
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary)
        LazyColumn(state = lazy, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                Column {
                    Row(Modifier.padding(horizontal = Dimens.screen), verticalAlignment = Alignment.CenterVertically) {
                        ArtThumb(local.firstOrNull()?.song?.uri, Modifier.size(104.dp).shadow(10.dp, RoundedCornerShape(Dimens.radiusM)), RoundedCornerShape(Dimens.radiusM), name ?: "", 44.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(name ?: "", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Playlist · ${local.size} songs" + if (local.isNotEmpty()) " · ${fmtTotal(local.sumOf { it.song.durationMs })}" else "",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                    Row(Modifier.padding(horizontal = Dimens.screen - 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayFab("Play playlist", { player.playItems(local.toList()) }, Modifier.padding(start = 4.dp), size = 56.dp)
                        IconAction(Icons.Default.Shuffle, "Shuffle playlist", { player.playShuffled(playable) })
                        IconAction(Icons.Default.Queue, "Add playlist to queue", { player.addToQueue(playable) })
                        Spacer(Modifier.weight(1f))
                        Box {
                            Row(
                                Modifier.pressClick("Add songs") { addMenu = true }.height(40.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add", style = MaterialTheme.typography.labelLarge) }
                            DropdownMenu(addMenu, { addMenu = false }) {
                                DropdownMenuItem({ Text("Import MP3s from files") }, { addMenu = false; importPicker.launch(arrayOf("audio/mpeg", "audio/*")) }, leadingIcon = { Icon(Icons.Default.FileOpen, null) })
                                DropdownMenuItem({ Text("Sort by title (1, 2, 10…)") }, {
                                    addMenu = false
                                    val sorted = local.sortedWith(compareBy(NaturalOrder) { it.song.title })
                                    local.clear(); local.addAll(sorted); vm.reorder(sorted.map { it.entryId })
                                }, enabled = local.size > 1, leadingIcon = { Icon(Icons.Default.SortByAlpha, null) })
                                DropdownMenuItem({ Text("Choose from library") }, { addMenu = false; adding = true }, leadingIcon = { Icon(Icons.Default.LibraryMusic, null) })
                            }
                        }
                    }
                }
            }
            item(key = "sub") {
                when {
                    local.isEmpty() -> Empty(Icons.Default.MusicNote, "This playlist is empty", "Tap Add to import MP3s straight into it, or pick from your library.")
                    selecting -> Text("Tap songs to select.", Modifier.padding(horizontal = Dimens.screen, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    local.size > 1 -> Text("Press and hold ≡ at the right of a song, then drag to reorder.", Modifier.padding(horizontal = Dimens.screen, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
            itemsIndexed(local, key = { _, row -> row.entryId }) { i, row ->
                ReorderableItem(rs, key = row.entryId) { isDragging ->
                    val lift by animateDpAsState(if (isDragging) 10.dp else 0.dp, label = "lift")
                    SongRow(
                        row.song, env, Modifier.shadow(lift), leading = "${i + 1}",
                        selecting = selecting, selected = row.entryId in selected, dragging = isDragging,
                        onLongClick = { selecting = true; selected = selected + row.entryId },
                        handle = {
                            ReorderHandle(
                                "Reorder ${row.song.title}",
                                // Press-and-hold to pick up; any movement before the hold completes cancels it and the list scrolls normally.
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { dragging = false; vm.reorder(local.map { r -> r.entryId }) }
                                ),
                                onMoveUp = if (i > 0) ({ moveBy(i, -1) }) else null,
                                onMoveDown = if (i < local.lastIndex) ({ moveBy(i, 1) }) else null
                            )
                        },
                        extraMenu = { close ->
                            DropdownMenuItem({ Text("Remove from playlist") }, { vm.removeEntries(listOf(row.entryId)); close() }, leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) })
                        },
                        onClick = {
                            if (selecting) { selected = if (row.entryId in selected) selected - row.entryId else selected + row.entryId }
                            else { player.playItems(local.toList(), i) }
                        }
                    )
                }
            }
        }
    }
    if (adding) SongPickerDialog(vm, exclude = local.map { it.song.id }.toSet(), onDismiss = { adding = false }) { vm.addToPlaylists(listOf(pid), it) }
    picker?.let { mode ->
        PlaylistPickerDialog(
            vm, onDismiss = { picker = null },
            title = if (mode == 1) "Move to playlist" else "Copy to playlist", confirm = if (mode == 1) "Move" else "Copy", excludeId = pid
        ) { targets ->
            vm.addToPlaylists(targets, chosen.map { it.song.id })
            if (mode == 1) vm.removeEntries(chosen.map { it.entryId })
            exit()
        }
    }
}

// ============================ QUEUE ============================
private data class QRow(val uid: Long, val item: QueueItem)

@Composable
fun QueueScreen(env: Env) {
    val ps = env.ps
    val rows = remember { mutableStateListOf<QRow>() }
    var dragging by remember { mutableStateOf(false) }
    var uid by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ps.queue) { if (!dragging) { rows.clear(); ps.queue.forEach { rows.add(QRow(uid++, it)) } } }
    val lazy = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rs = rememberReorderableLazyListState(lazy) { from, to -> rows.add(to.index, rows.removeAt(from.index)); player.moveItem(from.index, to.index) }
    var confirmClear by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    fun moveBy(i: Int, d: Int) { val j = i + d; if (j in rows.indices) { rows.add(j, rows.removeAt(i)); player.moveItem(i, j) } }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(env.nav.back, "Queue") {
            if (ps.index in rows.indices) IconAction(Icons.Default.MyLocation, "Scroll to the playing song", { scope.launch { lazy.animateScrollToItem(ps.index) } })
            if (rows.isNotEmpty()) TextButton({ confirmClear = true }) { Text("Clear") }
        }
        ScreenTitle("Up next", if (rows.isEmpty()) null else "${rows.size} songs")
        if (rows.isEmpty()) Empty(Icons.Default.Queue, "Your queue is empty", "Play something, or use “Add to queue” and “Play next” from any song’s menu.")
        else Text("Press and hold ≡ to reorder. Separate from saved playlists — editing it never changes them.", Modifier.padding(horizontal = Dimens.screen, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        LazyColumn(state = lazy, contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(rows, key = { _, r -> r.uid }) { i, r ->
                ReorderableItem(rs, key = r.uid) { isDragging ->
                    var menu by remember { mutableStateOf(false) }
                    val lift by animateDpAsState(if (isDragging) 10.dp else 0.dp, label = "lift")
                    val current = i == ps.index
                    TrackRow(
                        title = r.item.title, subtitle = r.item.artist, uri = r.item.uri, seed = r.item.title,
                        overline = when { current -> "NOW PLAYING"; i == ps.index + 1 -> "UP NEXT"; else -> null },
                        onClickLabel = if (current) "Play or pause ${r.item.title}" else "Play ${r.item.title}",
                        onClick = { if (current) player.togglePlay() else player.skipTo(i) },
                        modifier = Modifier.shadow(lift), isCurrent = current, isPlaying = current && ps.isPlaying, dragging = isDragging
                    ) {
                        // Removal lives in the menu (two deliberate taps) instead of a one-tap ✕ sitting next to the drag handle.
                        Box {
                            IconAction(Icons.Default.MoreVert, "More options for ${r.item.title}", { menu = true }, tint = cs.onSurfaceVariant)
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Go to artist") }, { menu = false; env.nav.artist(r.item.artist) }, leadingIcon = { Icon(Icons.Default.Person, null) })
                                DropdownMenuItem({ Text("Remove from queue") }, { menu = false; player.removeItem(i) }, leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) })
                            }
                        }
                        ReorderHandle(
                            "Reorder ${r.item.title}",
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { dragging = false }
                            ),
                            onMoveUp = if (i > 0) ({ moveBy(i, -1) }) else null,
                            onMoveDown = if (i < rows.lastIndex) ({ moveBy(i, 1) }) else null
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Clear the queue?", "Playback will stop. Saved playlists are not affected.", { player.clearQueue(); confirmClear = false }, { confirmClear = false })
}
