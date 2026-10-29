package com.simonpena.mako

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Glow tokens, from docs/mockups/prototype.html.
private val GBg = Color(0xFF0C1112)
private val GSoft = Color.White.copy(alpha = .64f)
private val GFaint = Color.White.copy(alpha = .16f)
private val GHi = Color(0xFF9FD6CF)
private val GDeep = Color(0xFF0B1415)
private val GDeepest = Color(0xFF070D0E)

private fun display(size: Int) = TextStyle(
    fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02 * size).sp, color = Color.White,
)
private val body = TextStyle(fontSize = 15.sp, color = Color.White)

private sealed interface Screen {
    data object Library : Screen
    data class AlbumDetail(val albumId: Long) : Screen
    data object NowPlaying : Screen
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
fun MakoApp(
    hasPermission: Boolean,
    library: List<Album>?,
    player: PlayerHolder,
    onGrant: () -> Unit,
    onRescan: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(GBg)) {
        when {
            !hasPermission -> Welcome(onGrant)
            library == null -> Message("Building your library…")
            library.isEmpty() -> Empty(onRescan)
            else -> Main(library, player)
        }
    }
}

@Composable
private fun Main(library: List<Album>, player: PlayerHolder) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Library) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val albumOfTrack = remember(library) { library.flatMap { a -> a.tracks.map { it.id to a } }.toMap() }
    val currentAlbum = player.currentTrackId?.let { albumOfTrack[it] }
    val currentTrack = currentAlbum?.tracks?.firstOrNull { it.id == player.currentTrackId }

    when (val screen = stack.last()) {
        Screen.Library -> LibraryScreen(library, hasMini = currentTrack != null) { stack.add(Screen.AlbumDetail(it)) }
        is Screen.AlbumDetail -> {
            val album = library.firstOrNull { it.id == screen.albumId }
            if (album == null) stack.removeAt(stack.lastIndex) else AlbumScreen(
                album, currentTrack, hasMini = currentTrack != null,
                onBack = { stack.removeAt(stack.lastIndex) },
                onPlay = { player.play(album.tracks, it) },
            )
        }
        Screen.NowPlaying ->
            if (currentAlbum == null || currentTrack == null) stack.removeAt(stack.lastIndex)
            else NowPlaying(currentAlbum, currentTrack, player) { stack.removeAt(stack.lastIndex) }
    }
    if (stack.last() != Screen.NowPlaying && currentAlbum != null && currentTrack != null) {
        Box(Modifier.fillMaxSize()) {
            MiniPlayer(currentAlbum, currentTrack, player, Modifier.align(Alignment.BottomCenter)) {
                stack.add(Screen.NowPlaying)
            }
        }
    }
}

// ---------------------------------------------------------------- first run

@Composable
private fun Welcome(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF24525A), GBg),
                        center = Offset(size.width * .7f, size.height * .18f),
                        radius = size.width * 1.1f,
                    )
                )
            }
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
    ) {
        Text("Your music, on this phone.", style = display(34))
        Text(
            "Mako plays the music stored on this phone. No account and no Internet. " +
                "It can also play to speakers and TVs on your Wi-Fi.",
            style = body.copy(color = GSoft, lineHeight = 22.sp),
        )
        PillButton("Find my music", Modifier.fillMaxWidth(), onGrant)
    }
}

@Composable
private fun Empty(onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(GlowIcons.Music, null, tint = GHi, modifier = Modifier.size(56.dp))
        Text("No music found", style = display(28))
        Text(
            "Copy music into the Music folder on this phone, over USB file transfer or from a computer:",
            style = body.copy(color = GSoft, lineHeight = 22.sp),
        )
        Text(
            "adb push ./Music /sdcard/Music",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFCFE9E4)),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = .07f)).padding(12.dp),
        )
        PillButton("Scan again", onClick = onRescan)
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = body.copy(color = GSoft))
    }
}

@Composable
private fun PillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(CircleShape).background(GHi).clickable(onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0B1A1A)))
    }
}

// ---------------------------------------------------------------- library

@Composable
private fun LibraryScreen(library: List<Album>, hasMini: Boolean, onAlbum: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Library", style = display(28), modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (hasMini) 96.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(library, key = { it.id }) { album ->
                Column(Modifier.clickable { onAlbum(album.id) }) {
                    AlbumArt(album.id, px = 336, corner = 8.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        album.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = GSoft))
                }
            }
        }
    }
}

@Composable
private fun AlbumScreen(
    album: Album,
    current: Track?,
    hasMini: Boolean,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    val tint = rememberArt(album.id, 400)?.tint ?: placeholderTint(album.id)
    val fadePx = with(LocalDensity.current) { 380.dp.toPx() }
    val minutes = album.tracks.sumOf { it.durationMs } / 60000
    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, GBg), endY = fadePx)),
        contentPadding = PaddingValues(bottom = if (hasMini) 96.dp else 24.dp),
    ) {
        item {
            Box(Modifier.statusBarsPadding().padding(horizontal = 8.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
                IconSpot(GlowIcons.Back, "Back", onClick = onBack)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AlbumArt(album.id, px = 400, corner = 8.dp, modifier = Modifier.size(200.dp))
                Spacer(Modifier.height(4.dp))
                Text(album.title, style = display(26), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = TextStyle(fontSize = 14.sp, color = GSoft))
                val year = if (album.year > 0) "${album.year} · " else ""
                Text("$year${album.tracks.size} songs · $minutes min", style = TextStyle(fontSize = 12.sp, color = GSoft))
                Spacer(Modifier.height(4.dp))
                PillButton("Play") { onPlay(0) }
            }
        }
        itemsIndexed(album.tracks, key = { _, t -> t.id }) { i, track ->
            val on = current?.id == track.id
            val color = if (on) GHi else Color.White
            val soft = if (on) GHi else GSoft
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${if (track.number > 0) track.number else i + 1}", style = TextStyle(fontSize = 13.sp, color = soft), modifier = Modifier.width(28.dp))
                Text(track.title, style = body.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(fmt(track.durationMs), style = TextStyle(fontSize = 13.sp, color = soft))
            }
        }
    }
}

// ---------------------------------------------------------------- playback

@Composable
private fun rememberPosition(player: PlayerHolder, key: Any): Long {
    var pos by remember(key) { mutableLongStateOf(player.positionMs()) }
    LaunchedEffect(key, player.isPlaying) {
        while (true) {
            pos = player.positionMs()
            delay(if (player.isPlaying) 500 else 1500)
        }
    }
    return pos
}

@Composable
private fun NowPlaying(album: Album, track: Track, player: PlayerHolder, onClose: () -> Unit) {
    val tint = rememberArt(album.id, 560)?.tint ?: placeholderTint(album.id)
    val pos = rememberPosition(player, track.id)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, .62f to GDeep, 1f to GDeepest))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconSpot(GlowIcons.Down, "Close", onClick = onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM ALBUM", style = TextStyle(fontSize = 11.sp, letterSpacing = .7.sp, color = GSoft))
                    Text(album.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(44.dp))
            }
            AlbumArt(album.id, px = 560, corner = 10.dp, modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(.86f).aspectRatio(1f))
            Column {
                Text(track.title, style = display(26), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = TextStyle(fontSize = 16.sp, color = GSoft), modifier = Modifier.padding(top = 4.dp))
            }
            Column {
                SeekBar(pos, track.durationMs) { player.seekTo(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), style = TextStyle(fontSize = 12.sp, color = GSoft))
                    Text(fmt(track.durationMs), style = TextStyle(fontSize = 12.sp, color = GSoft))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconSpot(GlowIcons.Shuffle, "Shuffle", tint = if (player.shuffle) GHi else GSoft) { player.toggleShuffle() }
                IconSpot(GlowIcons.Previous, "Previous", iconSize = 32.dp) { player.previous() }
                Box(
                    Modifier.size(66.dp).clip(CircleShape).background(Color.White).clickable { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (player.isPlaying) GlowIcons.Pause else GlowIcons.Play, "Play or pause", tint = Color(0xFF0E2226), modifier = Modifier.size(32.dp))
                }
                IconSpot(GlowIcons.Next, "Next", iconSize = 32.dp) { player.next() }
                Box {
                    IconSpot(GlowIcons.Repeat, "Repeat", tint = if (player.repeatMode == 0) GSoft else GHi) { player.cycleRepeat() }
                    if (player.repeatMode == 1) {
                        Text("1", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GHi), modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp))
                    }
                }
            }
            OutputRow()
        }
    }
}

/** Only "This phone" for now; the picker arrives with UPnP (roadmap B1). */
@Composable
private fun OutputRow() {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GFaint))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(GlowIcons.Phone, null, tint = GHi, modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    append("Playing on ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append("This phone") }
                },
                style = TextStyle(fontSize = 13.sp, color = GSoft),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text("Headphones", style = TextStyle(fontSize = 12.sp, color = GSoft))
        }
    }
}

@Composable
private fun SeekBar(pos: Long, duration: Long, onSeek: (Long) -> Unit) {
    val fraction = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    fun seekAt(x: Float, width: Int) = onSeek(((x / width).coerceIn(0f, 1f) * duration).toLong())
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(duration) { detectTapGestures { seekAt(it.x, size.width) } }
            .pointerInput(duration) { detectHorizontalDragGestures { change, _ -> seekAt(change.position.x, size.width) } },
    ) {
        val h = 4.dp.toPx()
        val y = size.height / 2 - h / 2
        drawRoundRect(GFaint, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(Color.White, Offset(0f, y), Size(size.width * fraction, h), CornerRadius(h / 2))
        drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * fraction, size.height / 2))
    }
}

@Composable
private fun MiniPlayer(album: Album, track: Track, player: PlayerHolder, modifier: Modifier, onOpen: () -> Unit) {
    val tint = rememberArt(album.id, 560)?.tint ?: placeholderTint(album.id) // same decode as Now Playing
    val pos = rememberPosition(player, track.id)
    val fraction = if (track.durationMs > 0) (pos.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .navigationBarsPadding()
            .padding(8.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(album.id, px = 112, corner = 5.dp, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = GSoft))
            }
            IconSpot(if (player.isPlaying) GlowIcons.Pause else GlowIcons.Play, "Play or pause") { player.togglePlay() }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(2.dp).background(Color.White))
    }
}

@Composable
private fun IconSpot(
    icon: ImageVector,
    description: String,
    tint: Color = Color.White,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
    }
}
