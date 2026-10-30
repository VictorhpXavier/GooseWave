package com.example.localplayer.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.example.localplayer.data.Art
import com.example.localplayer.playback.PlayerUi
import com.example.localplayer.playback.repeatLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ============================ MINI PLAYER (part of the bottom chrome) ============================
@Composable
fun MiniPlayer(ps: PlayerUi, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = ps.current ?: return
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open player", onClick = onOpen)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtThumb(cur.uri, Modifier.size(46.dp), RoundedCornerShape(8.dp), seed = cur.title)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(cur.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconAction(
                if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, repeatLabel(ps.repeat), { player.cycleRepeat() },
                tint = if (ps.repeat != Player.REPEAT_MODE_OFF) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f), size = 22.dp
            )
            IconAction(if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (ps.isPlaying) "Pause" else "Play", { player.togglePlay() }, size = 30.dp)
            IconAction(Icons.Default.SkipNext, "Next track", { player.next() })
        }
        MiniProgress()
    }
}

@Composable
private fun MiniProgress() {
    val pr by player.progress.collectAsState()
    val frac = if (pr.durationMs > 0) (pr.positionMs.toFloat() / pr.durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

// ============================ SEEK BAR ============================
@Composable
fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit, onScrub: (Float?) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(1) }
    val frac = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thick by animateDpAsState(if (drag != null) 8.dp else 4.dp, label = "seekThick")
    val thumb by animateDpAsState(if (drag != null) 20.dp else 12.dp, label = "seekThumb")
    val density = LocalDensity.current
    Box(
        modifier.fillMaxWidth().height(40.dp).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(duration) { detectTapGestures { o -> if (duration > 0) onSeek((o.x / widthPx).coerceIn(0f, 1f).times(duration).toLong()) } }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / widthPx).coerceIn(0f, 1f); onScrub(drag) },
                    onDragEnd = { drag?.let { if (duration > 0) onSeek((it * duration).toLong()) }; drag = null; onScrub(null) },
                    onDragCancel = { drag = null; onScrub(null) },
                    onHorizontalDrag = { change, _ -> change.consume(); drag = (change.position.x / widthPx).coerceIn(0f, 1f); onScrub(drag) }
                )
            }
            .semantics {
                contentDescription = "Seek bar"
                stateDescription = "${fmt(position)} of ${fmt(duration)}"
                progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                setProgress { v -> if (duration > 0) { onSeek((v.coerceIn(0f, 1f) * duration).toLong()); true } else false }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(thick).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(frac).height(thick).clip(CircleShape).background(Color.White))
        Box(
            Modifier.offset { IntOffset((frac * widthPx - with(density) { thumb.toPx() } / 2f).roundToInt(), 0) }
                .size(thumb).clip(CircleShape).background(Color.White)
        )
    }
}

// ============================ NOW PLAYING ============================
@Composable
fun NowPlayingScreen(env: Env, onClose: () -> Unit, onQueue: () -> Unit, onArtist: (String) -> Unit) {
    val ps = env.ps
    val cur = ps.current
    val haptic = LocalHapticFeedback.current
    val pr by player.progress.collectAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    val v by Art.version.collectAsState()
    val white = Color.White
    val dim = white.copy(alpha = 0.68f)
    val base = Color(0xFF090D15)

    // Background tint from the cover; blended heavily toward the base so text contrast never drops.
    val tint by produceState<Color?>(null, cur?.uri, v) {
        value = withContext(Dispatchers.IO) {
            val f = cur?.uri?.let { Art.file(it) }?.takeIf { it.exists() } ?: return@withContext null
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return@withContext null
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val c = p.getDarkMutedColor(p.getDarkVibrantColor(p.getDominantColor(0)))
            if (c == 0) null else Color(c)
        }
    }
    val top by animateColorAsState(lerp(base, tint ?: Color(0xFF1B2440), 0.75f), tween(600), label = "npTop")
    val artScale by animateFloatAsState(if (ps.isPlaying) 1f else 0.88f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), label = "artScale")
    val isLiked = cur?.mediaId?.toLongOrNull()?.let { it in env.liked } == true

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(0f to top, 0.6f to lerp(top, base, 0.7f), 1f to base))
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 140f) onClose() }, onVerticalDrag = { _, d -> total += d })
            }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Default.KeyboardArrowDown, "Close player", onClose, tint = white, size = 32.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = dim)
                    if (ps.queue.isNotEmpty()) Text("${ps.index + 1} of ${ps.queue.size}", style = MaterialTheme.typography.labelMedium, color = white.copy(alpha = 0.85f))
                }
                Spacer(Modifier.size(48.dp))
            }

            // artwork (square, sized to the smaller of the available width/height)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                AnimatedContent(
                    targetState = cur?.uri ?: "", label = "art",
                    transitionSpec = { (fadeIn(tween(380)) + scaleIn(initialScale = 0.94f, animationSpec = tween(380))) togetherWith fadeOut(tween(220)) }
                ) { uri ->
                    ArtThumb(
                        uri, Modifier.size(side).graphicsLayer { scaleX = artScale; scaleY = artScale }
                            .shadow(24.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black),
                        RoundedCornerShape(18.dp), seed = cur?.title ?: "", iconSize = 96.dp
                    )
                }
            }

            // title / artist / like
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(cur?.title ?: "Nothing playing", label = "title", transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }) { t ->
                        Text(t, style = MaterialTheme.typography.headlineSmall, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val artist = cur?.artist.orEmpty()
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = artist.isNotEmpty(), onClickLabel = "Open artist $artist") { onArtist(artist) }
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(artist, style = MaterialTheme.typography.bodyLarge, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (artist.isNotEmpty()) Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = dim)
                    }
                }
                val likeScale by animateFloatAsState(if (isLiked) 1.0f else 0.92f, spring(dampingRatio = 0.4f), label = "like")
                IconAction(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isLiked) "Remove from liked songs" else "Add to liked songs",
                    { cur?.mediaId?.toLongOrNull()?.let { env.vm.toggleLike(it); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
                    Modifier.graphicsLayer { scaleX = likeScale; scaleY = likeScale }, tint = if (isLiked) Accent else white, size = 28.dp
                )
            }

            // seek
            Spacer(Modifier.height(8.dp))
            SeekBar(pr.positionMs, pr.durationMs, { player.seekTo(it) }, { scrub = it })
            val shown = scrub?.let { (it * pr.durationMs).toLong() } ?: pr.positionMs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(shown), style = MaterialTheme.typography.labelMedium, color = dim)
                Text("-" + fmt((pr.durationMs - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = dim)
            }

            // primary controls
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToggleIcon(Icons.Default.Shuffle, if (ps.shuffle) "Shuffle on" else "Shuffle off", ps.shuffle) { player.toggleShuffle(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                IconAction(Icons.Default.SkipPrevious, "Previous track", { player.previous() }, tint = white, size = 40.dp)
                Box(
                    Modifier.size(76.dp).pressClick(label = if (ps.isPlaying) "Pause" else "Play") { player.togglePlay() }
                        .clip(CircleShape).background(Accent).semantics { contentDescription = if (ps.isPlaying) "Pause" else "Play" },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(ps.isPlaying, animationSpec = tween(160), label = "playpause") { playing ->
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(40.dp), tint = OnAccent)
                    }
                }
                IconAction(Icons.Default.SkipNext, "Next track", { player.next() }, tint = white, size = 40.dp)
                ToggleIcon(
                    if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    repeatLabel(ps.repeat),
                    ps.repeat != Player.REPEAT_MODE_OFF
                ) { player.cycleRepeat(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }

            // secondary controls
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        Modifier.pressClick("Playback speed", Role.Button) { speedMenu = true }.height(36.dp).clip(CircleShape)
                            .background(white.copy(alpha = 0.12f)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, null, Modifier.size(18.dp), tint = white); Spacer(Modifier.width(6.dp))
                        Text(speedLabel(ps.speed), style = MaterialTheme.typography.labelLarge, color = white)
                    }
                    DropdownMenu(speedMenu, { speedMenu = false }) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                            DropdownMenuItem(
                                { Text(speedLabel(sp)) }, { player.setSpeed(sp); speedMenu = false },
                                leadingIcon = { if (ps.speed == sp) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
                Row(
                    Modifier.pressClick("Open queue", Role.Button, onClick = onQueue).height(36.dp).clip(CircleShape)
                        .background(white.copy(alpha = 0.12f)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QueueMusic, null, Modifier.size(18.dp), tint = white); Spacer(Modifier.width(6.dp))
                    Text("Up next", style = MaterialTheme.typography.labelLarge, color = white)
                }
            }
        }
    }
}

private fun speedLabel(s: Float) = (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().trimEnd('0')) + "×"

/** Shuffle / repeat: icon turns accent and shows a small dot when active. */
@Composable
private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) Accent else Color.White.copy(alpha = 0.7f), label = "toggleTint")
    Column(Modifier.size(48.dp).pressClick(label = desc, onClick = onClick).semantics { contentDescription = desc; selected = active }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint)
        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (active) Accent else Color.Transparent))
    }
}
