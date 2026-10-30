@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // combinedClickable (long-press)

package com.simonpena.mako

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalContext
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
import com.simonpena.mako.upnp.Renderer
import com.simonpena.mako.upnp.discoverRenderers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Glow tokens, from docs/mockups/prototype.html.
private val GBg = Color(0xFF0C1112)
private val GSoft = Color.White.copy(alpha = .64f)
private val GFaint = Color.White.copy(alpha = .16f)
private val GHi = Color(0xFF9FD6CF)
private val GDeep = Color(0xFF0B1415)
private val GDeepest = Color(0xFF070D0E)
private val GSheet = Color(0xFF161D1E)

private fun display(size: Int) = TextStyle(
    fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02 * size).sp, color = Color.White,
)
private val body = TextStyle(fontSize = 15.sp, color = Color.White)

private sealed interface Screen {
    data object Library : Screen
    data class AlbumDetail(val albumId: Long) : Screen
    data object NowPlaying : Screen
    data object About : Screen
    data object Hidden : Screen
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
    hidden: Hidden,
    status: String?,
    onGrant: () -> Unit,
    onRescan: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(GBg)) {
        when {
            !hasPermission -> Welcome(onGrant)
            library == null -> Message("Building your library…")
            library.isEmpty() -> Empty(onRescan)
            else -> Main(library, player, hidden, status, onRescan)
        }
    }
}

@Composable
private fun Main(library: List<Album>, player: PlayerHolder, hidden: Hidden, status: String?, onRescan: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Library) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val albumOfTrack = remember(library) { library.flatMap { a -> a.tracks.map { it.id to a } }.toMap() }
    val currentAlbum = player.currentTrackId?.let { albumOfTrack[it] }
    val currentTrack = currentAlbum?.tracks?.firstOrNull { it.id == player.currentTrackId }
    // What the library shows; the player above still finds hidden tracks it is playing.
    val visible = remember(library, hidden.albums, hidden.tracks) { hidden.filter(library) }

    when (val screen = stack.last()) {
        Screen.Library -> LibraryScreen(
            visible, hasMini = currentTrack != null, status = status,
            onRescan = onRescan, onAbout = { stack.add(Screen.About) }, onHidden = { stack.add(Screen.Hidden) },
            onHide = hidden::hide,
        ) { stack.add(Screen.AlbumDetail(it)) }
        Screen.Hidden -> HiddenScreen(hidden) { stack.removeAt(stack.lastIndex) }
        Screen.About -> AboutScreen(library) { stack.removeAt(stack.lastIndex) }
        is Screen.AlbumDetail -> {
            val album = visible.firstOrNull { it.id == screen.albumId }
            if (album == null) stack.removeAt(stack.lastIndex) else AlbumScreen(
                album, currentTrack, hasMini = currentTrack != null,
                onBack = { stack.removeAt(stack.lastIndex) },
                onPlay = { player.play(album.tracks, it) },
                onHide = hidden::hide,
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
private fun LibraryScreen(
    library: List<Album>,
    hasMini: Boolean,
    status: String?,
    onRescan: () -> Unit,
    onAbout: () -> Unit,
    onHidden: () -> Unit,
    onHide: (Album) -> Unit,
    onAlbum: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = display(28), modifier = Modifier.weight(1f))
            Box {
                IconSpot(GlowIcons.More, "More") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = GSheet) {
                    DropdownMenuItem(text = { Text("Rescan library", style = body) }, onClick = { menu = false; onRescan() })
                    DropdownMenuItem(text = { Text("Hidden", style = body) }, onClick = { menu = false; onHidden() })
                    DropdownMenuItem(text = { Text("About", style = body) }, onClick = { menu = false; onAbout() })
                }
            }
        }
        Text(
            status.orEmpty(), style = TextStyle(fontSize = 12.sp, color = GHi),
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp).height(16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (hasMini) 96.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(library, key = { it.id }) { album ->
                var options by remember { mutableStateOf(false) }
                Column(Modifier.combinedClickable(onLongClick = { options = true }) { onAlbum(album.id) }) {
                    AlbumArt(album.id, px = 336, corner = 8.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        album.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = GSoft))
                    // Last in the column, so it opens below the album's name.
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }, containerColor = GSheet) {
                        DropdownMenuItem(text = { Text("Hide album", style = body) }, onClick = { options = false; onHide(album) })
                    }
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
    onHide: (Track) -> Unit,
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
            var options by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().combinedClickable(onLongClick = { options = true }) { onPlay(i) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DropdownMenu(expanded = options, onDismissRequest = { options = false }, containerColor = GSheet) {
                    DropdownMenuItem(text = { Text("Hide song", style = body) }, onClick = { options = false; onHide(track) })
                }
                Text("${if (track.number > 0) track.number else i + 1}", style = TextStyle(fontSize = 13.sp, color = soft), modifier = Modifier.width(28.dp))
                Text(track.title, style = body.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(fmt(track.durationMs), style = TextStyle(fontSize = 13.sp, color = soft))
            }
        }
    }
}

/** Everything hidden from the library, each with a way back. */
@Composable
private fun HiddenScreen(hidden: Hidden, onBack: () -> Unit) {
    val albums = hidden.albums.sortedBy { hiddenLabel(it).first.lowercase() }
    val tracks = hidden.tracks.sortedBy { hiddenLabel(it).first.lowercase() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Box(Modifier.padding(horizontal = 8.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
                IconSpot(GlowIcons.Back, "Back", onClick = onBack)
            }
            Text("Hidden", style = display(28), modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
            Text(
                if (albums.isEmpty() && tracks.isEmpty()) "Nothing is hidden. Long-press an album or a song to hide it."
                else "Hidden from the library. The files stay on the phone.",
                style = TextStyle(fontSize = 13.sp, color = GSoft), modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (albums.isNotEmpty()) item { HiddenHeading("Albums") }
        items(albums.size) { HiddenRow(albums[it]) { hidden.showAlbum(albums[it]) } }
        if (tracks.isNotEmpty()) item { HiddenHeading("Songs") }
        items(tracks.size) { HiddenRow(tracks[it]) { hidden.showTrack(tracks[it]) } }
    }
}

@Composable
private fun HiddenHeading(text: String) {
    Text(text.uppercase(), style = TextStyle(fontSize = 11.sp, letterSpacing = .7.sp, color = GSoft), modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
private fun HiddenRow(key: String, onShow: () -> Unit) {
    val (title, detail) = hiddenLabel(key)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = TextStyle(fontSize = 12.sp, color = GSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Show", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GHi),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onShow).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AboutScreen(library: List<Album>, onBack: () -> Unit) {
    val tracks = library.sumOf { it.tracks.size }
    val hours = library.sumOf { a -> a.tracks.sumOf { it.durationMs } } / 3_600_000
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.padding(horizontal = 8.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
            IconSpot(GlowIcons.Back, "Back", onClick = onBack)
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Mako", style = display(34))
            AboutLine("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.COMMIT_DATE}")
            AboutLine("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            AboutLine("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            AboutLine("Library", "${library.size} albums · $tracks songs · $hours hours")
            Text(
                "An offline music player. Plays from the Music folder, on this phone or on speakers and TVs on the same Wi-Fi.",
                style = TextStyle(fontSize = 13.sp, color = GSoft, lineHeight = 19.sp),
            )
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = TextStyle(fontSize = 11.sp, letterSpacing = .7.sp, color = GSoft))
        Text(value, style = body, modifier = Modifier.padding(top = 2.dp))
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
    var outputs by remember { mutableStateOf(false) }
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
            OutputRow(player.output) { outputs = true }
        }
        if (outputs) OutputSheet(player) { outputs = false }
    }
}

private fun outputIcon(output: Renderer?) = when {
    output == null -> GlowIcons.Phone
    output.isSpeaker -> GlowIcons.Speaker
    else -> GlowIcons.Tv
}

@Composable
private fun OutputRow(output: Renderer?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GFaint))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(outputIcon(output), null, tint = GHi, modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    append("Playing on ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                        append(output?.name ?: "This phone")
                    }
                },
                style = TextStyle(fontSize = 13.sp, color = GSoft),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("Change", style = TextStyle(fontSize = 12.sp, color = GSoft))
        }
    }
}

/** Pick where to play: this phone, a Sonos room or the TV, found on the Wi-Fi. */
@Composable
private fun OutputSheet(player: PlayerHolder, onClose: () -> Unit) {
    val context = LocalContext.current
    var found by remember { mutableStateOf<List<Renderer>?>(null) }
    LaunchedEffect(Unit) {
        found = withContext(Dispatchers.IO) { runCatching { discoverRenderers(context) }.getOrDefault(emptyList()) }
    }
    BackHandler(onBack = onClose)
    val current = player.output
    // Keep the current output listed even if this search missed it.
    val renderers = (found.orEmpty() + listOfNotNull(current)).distinctBy { it.avTransport }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).pointerInput(Unit) { detectTapGestures { onClose() } }) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(GSheet)
                .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close it
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(GFaint))
            Text("Play on", style = display(20), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp))
            OutputOption(GlowIcons.Phone, "This phone", "Headphones or Bluetooth", current == null) {
                player.playOn(null)
                onClose()
            }
            renderers.forEach { r ->
                OutputOption(outputIcon(r), r.name, r.model.ifEmpty { if (r.isSpeaker) "Speaker" else "TV" }, current?.avTransport == r.avTransport) {
                    player.playOn(r)
                    onClose()
                }
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(GFaint))
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(GHi))
                Text(
                    when {
                        found == null -> "Looking for speakers and TVs…"
                        found!!.isEmpty() -> "No speakers or TVs found on this Wi-Fi"
                        else -> "Found on this Wi-Fi"
                    },
                    style = TextStyle(fontSize = 12.sp, color = GSoft),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OutputOption(icon: ImageVector, name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) GHi else Color.White.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) Color(0xFF0B1A1A) else Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(name, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = TextStyle(fontSize = 13.sp, color = GSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(GlowIcons.Check, null, tint = GHi, modifier = Modifier.size(22.dp))
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
