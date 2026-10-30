package com.example.localplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.data.PlaylistRow
import com.example.localplayer.data.SongEntity

// ============================ LIBRARY ============================
private val libTabs = listOf("Songs", "Albums", "Artists", "Playlists", "Liked")

@Composable
fun LibraryScreen(env: Env, tab: Int, onTab: (Int) -> Unit) {
    val songs by env.vm.songs.collectAsState()
    val importing by env.vm.importing.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library") { IconAction(Icons.Default.Add, "Import music", env.import) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.screen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            libTabs.forEachIndexed { i, t -> Pill(t, tab == i, { onTab(i) }) }
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp, start = Dimens.screen, end = Dimens.screen), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when (tab) {
            0 -> SongsPane(env, songs, Icons.Default.LibraryMusic, "No songs yet", "Tap + to import one or many MP3 files.")
            1 -> AlbumsPane(env)
            2 -> ArtistsPane(env)
            3 -> PlaylistsPane(env)
            else -> {
                val liked = remember(songs, env.liked) { songs.filter { it.id in env.liked } }
                SongsPane(env, liked, Icons.Default.FavoriteBorder, "No liked songs", "Tap the heart on the player, or use a song’s menu, to collect favourites here.")
            }
        }
        }
    }
}

@Composable
private fun SongsPane(env: Env, songs: List<SongEntity>, emptyIcon: androidx.compose.ui.graphics.vector.ImageVector, emptyTitle: String, emptyHint: String) {
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val cs = MaterialTheme.colorScheme
    val q = query.trim()
    val shown = remember(songs, sort, q) {
        val base = when (sort) {
            1 -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            2 -> songs.sortedByDescending { it.addedAt }
            else -> songs.sortedBy { it.title.lowercase() }
        }
        if (q.isEmpty()) base else base.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
    }
    val avail = remember(shown) { shown.filter { it.available } }
    val exit = { selecting = false; selected = emptySet() }
    BackHandler(selecting) { exit() }
    if (songs.isEmpty()) { Empty(emptyIcon, emptyTitle, emptyHint); return }
    val chosen = shown.filter { it.id in selected }
    val chosenIds = chosen.map { it.id }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(selected.size, { exit() }, { selected = shown.map { it.id }.toSet() }, listOf(
                SelAction(Icons.Default.PlaylistAdd, "Add to playlist") { env.addToPlaylist(chosenIds); exit() },
                SelAction(Icons.Default.Queue, "Add to queue") { player.addToQueue(chosen.filter { it.available }); exit() },
                SelAction(Icons.Default.PlaylistPlay, "Play next") { player.playNext(chosen.filter { it.available }); exit() },
                SelAction(Icons.Default.Favorite, "Add to Liked songs") { env.vm.setLiked(chosenIds, true); exit() },
                SelAction(Icons.Default.FavoriteBorder, "Remove from Liked songs") { env.vm.setLiked(chosenIds, false); exit() },
                SelAction(Icons.Default.Delete, "Remove from library") { env.removeSongs(chosen); exit() }
            ))
        } else {
            Row(Modifier.padding(horizontal = Dimens.screen, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayFab("Play all", { player.playSongs(avail) }, size = 52.dp)
                IconAction(Icons.Default.Shuffle, "Shuffle all", { player.playShuffled(avail) }, Modifier.clip(CircleShape).background(cs.surfaceContainerHigh))
                IconAction(Icons.Default.Checklist, "Select songs", { selecting = true }, Modifier.clip(CircleShape).background(cs.surfaceContainerHigh))
                Spacer(Modifier.weight(1f))
                Box {
                    Row(Modifier.pressClick("Sort songs") { sortMenu = true }.height(36.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapVert, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(4.dp))
                        Text(listOf("Title", "Artist", "Recently added")[sort], style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf("Title", "Artist", "Recently added").forEachIndexed { i, label ->
                            DropdownMenuItem({ Text(label) }, { sort = i; sortMenu = false }, leadingIcon = { if (sort == i) Icon(Icons.Default.Check, null) })
                        }
                    }
                }
            }
        }
        SearchField(query, { query = it }, "Search songs, artists, albums", Modifier.padding(horizontal = Dimens.screen, vertical = 4.dp))
        if (shown.isEmpty()) Empty(Icons.Default.SearchOff, "No matches", "No songs match “$q”.")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.id }) { s ->
                SongRow(
                    s, env, selecting = selecting, selected = s.id in selected,
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

@Composable
private fun AlbumsPane(env: Env) {
    val albums by env.vm.albums.collectAsState()
    if (albums.isEmpty()) { Empty(Icons.Default.Album, "No albums yet", "Albums appear automatically from your songs’ tags."); return }
    LazyVerticalGrid(
        GridCells.Fixed(2), contentPadding = PaddingValues(start = Dimens.screen, end = Dimens.screen, bottom = 24.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(albums, key = { it.name }) { a ->
            CoverCard(a.songs.first().uri, a.name, a.artist, { env.nav.album(a.name) })
        }
    }
}

@Composable
private fun ArtistsPane(env: Env) {
    val artists by env.vm.artists.collectAsState()
    if (artists.isEmpty()) { Empty(Icons.Default.Person, "No artists yet", "Artists appear automatically from your songs’ tags."); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(artists, key = { it.name }) { a -> ArtistRow(a.name, a.songs.first().uri, "${a.songs.size} songs · ${a.albumCount} albums") { env.nav.artist(a.name) } }
    }
}

@Composable
fun ArtistRow(name: String, uri: String?, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = "Open $name", onClick = onClick).padding(horizontal = Dimens.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtThumb(uri, Modifier.size(56.dp), CircleShape, name, 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistsPane(env: Env) {
    val lists by env.vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistRow?>(null) }
    var deleting by remember { mutableStateOf<PlaylistRow?>(null) }
    val cs = MaterialTheme.colorScheme
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = "Create playlist") { creating = true }.padding(horizontal = Dimens.screen, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(Dimens.radiusS)).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = cs.primary) }
                Text("New playlist", Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
        if (lists.isEmpty()) item { Empty(Icons.Default.PlaylistPlay, "No playlists yet", "Create one, then add or import songs straight into it.") }
        items(lists, key = { it.id }) { p ->
            var open by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = "Open ${p.name}") { env.nav.playlist(p.id) }.padding(start = Dimens.screen, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtThumb(p.coverUri, Modifier.size(56.dp), RoundedCornerShape(Dimens.radiusS), p.name, 26.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.songCount} songs", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Box {
                    IconAction(Icons.Default.MoreVert, "More options for ${p.name}", { open = true }, tint = cs.onSurfaceVariant)
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem({ Text("Rename") }, { renaming = p; open = false }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem({ Text("Delete") }, { deleting = p; open = false }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
        }
    }
    if (creating) NameDialog("New playlist", "", { creating = false }) { env.vm.createPlaylist(it); creating = false }
    renaming?.let { p -> NameDialog("Rename playlist", p.name, { renaming = null }) { env.vm.rename(p.id, it); renaming = null } }
    deleting?.let { p -> ConfirmDialog("Delete “${p.name}”?", "Songs stay in your library and files are untouched.", { env.vm.deletePlaylist(p.id); deleting = null }, { deleting = null }) }
}

// ============================ SEARCH ============================
@Composable
/** [online] is the extension point for the future web-search section (see OnlineSearch.kt); null = local-only. */
fun SearchScreen(env: Env, online: (androidx.compose.foundation.lazy.LazyListScope.(query: String) -> Unit)? = null) {
    var q by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val songs by env.vm.songs.collectAsState()
    val albums by env.vm.albums.collectAsState()
    val artists by env.vm.artists.collectAsState()
    val playlists by env.vm.playlists.collectAsState()
    val cs = MaterialTheme.colorScheme
    val query = q.trim()
    val songHits = remember(query, songs) { if (query.isEmpty()) emptyList() else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }.take(30) }
    val albumHits = remember(query, albums) { if (query.isEmpty()) emptyList() else albums.filter { it.name.contains(query, true) || it.artist.contains(query, true) }.take(10) }
    val artistHits = remember(query, artists) { if (query.isEmpty()) emptyList() else artists.filter { it.name.contains(query, true) }.take(10) }
    val playlistHits = remember(query, playlists) { if (query.isEmpty()) emptyList() else playlists.filter { it.name.contains(query, true) }.take(10) }
    val avail = remember(songHits) { songHits.filter { it.available } }
    val none = query.isNotEmpty() && songHits.isEmpty() && albumHits.isEmpty() && artistHits.isEmpty() && playlistHits.isEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search")
        Row(
            Modifier.padding(horizontal = Dimens.screen).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (q.isEmpty()) Text("Songs, albums, artists, playlists", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                BasicTextField(
                    q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
                )
            }
            if (q.isNotEmpty()) IconAction(Icons.Default.Close, "Clear search", { q = "" }, tint = cs.onSurfaceVariant)
        }
        when {
            songs.isEmpty() -> Empty(Icons.Default.Search, "Nothing to search yet", "Import some music first, then find it here.")
            query.isEmpty() -> Empty(Icons.Default.Search, "Search your library", "Find songs, albums, artists and playlists by name.")
            none -> Empty(Icons.Default.SearchOff, "No results for “$query”", "Check the spelling or try a shorter search.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (songHits.isNotEmpty()) {
                    item { SectionTitle("Songs") }
                    items(songHits, key = { "s${it.id}" }) { s -> SongRow(s, env, onClick = { focus.clearFocus(); if (s.available) player.playSongs(avail, avail.indexOf(s)) else env.locate(s.id) }) }
                }
                if (albumHits.isNotEmpty()) {
                    item { SectionTitle("Albums") }
                    items(albumHits, key = { "a${it.name}" }) { a ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { env.nav.album(a.name) }.padding(horizontal = Dimens.screen, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArtThumb(a.songs.first().uri, Modifier.size(56.dp), RoundedCornerShape(Dimens.radiusS), a.name, 26.dp)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Album · ${a.artist}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                if (artistHits.isNotEmpty()) {
                    item { SectionTitle("Artists") }
                    items(artistHits, key = { "r${it.name}" }) { a -> ArtistRow(a.name, a.songs.first().uri, "Artist · ${a.songs.size} songs") { env.nav.artist(a.name) } }
                }
                online?.invoke(this, query)
                if (playlistHits.isNotEmpty()) {
                    item { SectionTitle("Playlists") }
                    items(playlistHits, key = { "p${it.id}" }) { p ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { env.nav.playlist(p.id) }.padding(horizontal = Dimens.screen, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArtThumb(p.coverUri, Modifier.size(56.dp), RoundedCornerShape(Dimens.radiusS), p.name, 26.dp)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Playlist · ${p.songCount} songs", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
