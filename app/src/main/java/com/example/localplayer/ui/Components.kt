package com.example.localplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import android.provider.Settings
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.localplayer.data.*
import com.example.localplayer.playback.PlayerUi

val player get() = AppGraph.player
fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
fun fmtTotal(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
}

// ---------- navigation + shared environment passed to screens ----------
class Nav(
    val album: (String) -> Unit, val artist: (String) -> Unit, val playlist: (Long) -> Unit,
    val queue: () -> Unit, val back: () -> Unit, val library: (Int) -> Unit, val player: () -> Unit
)

class Env(
    val vm: MainViewModel, val nav: Nav, val ps: PlayerUi, val liked: Set<Long>,
    val addToPlaylist: (List<Long>) -> Unit, val removeSong: (SongEntity) -> Unit, val removeSongs: (List<SongEntity>) -> Unit,
    val locate: (Long) -> Unit, val import: () -> Unit
)

// ---------- interaction ----------
/** True when the user turned system animations off (Developer options / "Remove animations"). */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Clickable with a subtle press-scale (disabled when animations are off). Replaces default ripples on controls. */
fun Modifier.pressClick(label: String? = null, role: Role? = Role.Button, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val s by animateFloatAsState(if (pressed && !reduce) 0.93f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
}

@Composable
fun IconAction(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 24.dp) {
    Box(modifier.size(48.dp).pressClick(label = desc, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size), tint = tint)
    }
}

/** The one filled, accent-colored circular action. Used for Play everywhere. */
@Composable
fun PlayFab(desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, icon: ImageVector = Icons.Default.PlayArrow) {
    Box(
        modifier.size(size).pressClick(label = desc, onClick = onClick).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.onSurface else cs.surfaceContainerHigh, label = "pillBg")
    val fg by animateColorAsState(if (selected) cs.surface else cs.onSurface, label = "pillFg")
    Row(
        Modifier.pressClick(role = Role.Tab, onClick = onClick).height(36.dp).clip(CircleShape).background(bg).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

// ---------- artwork ----------
/** Cached embedded cover, or a generated, name-seeded gradient so missing art still feels designed. */
@Composable
fun ArtThumb(uri: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(Dimens.radiusS), seed: String = "", iconSize: Dp = 24.dp) {
    val v by Art.version.collectAsState()
    val file = remember(uri, v) { if (uri.isNullOrEmpty()) null else Art.file(uri).takeIf { it.exists() } }
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (file != null) {
            AsyncImage(
                ImageRequest.Builder(LocalContext.current).data(file).crossfade(220).build(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        } else {
            val hue = Math.floorMod((seed.ifEmpty { uri ?: "" }).hashCode(), 360).toFloat()
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.hsv(hue, 0.28f, 0.36f), Color.hsv((hue + 40f) % 360f, 0.35f, 0.20f)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.35f)) }
        }
    }
}

// ---------- text blocks ----------
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = Dimens.screen, end = 8.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(start = Dimens.screen, end = Dimens.screen, top = 28.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
fun DetailTopBar(onBack: () -> Unit, title: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.Default.ArrowBack, "Back", onBack)
        Text(title ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun Empty(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(24.dp)); action() }
    }
}

// ---------- now-playing indicator ----------
/** Three animated bars. Frozen (short bars) when paused or when system animations are off. */
@Composable
fun EqualizerBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "eq1")
    val b by t.animateFloat(0.6f, 0.2f, infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Reverse), label = "eq2")
    val c by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "eq3")
    val live = active && !reduce
    Row(modifier.size(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { f ->
            Box(Modifier.width(3.dp).fillMaxHeight(if (live) f else 0.4f).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

// ---------- reorder handle ----------
/**
 * The ONLY element that can start a reorder. [drag] must be the library's `longPressDraggableHandle(...)`:
 * a press-and-hold is required, and moving before the hold completes cancels the gesture, so a vertical swipe that
 * starts on the handle scrolls the list instead. It lives outside the row's click target, so a stray tap does nothing.
 * Screen-reader users get "Move up / Move down" actions instead of a drag.
 */
@Composable
fun ReorderHandle(desc: String, drag: Modifier, onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outlineVariant
    Box(
        Modifier.size(44.dp, 56.dp)
            .drawBehind {
                val h = 28.dp.toPx(); val y = (size.height - h) / 2f
                drawLine(line, Offset(0f, y), Offset(0f, y + h), strokeWidth = 1.dp.toPx())
            }
            .then(drag)
            .semantics {
                contentDescription = desc
                customActions = listOfNotNull(
                    onMoveUp?.let { f -> CustomAccessibilityAction("Move up") { f(); true } },
                    onMoveDown?.let { f -> CustomAccessibilityAction("Move down") { f(); true } }
                )
            },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.DragHandle, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.55f)) }
}

// ---------- track row (shared by library songs and the queue) ----------
/**
 * Layout: [leading #] [art] [title / artist] [duration] | trailing (overflow menu, optional drag handle).
 * Only the left part is the click target (tap = play/pause, long-press = select); [trailing] sits outside it,
 * so playing, selecting, menu and reordering are four separate interactions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    title: String, subtitle: String, uri: String?, seed: String, onClickLabel: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, overline: String? = null, leading: String? = null, showArt: Boolean = true,
    durationMs: Long? = null, subtitleIsError: Boolean = false, isCurrent: Boolean = false, isPlaying: Boolean = false, liked: Boolean = false,
    selecting: Boolean = false, selected: Boolean = false, dragging: Boolean = false, onLongClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when { dragging -> cs.surfaceContainerHighest; selected -> cs.primary.copy(alpha = 0.14f); isCurrent -> cs.primary.copy(alpha = 0.07f); else -> Color.Transparent },
        tween(150), label = "rowBg"
    )
    val accent = cs.primary
    Row(
        modifier.fillMaxWidth().heightIn(min = Dimens.row).background(bg).then(
            if (dragging) Modifier.drawBehind {   // insertion marker: the dragged row is framed, neighbours slide apart to open the gap
                drawLine(accent, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
            } else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = Dimens.row)
                .combinedClickable(onClickLabel = onClickLabel, onLongClickLabel = "Select", onLongClick = onLongClick, onClick = onClick)
                .semantics {
                    if (isCurrent) stateDescription = if (isPlaying) "Now playing" else "Paused"
                    if (selecting) this.selected = selected
                }
                .padding(start = Dimens.screen, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) Checkbox(selected, null, Modifier.padding(end = 12.dp))
            if (leading != null && !selecting) {
                Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                    if (isCurrent && !showArt) EqualizerBars(isPlaying, accent)
                    else Text(leading, style = MaterialTheme.typography.labelLarge, color = if (isCurrent) accent else cs.onSurfaceVariant)
                }
            }
            if (showArt) {
                Box(Modifier.size(Dimens.thumb).clip(RoundedCornerShape(Dimens.radiusS)), contentAlignment = Alignment.Center) {
                    ArtThumb(uri, Modifier.fillMaxSize(), seed = seed)
                    if (isCurrent) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { EqualizerBars(isPlaying, Color.White) }
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = if (showArt) 14.dp else 0.dp)) {
                if (overline != null) Text(overline, style = MaterialTheme.typography.labelSmall, color = accent)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = if (isCurrent) accent else cs.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (liked) Icon(Icons.Default.Favorite, "Liked", Modifier.padding(end = 4.dp).size(12.dp), tint = accent)
                    Text(subtitle, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = if (subtitleIsError) cs.error else cs.onSurfaceVariant)
                }
            }
            if (durationMs != null && durationMs > 0) Text(fmt(durationMs), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        if (selecting) Spacer(Modifier.width(12.dp)) else trailing()
    }
}

// ---------- song row with the standard action menu ----------
@Composable
fun SongRow(
    s: SongEntity, env: Env, modifier: Modifier = Modifier, leading: String? = null, showArt: Boolean = true,
    handle: (@Composable () -> Unit)? = null,
    extraMenu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    selecting: Boolean = false, selected: Boolean = false, dragging: Boolean = false, onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val current = env.ps.current?.mediaId == s.id.toString()
    val isLiked = s.id in env.liked
    TrackRow(
        title = s.title, subtitle = if (s.available) s.artist else "File unavailable — tap to locate", uri = s.uri, seed = s.album,
        onClickLabel = when { selecting -> "Toggle selection"; current && s.available -> "Play or pause ${s.title}"; else -> "Play ${s.title}" },
        // Tapping the song that is already loaded toggles play/pause instead of restarting it.
        onClick = { if (!selecting && current && s.available) player.togglePlay() else onClick() },
        modifier = modifier, leading = leading, showArt = showArt, durationMs = if (s.available) s.durationMs else null,
        subtitleIsError = !s.available, isCurrent = current, isPlaying = current && env.ps.isPlaying, liked = isLiked,
        selecting = selecting, selected = selected, dragging = dragging, onLongClick = onLongClick
    ) {
        Box {
            IconAction(Icons.Default.MoreVert, "More options for ${s.title}", { open = true }, tint = cs.onSurfaceVariant)
            DropdownMenu(open, { open = false }) {
                val close = { open = false }
                extraMenu?.invoke(this, close)
                DropdownMenuItem({ Text("Select") }, { onLongClick?.invoke(); close() }, leadingIcon = { Icon(Icons.Default.Checklist, null) })
                DropdownMenuItem({ Text("Play next") }, { player.playNext(listOf(s)); close() }, leadingIcon = { Icon(Icons.Default.PlaylistPlay, null) })
                DropdownMenuItem({ Text("Add to queue") }, { player.addToQueue(listOf(s)); close() }, leadingIcon = { Icon(Icons.Default.Queue, null) })
                DropdownMenuItem({ Text("Add to playlist…") }, { env.addToPlaylist(listOf(s.id)); close() }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) })
                DropdownMenuItem(
                    { Text(if (isLiked) "Remove from Liked songs" else "Add to Liked songs") }, { env.vm.toggleLike(s.id); close() },
                    leadingIcon = { Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }
                )
                DropdownMenuItem({ Text("Go to album") }, { env.nav.album(s.album); close() }, leadingIcon = { Icon(Icons.Default.Album, null) })
                DropdownMenuItem({ Text("Go to artist") }, { env.nav.artist(s.artist); close() }, leadingIcon = { Icon(Icons.Default.Person, null) })
                if (!s.available) DropdownMenuItem({ Text("Locate file…") }, { env.locate(s.id); close() }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                HorizontalDivider()
                // Destructive and last, visually distinct; still goes through a confirmation dialog.
                DropdownMenuItem(
                    { Text("Remove from library", color = cs.error) }, { env.removeSong(s); close() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = cs.error) }
                )
            }
        }
        handle?.invoke()
    }
}

// ---------- multi-select ----------
class SelAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Replaces the screen header while songs are being selected. First two actions are inline, the rest go in a menu. */
@Composable
fun SelectionBar(count: Int, onClose: () -> Unit, onAll: () -> Unit, actions: List<SelAction>) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Surface(color = cs.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Default.Close, "Cancel selection", onClose)
            Text("$count selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            IconAction(Icons.Default.SelectAll, "Select all", onAll)
            actions.take(2).forEach { a -> IconAction(a.icon, a.label, a.onClick, enabledAlpha(count)) }
            val more = actions.drop(2)
            if (more.isNotEmpty()) Box {
                IconAction(Icons.Default.MoreVert, "More actions", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    more.forEach { a -> DropdownMenuItem({ Text(a.label) }, { menu = false; if (count > 0) a.onClick() }, leadingIcon = { Icon(a.icon, null) }) }
                }
            }
        }
    }
}
private fun enabledAlpha(count: Int): Modifier = if (count > 0) Modifier else Modifier.graphicsLayer { alpha = 0.38f }

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
            )
        }
        if (value.isNotEmpty()) IconAction(Icons.Default.Close, "Clear search", { onChange("") }, tint = cs.onSurfaceVariant, size = 20.dp)
    }
}

/** Square cover + two lines of text. Used in carousels and grids. */
@Composable
fun CoverCard(uri: String?, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, circle: Boolean = false, seed: String = title) {
    Column(modifier.pressClick(role = Role.Button, onClick = onClick)) {
        ArtThumb(uri, Modifier.fillMaxWidth().aspectRatio(1f), if (circle) CircleShape else RoundedCornerShape(Dimens.radiusM), seed, 40.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- dialogs ----------
@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text("Confirm") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onOk(t) }, enabled = t.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun PlaylistPickerDialog(
    vm: MainViewModel, onDismiss: () -> Unit, title: String = "Add to playlists", confirm: String = "Add",
    excludeId: Long? = null, onOk: (Set<Long>) -> Unit
) {
    val all by vm.playlists.collectAsState()
    val lists = all.filter { it.id != excludeId }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            if (lists.isEmpty()) Text("Create a playlist first (Library → Playlists).")
            LazyColumn { items(lists) { p ->
                Row(Modifier.fillMaxWidth().clickable { sel = if (p.id in sel) sel - p.id else sel + p.id }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(p.id in sel, { sel = if (p.id in sel) sel - p.id else sel + p.id }); Text(p.name)
                }
            } }
        },
        confirmButton = { TextButton({ onOk(sel); onDismiss() }, enabled = sel.isNotEmpty()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun SongPickerDialog(vm: MainViewModel, exclude: Set<Long>, onDismiss: () -> Unit, onOk: (List<Long>) -> Unit) {
    val songs by vm.songs.collectAsState()
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val avail = songs.filter { it.id !in exclude && (query.isEmpty() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)) }
    var sel by remember { mutableStateOf(listOf<Long>()) }   // selection order = append order
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add songs (${sel.size})") },
        text = {
            Column {
                SearchField(q, { q = it }, "Search your songs")
                Spacer(Modifier.height(8.dp))
                if (avail.isEmpty()) Text(if (query.isEmpty()) "No more songs in the library to add." else "No songs match “$query”.")
                LazyColumn(Modifier.weight(1f, fill = false)) { items(avail, key = { it.id }) { s ->
                    Row(Modifier.fillMaxWidth().clickable { sel = if (s.id in sel) sel - s.id else sel + s.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.id in sel, { sel = if (s.id in sel) sel - s.id else sel + s.id })
                        ArtThumb(s.uri, Modifier.size(40.dp), RoundedCornerShape(4.dp), s.album, 20.dp)
                        Column(Modifier.padding(start = 10.dp)) { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(s.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                } }
            }
        },
        confirmButton = { TextButton({ onOk(sel); onDismiss() }, enabled = sel.isNotEmpty()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
