@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // combinedClickable (long-press)

package com.simonpena.mako

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
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
internal val GDeepest = Color(0xFF070D0E)
private val GSheet = Color(0xFF161D1E)

private fun display(size: Int) = TextStyle(
    fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02 * size).sp, color = Color.White,
)
private val body = TextStyle(fontSize = 15.sp, color = Color.White)

/** What changes when Mako is the phone's home screen (roadmap A6). */
private class HomeActions(val isHome: Boolean, val presses: Int, val onAndroidSettings: () -> Unit, val onOtherApps: () -> Unit)

private sealed interface Screen {
    data object Library : Screen
    data class AlbumDetail(val albumId: Long) : Screen
    data object NowPlaying : Screen
    data object About : Screen
    data object Hidden : Screen
    data object Search : Screen
    data object Licences : Screen
    /** An artist's or a genre's albums: a level below the tab it was opened from. */
    class Shelf(val title: String, val matches: (Album) -> Boolean) : Screen {
        val grid = LazyGridState() // kept while an album is open on top
    }
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
    isHome: Boolean = false,
    homePresses: Int = 0,
    onAndroidSettings: () -> Unit = {},
    onOtherApps: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(GBg)) {
        when {
            !hasPermission -> Welcome(onGrant)
            library == null -> Message(stringResource(R.string.building_library))
            library.isEmpty() -> Empty(onRescan)
            else -> Main(library, player, hidden, status, onRescan, HomeActions(isHome, homePresses, onAndroidSettings, onOtherApps))
        }
    }
}

@Composable
private fun Main(library: List<Album>, player: PlayerHolder, hidden: Hidden, status: String?, onRescan: () -> Unit, home: HomeActions) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Library) }
    // As the home screen there is nowhere to go back to from the library.
    BackHandler(enabled = home.isHome && stack.size == 1) {}
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    // Home pressed while Mako is open: back to the library.
    LaunchedEffect(home.presses) { if (home.presses > 0) stack.removeRange(1, stack.size) }
    val albumOfTrack = remember(library) { library.flatMap { a -> a.tracks.map { it.id to a } }.toMap() }
    val currentAlbum = player.currentTrackId?.let { albumOfTrack[it] }
    val currentTrack = currentAlbum?.tracks?.firstOrNull { it.id == player.currentTrackId }
    // What the library shows; the player above still finds hidden tracks it is playing.
    val visible = remember(library, hidden.albums, hidden.tracks) { hidden.filter(library) }
    val libraryState = remember { LibraryState() }
    val context = LocalContext.current
    val look = remember { Look(context) }
    // "Same people" needs the liner notes on the phone.
    val radioModes = remember { RadioMode.entries.filter { it != RadioMode.People || LinerNotes.file(context).exists() } }

    when (val screen = stack.last()) {
        Screen.Library -> LibraryScreen(
            visible, libraryState, look, hasMini = currentTrack != null, status = status,
            onRescan = onRescan, onAbout = { stack.add(Screen.About) }, onHidden = { stack.add(Screen.Hidden) },
            onSearch = { stack.add(Screen.Search) }, onHide = hidden::hide, home = home,
            onShelf = { title, matches -> stack.add(Screen.Shelf(title, matches)) },
        ) { stack.add(Screen.AlbumDetail(it)) }
        is Screen.Shelf -> {
            val albums = visible.filter(screen.matches)
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                LevelHeader(screen.title, albumCount(albums.size)) { stack.removeAt(stack.lastIndex) }
                AlbumGrid(albums, screen.grid, if (currentTrack != null) 96.dp else 24.dp, hidden::hide) { stack.add(Screen.AlbumDetail(it)) }
            }
        }
        Screen.Search -> SearchScreen(
            visible, onBack = { stack.removeAt(stack.lastIndex) },
            onAlbum = { stack.add(Screen.AlbumDetail(it)) },
            onPlay = { album, i -> player.play(album.tracks, i) },
        )
        Screen.Hidden -> HiddenScreen(hidden) { stack.removeAt(stack.lastIndex) }
        Screen.About -> AboutScreen(library, onLicences = { stack.add(Screen.Licences) }) { stack.removeAt(stack.lastIndex) }
        Screen.Licences -> LicencesScreen { stack.removeAt(stack.lastIndex) }
        is Screen.AlbumDetail -> {
            val album = visible.firstOrNull { it.id == screen.albumId }
            if (album == null) stack.removeAt(stack.lastIndex) else AlbumScreen(
                album, currentTrack, hasMini = currentTrack != null,
                onBack = { stack.removeAt(stack.lastIndex) },
                onPlay = { player.play(album.tracks, it) },
                onHide = hidden::hide,
                radioModes = radioModes,
                onRadio = { track, mode -> player.startRadio(track, mode, context.getString(mode.label)) },
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
        Text(stringResource(R.string.welcome_title), style = display(34))
        Text(
            stringResource(R.string.welcome_body),
            style = body.copy(color = GSoft, lineHeight = 22.sp),
        )
        PillButton(stringResource(R.string.welcome_find), Modifier.fillMaxWidth(), onGrant)
    }
}

@Composable
private fun Empty(onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(GlowIcons.Music, null, tint = GHi, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.empty_title), style = display(28))
        Text(
            stringResource(R.string.empty_body),
            style = body.copy(color = GSoft, lineHeight = 22.sp),
        )
        Text(
            "adb push ./Music /sdcard/Music",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFCFE9E4)),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = .07f)).padding(12.dp),
        )
        PillButton(stringResource(R.string.empty_scan_again), onClick = onRescan)
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

private enum class Tab(val label: Int) { Albums(R.string.tab_albums), Artists(R.string.tab_artists), Genres(R.string.tab_genres), Years(R.string.tab_years) }

/** Narrows the album grid to one artist or genre; shown as a removable chip. */
/** Library state that outlives visits to an album: tab, genre family and scroll positions. */
private class LibraryState {
    var tab by mutableStateOf(Tab.Albums)
    var family by mutableStateOf<String?>(null) // the genre family open on the Genres tab
    var visit by mutableIntStateOf(Random.nextInt(1000)) // each arrival on a tab shows other covers on its tiles
    val genreGrid = LazyGridState()
    val artistGrid = LazyGridState()
    val grid = LazyGridState()
    val lists = Tab.entries.associateWith { LazyListState() } // each tab keeps its own scroll
}

@Composable
private fun LibraryScreen(
    library: List<Album>,
    state: LibraryState,
    look: Look,
    hasMini: Boolean,
    status: String?,
    onRescan: () -> Unit,
    onAbout: () -> Unit,
    onHidden: () -> Unit,
    onSearch: () -> Unit,
    onHide: (Album) -> Unit,
    home: HomeActions,
    onShelf: (title: String, matches: (Album) -> Boolean) -> Unit,
    onAlbum: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val bottom = if (hasMini) 96.dp else 24.dp
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library), style = display(28), modifier = Modifier.weight(1f))
            IconSpot(GlowIcons.Search, stringResource(R.string.search), onClick = onSearch)
            Box {
                IconSpot(GlowIcons.More, stringResource(R.string.more)) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = GSheet) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_rescan), style = body) }, onClick = { menu = false; onRescan() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_hidden), style = body) }, onClick = { menu = false; onHidden() })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (look.tiles) R.string.menu_show_lists else R.string.menu_show_tiles), style = body) },
                        onClick = { menu = false; look.tiles = !look.tiles },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_about), style = body) }, onClick = { menu = false; onAbout() })
                    if (home.isHome) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_android_settings), style = body) }, onClick = { menu = false; home.onAndroidSettings() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_other_apps), style = body) }, onClick = { menu = false; home.onOtherApps() })
                    }
                }
            }
        }
        Text(
            status.orEmpty(), style = TextStyle(fontSize = 12.sp, color = GHi),
            modifier = Modifier.padding(start = 16.dp).height(16.dp),
        )
        Row(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 10.dp)) {
            Tab.entries.forEach { tab ->
                val on = state.tab == tab
                Column(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                        if (tab != state.tab) state.visit++
                        state.tab = tab
                    }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(tab.label), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else GSoft))
                    Box(Modifier.padding(top = 4.dp).size(18.dp, 2.dp).background(if (on) GHi else Color.Transparent))
                }
            }
        }
        when (state.tab) {
            Tab.Albums -> AlbumGrid(library, state.grid, bottom, onHide, onAlbum)
            Tab.Artists -> {
                val artists = remember(library) { library.groupBy { it.artist }.toList() } // already sorted by artist
                val scrub = remember(artists) { letterMarks(artists.map { initial(it.first) }) }
                val entries = artists.map { (artist, albums) ->
                    CoverEntry(artist, artist, "${albumCount(albums.size)} · ${songCount(albums.sumOf { it.tracks.size })}", coverCandidates(albums, state.visit, artist), round = true) {
                        onShelf(artist) { it.artist == artist }
                    }
                }
                val list = state.lists.getValue(state.tab)
                val grid = state.artistGrid
                if (look.tiles) {
                    Lettered(scrub, { grid.firstVisibleItemIndex }, { grid.layoutInfo.visibleItemsInfo.size }, grid::scrollToItem, bottom) { end ->
                        Covers(entries, tiles = true, list, grid, bottom, end)
                    }
                } else {
                    Lettered(scrub, { list.firstVisibleItemIndex }, { list.layoutInfo.visibleItemsInfo.size }, list::scrollToItem, bottom) { end ->
                        Covers(entries, tiles = false, list, grid, bottom, end)
                    }
                }
            }
            Tab.Genres -> {
                GenresList(library, state, look.tiles, bottom) { label, keys -> onShelf(label) { a -> genreTags(a.genre).map(::genreKey).let { if (keys.isEmpty()) it.isEmpty() else it.any(keys::contains) } } }
            }
            Tab.Years -> YearsList(library, state.lists.getValue(Tab.Years), bottom, onAlbum)
        }
    }
}

/**
 * Genres, with those that share a word gathered into a family (Indie:
 * Indie, Indie Pop, Indie Rock). A small family opens straight to its
 * albums, a big one to its genres; each entry says which. As tiles, each
 * shows a cover from its genre, another one on each visit to the tab.
 * [onAlbums] shows the albums of the genres with these keys (none: no genre).
 */
@Composable
private fun GenresList(library: List<Album>, state: LibraryState, tiles: Boolean, bottom: Dp, onAlbums: (label: String, keys: Set<String>) -> Unit) {
    val shelves = remember(library) { genreShelves(genres(library)) }
    val untagged = remember(library) { library.filter { genreTags(it.genre).isEmpty() } }
    val family = shelves.firstOrNull { it.isFamily && it.name == state.family }
    BackHandler(enabled = family != null) { state.family = null }
    val other = stringResource(R.string.other_genres)
    val label = { shelf: GenreShelf -> if (shelf.isOther) other else shelf.name }
    val keys = { shelf: GenreShelf -> shelf.genres.map { it.key }.toSet() }
    val visit = state.visit
    val entries = if (family == null) {
        shelves.map { shelf ->
            val direct = opensAlbums(shelf)
            val detail = if (direct) albumCount(shelf.albums.size) else pluralStringResource(R.plurals.genre_count, shelf.genres.size, shelf.genres.size)
            CoverEntry(shelf.name, label(shelf), detail, coverCandidates(shelf, visit)) {
                if (direct) onAlbums(label(shelf), keys(shelf)) else state.family = shelf.name
            }
        } + if (untagged.isEmpty()) emptyList() else {
            val noGenre = stringResource(R.string.no_genre)
            listOf(CoverEntry("\u0000none", noGenre, albumCount(untagged.size), coverCandidates(untagged, visit, noGenre)) { onAlbums(noGenre, emptySet()) })
        }
    } else {
        listOf(CoverEntry("\u0000all", stringResource(R.string.all_albums), albumCount(family.albums.size), coverCandidates(family, visit), accent = true) {
            onAlbums(label(family), keys(family))
        }) + family.genres.map { genre ->
            CoverEntry(genre.key, genre.name, albumCount(genre.albums.size), coverCandidates(genre.albums, visit, genre.name)) {
                onAlbums(genre.name, setOf(genre.key))
            }
        }
    }
    Column {
        if (family != null) LevelHeader(label(family), pluralStringResource(R.plurals.genre_count, family.genres.size, family.genres.size)) { state.family = null }
        val list = if (family == null) state.lists.getValue(Tab.Genres) else remember(family.name) { LazyListState() }
        val grid = if (family == null) state.genreGrid else remember(family.name) { LazyGridState() }
        Covers(entries, tiles, list, grid, bottom)
    }
}

/** One genre or artist, as a tile or a row. [candidates]: the albums whose covers it may show, in order. */
private class CoverEntry(
    val key: String,
    val title: String,
    val detail: String,
    val candidates: List<Album>,
    val round: Boolean = false,
    val accent: Boolean = false,
    val onClick: () -> Unit,
)

/** [entries] as a grid of tiles or as a list of rows (Look, ⋮ menu). [end] keeps clear of a scrubber. */
@Composable
private fun Covers(entries: List<CoverEntry>, tiles: Boolean, list: LazyListState, grid: LazyGridState, bottom: Dp, end: Dp = 0.dp) {
    if (tiles) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = maxOf(16.dp, end), bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding().testTag("tiles"),
        ) {
            items(entries, key = { it.key }) { CoverTile(it.title, it.detail, it.candidates, it.accent, { grid.isScrollInProgress }, it.onClick) }
        }
    } else {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottom), modifier = Modifier.navigationBarsPadding().padding(end = end).testTag("rows")) {
            items(entries, key = { it.key }) {
                ListRow(it.candidates.first().id, round = it.round, title = it.title, detail = it.detail, onClick = it.onClick)
            }
        }
    }
}

/** Decode size of a tile's cover: about 100 dp on screen. */
private const val TILE_COVER_PX = 224

/**
 * A tile for a genre or an artist: its name, what it opens, and one of its
 * covers tilted into the corner, on a dark tone of that cover's colour.
 * Tries [candidates] in order and shows the first with artwork.
 */
@Composable
private fun CoverTile(title: String, detail: String, candidates: List<Album>, accent: Boolean, scrolling: () -> Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val initial = remember(candidates) { coverToShow(candidates, ::knownWithoutArt)?.let { cachedArt(it.id, TILE_COVER_PX, tint = true) } }
    // On a new visit the state keeps the last cover until this one is found, so set it either way.
    val art by produceState(initial, candidates) {
        if (initial == null) snapshotFlow(scrolling).first { !it } // not mid-fling: decoding then made Artists stutter
        value = initial ?: candidates.take(8).firstNotNullOfOrNull { loadArt(context, it.id, TILE_COVER_PX, tint = true) }
    }
    val tint = art?.tint ?: candidates.firstOrNull()?.let { placeholderTint(it.id) } ?: GSheet
    // Kept cheap to draw: no shadows, which cost the Nexus 4 most of its frames flinging Artists (69% janky).
    // The cover sits below two lines of name, so the text needs no shadow to stay readable.
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(tint, lerp(tint, GDeepest, .55f))))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 16.dp).fillMaxWidth(.5f).aspectRatio(1f)
                .rotate(12f).background(lerp(tint, Color.Black, .3f)),
        ) {
            art?.let { Image(it.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Icon(GlowIcons.Music, null, tint = Color.White.copy(alpha = .35f), modifier = Modifier.align(Alignment.Center).fillMaxWidth(.3f))
        }
        Column(Modifier.padding(10.dp)) {
            Text(title, style = display(17).copy(color = if (accent) GHi else Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = TextStyle(fontSize = 11.sp, color = Color.White.copy(alpha = .78f)), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The top of a level below a tab (an artist, a genre family): back, its name and a count. */
@Composable
private fun LevelHeader(title: String, detail: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconSpot(GlowIcons.Back, stringResource(R.string.back), onClick = onBack)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = display(24), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = TextStyle(fontSize = 12.sp, color = GSoft))
        }
    }
}

/**
 * Albums by year with a scrubber on the right: drag or tap it to jump.
 * While scrolling fast a label shows the decade; as it slows, the year.
 */
@Composable
private fun YearsList(library: List<Album>, list: LazyListState, bottom: Dp, onAlbum: (Long) -> Unit) {
    val years = remember(library) { library.groupBy { it.year }.toList().sortedByDescending { (y, _) -> if (y > 0) y else -1 } }
    val rows = remember(years) { YearRows(years.map { (year, albums) -> year to albums.size }) }
    val zoom = remember { ScrollZoom() }
    var coarse by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Speed in rows per second, from each change of the top row: nothing runs
    // while the list is still. No change for a moment counts as slowing down.
    LaunchedEffect(list) {
        var lastRow = list.firstVisibleItemIndex
        var lastTime = System.nanoTime()
        var speed = 0f
        snapshotFlow { list.firstVisibleItemIndex }.drop(1).collectLatest { row -> // the first is where it starts, not a movement
            val now = System.nanoTime()
            val seconds = (now - lastTime) / 1e9f
            if (seconds > 0) speed = .5f * speed + .5f * ((row - lastRow) / seconds) // smoothed
            lastRow = row
            lastTime = now
            zoom.onSpeed(speed)
            coarse = zoom.coarse
            showLabel = true
            delay(250) // no new row since: slowing down, narrow to the year
            speed = 0f
            zoom.onSpeed(0f)
            coarse = false
            delay(700)
            if (!dragging) showLabel = false
        }
    }
    val res = LocalContext.current.resources
    val label by remember(rows) { derivedStateOf { yearLabel(res, rows.yearAt(list.firstVisibleItemIndex), coarse) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottom), modifier = Modifier.navigationBarsPadding().padding(end = 28.dp)) {
            years.forEach { (year, albums) ->
                item(key = "y$year") {
                    Text(
                        if (year > 0) "$year" else stringResource(R.string.unknown_year), style = display(20),
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(albums, key = { it.id }) { album ->
                    ListRow(album.id, round = false, title = album.title, detail = album.artist) { onAlbum(album.id) }
                }
            }
        }
        Scrubber(
            rows.scrub, { list.firstVisibleItemIndex },
            tag = "yearScrubber",
            onDrag = { fraction ->
                dragging = true
                showLabel = true
                scope.launch { list.scrollToItem(rows.rowAt(fraction)) }
            },
            onRelease = { dragging = false },
            modifier = Modifier.align(Alignment.TopEnd).padding(bottom = bottom).navigationBarsPadding(),
        )
        if (showLabel || dragging) {
            Text(
                label, style = display(22),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp)).background(GSheet).padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("yearLabel"),
            )
        }
    }
}

/**
 * A long list with a letter scrubber on the right, once it's more than three
 * screens long: drag or tap it to jump; the letter shows while dragging.
 * [content] gets the end padding that keeps it clear of the strip.
 */
@Composable
private fun Lettered(
    scrub: Scrub,
    firstVisible: () -> Int,
    visible: () -> Int,
    scrollTo: suspend (row: Int) -> Unit,
    bottom: Dp,
    content: @Composable (end: Dp) -> Unit,
) {
    val show by remember(scrub) { derivedStateOf { needsScrubber(scrub.size, visible()) } }
    var dragRow by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        content(if (show) 28.dp else 0.dp)
        if (show) {
            Scrubber(
                scrub, firstVisible,
                onDrag = { fraction ->
                    val row = scrub.rowAt(fraction)
                    dragRow = row
                    scope.launch { scrollTo(row) }
                },
                onRelease = { dragRow = null },
                modifier = Modifier.align(Alignment.TopEnd).padding(bottom = bottom).navigationBarsPadding(),
            )
        }
        dragRow?.let { row ->
            Text(
                scrub.labelAt(row), style = display(22),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp)).background(GSheet).padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("scrubLabel"),
            )
        }
    }
}

/** The strip on the right of a long list: section marks, a thumb, and touch to jump. */
@Composable
private fun Scrubber(
    scrub: Scrub,
    firstVisible: () -> Int,
    onDrag: (fraction: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier,
    tag: String = "scrubber",
) {
    BoxWithConstraints(
        modifier.fillMaxHeight().width(28.dp).testTag(tag).pointerInput(scrub) {
            awaitEachGesture {
                val down = awaitFirstDown()
                onDrag(down.position.y / size.height)
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        onDrag(change.position.y / size.height)
                        change.consume()
                    }
                } while (event.changes.any { it.pressed })
                onRelease()
            }
        },
    ) {
        val height = constraints.maxHeight.toFloat()
        val minGap = with(LocalDensity.current) { 16.dp.toPx() }
        scrub.visibleMarks(height, minGap).forEach { mark ->
            Text(
                mark.label, style = TextStyle(fontSize = 10.sp, color = GSoft),
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (mark.fraction * height).toInt()) },
            )
        }
        val thumb by remember(scrub) { derivedStateOf { scrub.fractionOf(firstVisible()) } }
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, (thumb * height).toInt()) }
                .size(3.dp, 22.dp).clip(RoundedCornerShape(2.dp)).background(GHi),
        )
    }
}

@Composable
private fun albumCount(n: Int) = pluralStringResource(R.plurals.albums, n, n)

@Composable
private fun songCount(n: Int) = pluralStringResource(R.plurals.song_count, n, n)

@Composable
private fun AlbumGrid(albums: List<Album>, state: LazyGridState, bottom: Dp, onHide: (Album) -> Unit, onAlbum: (Long) -> Unit) {
    // Albums are sorted by artist, so the letters are the artists' (as on the Artists tab).
    val scrub = remember(albums) { letterMarks(albums.map { initial(it.artist) }) }
    Lettered(scrub, { state.firstVisibleItemIndex }, { state.layoutInfo.visibleItemsInfo.size }, state::scrollToItem, bottom) { end ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = maxOf(16.dp, end), bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(albums, key = { it.id }) { album ->
                var options by remember { mutableStateOf(false) }
                Column(Modifier.combinedClickable(onLongClick = { options = true }) { onAlbum(album.id) }) {
                    AlbumArt(album.id, px = 336, corner = 8.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        album.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, color = GSoft))
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }, containerColor = GSheet) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.hide_album), style = body) }, onClick = { options = false; onHide(album) })
                    }
                }
            }
        }
    }
}

/** A row with small artwork: artists (round), genres, albums by year, search results. */
@Composable
private fun ListRow(albumId: Long, round: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(albumId, px = 112, corner = if (round) 24.dp else 4.dp, modifier = Modifier.size(48.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = TextStyle(fontSize = 13.sp, color = GSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Search albums, artists and songs; accents and case don't matter ("camaron" finds Camarón). */
@Composable
private fun SearchScreen(library: List<Album>, onBack: () -> Unit, onAlbum: (Long) -> Unit, onPlay: (Album, Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val index = remember(library) { SearchIndex(library) }
    val (albums, songs) = index.search(query)
    val q = query.trim()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconSpot(GlowIcons.Back, stringResource(R.string.back), onClick = onBack)
            Box(Modifier.weight(1f).padding(start = 4.dp)) {
                if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = body.copy(color = GSoft))
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = body, cursorBrush = SolidColor(GHi),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) IconSpot(GlowIcons.Close, stringResource(R.string.clear)) { query = "" }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GFaint))
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.navigationBarsPadding().imePadding()) {
            if (albums.isNotEmpty()) item { HiddenHeading(stringResource(R.string.tab_albums)) }
            items(albums, key = { "a${it.id}" }) { a -> ListRow(a.id, round = false, title = a.title, detail = a.artist) { onAlbum(a.id) } }
            if (songs.isNotEmpty()) item { HiddenHeading(stringResource(R.string.songs)) }
            items(songs, key = { (a, i) -> "s${a.tracks[i].id}" }) { (a, i) ->
                ListRow(a.id, round = false, title = a.tracks[i].title, detail = "${a.tracks[i].artist} · ${a.title}") { onPlay(a, i) }
            }
            if (q.isNotEmpty() && albums.isEmpty() && songs.isEmpty()) item {
                Text(stringResource(R.string.nothing_matches, query), style = body.copy(color = GSoft), modifier = Modifier.padding(20.dp))
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
    radioModes: List<RadioMode>,
    onRadio: (Track, RadioMode) -> Unit,
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
                IconSpot(GlowIcons.Back, stringResource(R.string.back), onClick = onBack)
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
                val details = listOfNotNull(album.year.takeIf { it > 0 }?.toString(), songCount(album.tracks.size), stringResource(R.string.minutes, minutes))
                Text(details.joinToString(" · "), style = TextStyle(fontSize = 12.sp, color = GSoft))
                Spacer(Modifier.height(4.dp))
                PillButton(stringResource(R.string.play)) { onPlay(0) }
            }
        }
        itemsIndexed(album.tracks, key = { _, t -> t.id }) { i, track ->
            val on = current?.id == track.id
            val color = if (on) GHi else Color.White
            val soft = if (on) GHi else GSoft
            var options by remember { mutableStateOf(false) }
            var radio by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().combinedClickable(onLongClick = { options = true; radio = false }) { onPlay(i) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DropdownMenu(expanded = options, onDismissRequest = { options = false }, containerColor = GSheet) {
                    if (!radio) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.start_radio), style = body) }, onClick = { radio = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.hide_song), style = body) }, onClick = { options = false; onHide(track) })
                    } else radioModes.forEach { mode ->
                        DropdownMenuItem(text = { Text(stringResource(mode.label), style = body) }, onClick = { options = false; onRadio(track, mode) })
                    }
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
                IconSpot(GlowIcons.Back, stringResource(R.string.back), onClick = onBack)
            }
            Text(stringResource(R.string.menu_hidden), style = display(28), modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
            Text(
                stringResource(if (albums.isEmpty() && tracks.isEmpty()) R.string.hidden_none else R.string.hidden_some),
                style = TextStyle(fontSize = 13.sp, color = GSoft), modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (albums.isNotEmpty()) item { HiddenHeading(stringResource(R.string.tab_albums)) }
        items(albums.size) { HiddenRow(albums[it]) { hidden.showAlbum(albums[it]) } }
        if (tracks.isNotEmpty()) item { HiddenHeading(stringResource(R.string.songs)) }
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
            stringResource(R.string.show), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GHi),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onShow).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AboutScreen(library: List<Album>, onLicences: () -> Unit, onBack: () -> Unit) {
    val tracks = library.sumOf { it.tracks.size }
    val hours = (library.sumOf { a -> a.tracks.sumOf { it.durationMs } } / 3_600_000).toInt()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.padding(horizontal = 8.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
            IconSpot(GlowIcons.Back, stringResource(R.string.back), onClick = onBack)
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Mako", style = display(34))
            AboutLine(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.COMMIT_DATE}")
            AboutLine(stringResource(R.string.about_device), "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            AboutLine("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            AboutLine(stringResource(R.string.library), "${albumCount(library.size)} · ${songCount(tracks)} · ${pluralStringResource(R.plurals.hours, hours, hours)}")
            Text(
                stringResource(R.string.about_summary),
                style = TextStyle(fontSize = 13.sp, color = GSoft, lineHeight = 19.sp),
            )
            Text(
                stringResource(R.string.licences_link),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GHi),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onLicences).padding(vertical = 8.dp),
            )
        }
    }
}

/** Every library the app is built on, with its licence: collected at build time, shown offline. */
@Composable
private fun LicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember {
        Libs.Builder().withContext(context).build().libraries.sortedBy { it.name.lowercase() }
    }
    val counts = remember(libraries) {
        libraries.flatMap { it.licenses.map { l -> l.name } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    }
    var open by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Box(Modifier.padding(horizontal = 8.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
                IconSpot(GlowIcons.Back, stringResource(R.string.back), onClick = onBack)
            }
            Text(stringResource(R.string.licences), style = display(28), modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
            Text(
                stringResource(
                    R.string.licences_intro,
                    pluralStringResource(R.plurals.libraries, libraries.size, libraries.size),
                    counts.joinToString { (name, n) -> context.getString(R.string.licences_count, n, name) },
                ),
                style = TextStyle(fontSize = 13.sp, color = GSoft, lineHeight = 19.sp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        items(libraries, key = { it.uniqueId }) { lib ->
            val expanded = open == lib.uniqueId
            Column(Modifier.fillMaxWidth().clickable { open = if (expanded) null else lib.uniqueId }.padding(horizontal = 20.dp, vertical = 9.dp)) {
                Text(lib.name, style = body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(lib.artifactVersion, lib.licenses.joinToString { it.name }.ifEmpty { null }).joinToString(" · "),
                    style = TextStyle(fontSize = 12.sp, color = GSoft),
                )
                if (expanded) {
                    lib.licenses.forEach { licence ->
                        Text(
                            licence.licenseContent ?: licence.url.orEmpty(),
                            style = TextStyle(fontSize = 11.sp, color = GSoft, fontFamily = FontFamily.Monospace, lineHeight = 15.sp),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
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
internal fun NowPlaying(album: Album, track: Track, player: PlayerHolder, onClose: () -> Unit) {
    val tint = rememberArt(album.id, 560)?.tint ?: placeholderTint(album.id)
    val pos = rememberPosition(player, track.id)
    var outputs by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, .62f to GDeep, 1f to GDeepest))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconSpot(GlowIcons.Down, stringResource(R.string.close), onClick = onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val radio = player.currentItem?.mediaMetadata?.extras?.getString(EXTRA_RADIO)
                    Text(
                        if (radio != null) stringResource(R.string.radio_now, radio) else stringResource(R.string.playing_from_album),
                        style = TextStyle(fontSize = 11.sp, letterSpacing = .7.sp, color = GSoft),
                    )
                    Text(album.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(44.dp))
            }
            AlbumArt(album.id, px = 560, corner = 10.dp, modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(.86f).aspectRatio(1f))
            Column {
                Text(track.title, style = display(26), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = TextStyle(fontSize = 16.sp, color = GSoft), modifier = Modifier.padding(top = 4.dp))
                player.currentItem?.mediaMetadata?.extras?.getString(EXTRA_REASON)?.let {
                    Text(it, style = TextStyle(fontSize = 13.sp, color = GHi), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Column {
                SeekBar(pos, track.durationMs) { player.seekTo(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), style = TextStyle(fontSize = 12.sp, color = GSoft))
                    Text(fmt(track.durationMs), style = TextStyle(fontSize = 12.sp, color = GSoft))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconSpot(GlowIcons.Shuffle, stringResource(R.string.shuffle), tint = if (player.shuffle) GHi else GSoft) { player.toggleShuffle() }
                IconSpot(GlowIcons.Previous, stringResource(R.string.previous), iconSize = 32.dp) { player.previous() }
                Box(
                    Modifier.size(66.dp).clip(CircleShape).background(Color.White).clickable { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (player.isPlaying) GlowIcons.Pause else GlowIcons.Play, stringResource(R.string.play_or_pause), tint = Color(0xFF0E2226), modifier = Modifier.size(32.dp))
                }
                IconSpot(GlowIcons.Next, stringResource(R.string.next), iconSize = 32.dp) { player.next() }
                Box {
                    IconSpot(GlowIcons.Repeat, stringResource(R.string.repeat), tint = if (player.repeatMode == 0) GSoft else GHi) { player.cycleRepeat() }
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
    output.isGroup -> GlowIcons.Speakers
    output.isSpeaker -> GlowIcons.Speaker
    else -> GlowIcons.Tv
}

@Composable
private fun OutputRow(output: Renderer?, onClick: () -> Unit) {
    // "Playing on %s", with the output's name in bold wherever the language puts it.
    val (before, after) = stringResource(R.string.playing_on).split("%s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val name = output?.name ?: stringResource(R.string.this_phone)
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GFaint))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(outputIcon(output), null, tint = GHi, modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    append(before)
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(name) }
                    append(after)
                },
                style = TextStyle(fontSize = 13.sp, color = GSoft),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.change), style = TextStyle(fontSize = 12.sp, color = GSoft))
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
    // A group shares its lead room's address, so compare whole entries.
    val renderers = (found.orEmpty() + listOfNotNull(current)).distinct()
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
            Text(stringResource(R.string.play_on), style = display(20), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp))
            OutputOption(GlowIcons.Phone, stringResource(R.string.this_phone), stringResource(R.string.this_phone_detail), current == null) {
                player.playOn(null)
                onClose()
            }
            val speaker = stringResource(R.string.speaker)
            val tv = stringResource(R.string.tv)
            renderers.forEach { r ->
                OutputOption(outputIcon(r), r.name, r.model.ifEmpty { if (r.isSpeaker) speaker else tv }, current == r) {
                    player.playOn(r)
                    onClose()
                }
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(GFaint))
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(GHi))
                Text(
                    stringResource(
                        when {
                            found == null -> R.string.outputs_looking
                            found!!.isEmpty() -> R.string.outputs_none
                            else -> R.string.outputs_found
                        },
                    ),
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
            IconSpot(if (player.isPlaying) GlowIcons.Pause else GlowIcons.Play, stringResource(R.string.play_or_pause)) { player.togglePlay() }
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
