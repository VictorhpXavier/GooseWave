package com.example.localplayer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localplayer.data.SongEntity

private const val SEP = "\u001F"

@Composable
fun Root(vm: MainViewModel = viewModel()) {
    val ps by player.state.collectAsState()
    val liked by vm.liked.collectAsState()
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }          // 0 Home, 1 Search, 2 Library
    var libTab by rememberSaveable { mutableIntStateOf(0) }       // Library segment
    var stack by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var showNow by rememberSaveable { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<List<Long>?>(null) }
    var removing by remember { mutableStateOf<List<SongEntity>?>(null) }
    var relocateId by remember { mutableStateOf<Long?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.import(it) }
    val locatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val id = relocateId; if (u != null && id != null) vm.relocate(id, u); relocateId = null
    }

    fun push(r: String) { stack = ArrayList(stack + r) }
    val nav = Nav(
        album = { push("album$SEP$it") }, artist = { push("artist$SEP$it") }, playlist = { push("playlist$SEP$it") },
        queue = { push("queue") }, back = { if (stack.isNotEmpty()) stack = ArrayList(stack.dropLast(1)) },
        library = { i -> libTab = i; tab = 2; stack = ArrayList() }, player = { showNow = true }
    )
    val env = Env(vm, nav, ps, liked,
        addToPlaylist = { addTo = it }, removeSong = { removing = listOf(it) }, removeSongs = { removing = it },
        locate = { relocateId = it; locatePicker.launch(arrayOf("audio/*")) },
        import = { importPicker.launch(arrayOf("audio/mpeg", "audio/*")) })

    BackHandler(showNow || stack.isNotEmpty()) { if (showNow) showNow = false else nav.back() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    when (tab) {
                        0 -> HomeScreen(env)
                        1 -> SearchScreen(env)
                        else -> LibraryScreen(env, libTab) { libTab = it }
                    }
                }
                DetailOverlay(stack.lastOrNull(), env)
            }
            if (!imeVisible) BottomChrome(env, tab) { tab = it; stack = ArrayList() }
        }

        AnimatedVisibility(
            showNow,
            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) {
            NowPlayingScreen(env, onClose = { showNow = false },
                onQueue = { showNow = false; push("queue") },
                onArtist = { a -> showNow = false; push("artist$SEP$a") })
        }
    }

    addTo?.let { ids -> PlaylistPickerDialog(vm, onDismiss = { addTo = null }) { vm.addToPlaylists(it, ids) } }
    removing?.let { list ->
        ConfirmDialog(
            if (list.size == 1) "Remove “${list[0].title}” from the library?" else "Remove ${list.size} songs from the library?",
            "They will also leave all playlists. The audio files themselves are not deleted.",
            { vm.removeSongs(list.map { it.id }); removing = null }, { removing = null })
    }
}

/** Own function (no implicit Column/Row receiver) so AnimatedVisibility resolves unambiguously. */
@Composable
private fun DetailOverlay(top: String?, env: Env) {
    val cs = MaterialTheme.colorScheme
    val last = remember { arrayOf("") }
    if (top != null) last[0] = top
    AnimatedVisibility(
        top != null,
        enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(220)),
        exit = slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(160))
    ) {
        Box(Modifier.fillMaxSize().background(cs.background).statusBarsPadding()) {
            Crossfade(last[0], label = "detail") { key ->
                val parts = key.split(SEP)
                when (parts[0]) {
                    "album" -> AlbumScreen(env, parts.getOrElse(1) { "" })
                    "artist" -> ArtistScreen(env, parts.getOrElse(1) { "" })
                    "playlist" -> PlaylistScreen(env, parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L)
                    "queue" -> QueueScreen(env)
                    else -> Spacer(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ============================ BOTTOM CHROME: mini-player + navigation as one surface ============================
@Composable
private fun BottomChrome(env: Env, tab: Int, onTab: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer) {
        Column(Modifier.navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
            if (env.ps.queue.isNotEmpty()) {
                MiniPlayer(env.ps) { env.nav.player() }
                Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
            }
            Row(Modifier.fillMaxWidth().height(60.dp)) {
                NavItem("Home", tab == 0, Icons.Filled.Home, Icons.Outlined.Home, { onTab(0) }, Modifier.weight(1f))
                NavItem("Search", tab == 1, Icons.Filled.Search, Icons.Outlined.Search, { onTab(1) }, Modifier.weight(1f))
                NavItem("Library", tab == 2, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, { onTab(2) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(label: String, isSelected: Boolean, filled: ImageVector, outlined: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val c = if (isSelected) cs.primary else cs.onSurfaceVariant
    Column(
        modifier.fillMaxHeight().pressClick(role = Role.Tab, onClick = onClick).semantics { selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(if (isSelected) filled else outlined, null, Modifier.size(26.dp), tint = c)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}
