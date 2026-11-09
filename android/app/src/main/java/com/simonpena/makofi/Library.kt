package com.simonpena.makofi

import android.content.ContentUris
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val number: Int,
    val uri: Uri,
    /** Inside the Music folder ("Altan/The Blue Idol/01 Daily Growing.mp3"): how the liner notes know it. */
    val path: String = "",
    /** The album's artist as tagged ("Various Artists" on a compilation); empty if untagged. */
    val albumArtist: String = "",
    /** 1, 2… on a multi-disc album; 0 if untagged. */
    val disc: Int = 0,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val tracks: List<Track>,
    val genre: String = "",
) {
    /** The songs aren't all by the same credit (a compilation, guests): each song says whose it is. */
    val mixedArtists: Boolean get() = tracks.any { it.artist != tracks[0].artist }
}

/** Artists sort ignoring a leading "The", so The Strokes is under S, and ignoring accents. */
fun sortKey(artist: String) = fold(artist.removePrefix("The "))

private fun clean(value: String?, fallback: String) =
    value?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }?.let(::repairMojibake) ?: fallback

private val WINDOWS_1252 = Charset.forName("windows-1252")

/**
 * Undoes Android's occasional misreading of correct UTF-8 tags as
 * Windows-1252 ("Thatâ€™s" for "That’s"; 6% of tags with accents or curly
 * quotes on the Pixel 5). Its encoding guesser decides per file, so one
 * album can come out half garbled and split in two. The reversal is exact:
 * text that isn't garbled doesn't survive the round trip and is kept as is.
 */
fun repairMojibake(s: String): String {
    if (s.none { it == 'Ã' || it == 'Â' || it == 'â' }) return s
    return runCatching {
        val bytes = WINDOWS_1252.newEncoder().encode(CharBuffer.wrap(s))
        Charsets.UTF_8.newDecoder().decode(bytes).toString()
    }.getOrDefault(s)
}

/**
 * Only audio under the shared Music folder is part of the library; test
 * tones, recordings and ringtones elsewhere on the phone are ignored.
 */
@Suppress("DEPRECATION") // DATA and the public directory path are the only way on API 27–28
private fun musicFolderSelection(): Pair<String, Array<String>> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("${Environment.DIRECTORY_MUSIC}/%")
    } else {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
        "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("$root/%")
    }

@Suppress("DEPRECATION") // DATA: the file path on API 27–28
private fun pathColumns(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DISPLAY_NAME)
    else arrayOf(MediaStore.Audio.Media.DATA)

/** Reads the current row's path inside the Music folder ([musicPath]). */
@Suppress("DEPRECATION")
private fun pathReader(c: android.database.Cursor): () -> String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rel = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        ({ musicPath(c.getString(rel).orEmpty(), c.getString(name).orEmpty()) })
    } else {
        val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
        ({ musicPathFromFile(c.getString(data).orEmpty(), root) })
    }

/** Reads the audio files in the Music folder from Android's MediaStore, grouped into albums. */
suspend fun loadLibrary(context: Context): List<Album> = withContext(Dispatchers.IO) {
    val m = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        ALBUM_ARTIST,
        *pathColumns(),
    )
    val tracks = mutableListOf<Track>()
    val years = mutableMapOf<Long, Int>()
    val (folder, args) = musicFolderSelection()
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND $folder"
    context.contentResolver.query(m, projection, selection, args, null)?.use { c ->
        val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val albumArtist = c.getColumnIndexOrThrow(ALBUM_ARTIST)
        val path = pathReader(c)
        while (c.moveToNext()) {
            val trackId = c.getLong(id)
            val aId = c.getLong(albumId)
            tracks += Track(
                id = trackId,
                title = clean(c.getString(title), "Unknown track"),
                artist = clean(c.getString(artist), "Unknown artist"),
                album = clean(c.getString(album), "Unknown album"),
                albumId = aId,
                durationMs = c.getLong(duration),
                number = c.getInt(track) % 1000, // stored as disc * 1000 + track
                disc = c.getInt(track) / 1000,
                uri = ContentUris.withAppendedId(m, trackId),
                path = path(),
                albumArtist = clean(c.getString(albumArtist), ""),
            )
            if (!c.isNull(year)) years[aId] = c.getInt(year)
        }
    }
    val genres = trackGenres(context)
    groupAlbums(tracks, years, context.getString(R.string.various_artists)).map { album ->
        // The genre most of the album's songs share.
        album.copy(genre = album.tracks.mapNotNull { genres[it.id] }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty())
    }
}

/** Track id → genre. Android 8 keeps genres in their own table, one query per genre. */
private fun trackGenres(context: Context): Map<Long, String> {
    val result = HashMap<Long, String>()
    val resolver = context.contentResolver
    resolver.query(
        MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null,
    )?.use { g ->
        while (g.moveToNext()) {
            val name = g.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            resolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", g.getLong(0)),
                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null,
            )?.use { m -> while (m.moveToNext()) result[m.getLong(0)] = name }
        }
    }
    return result
}

/**
 * Albums from tracks: Android's album ids first (they keep compilations
 * together), then groups with the same title and album artist merged. Android
 * can give one album two ids when it reads the name differently for some
 * tracks (see [repairMojibake]). The id most tracks share stays, for the cover.
 */
fun groupAlbums(tracks: List<Track>, years: Map<Long, Int>, various: String = VARIOUS_ARTISTS): List<Album> {
    fun mainArtist(list: List<Track>) = list.groupingBy { it.artist }.eachCount().maxBy { it.value }.key
    // The album artist tag when there is one (a compilation's most frequent song artist is a guest), else the main artist.
    fun albumArtist(list: List<Track>) = list.map { it.albumArtist }.filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        ?.let { if (fold(it) == fold(VARIOUS_ARTISTS)) various else it } ?: mainArtist(list)
    return tracks.groupBy { it.albumId }.values
        .groupBy { list -> fold(list.first().album) + "\u0000" + fold(albumArtist(list)) }.values
        .map { groups ->
            val main = groups.maxBy { it.size }
            val id = main.first().albumId
            val sorted = groups.flatten().sortedWith(compareBy({ it.disc }, { it.number }, { it.title }))
            val year = years[id] ?: groups.firstNotNullOfOrNull { years[it.first().albumId] } ?: 0
            Album(id, main.first().album, albumArtist(main), year, sorted)
        }
        .sortedWith(compareBy({ sortKey(it.artist) }, { it.year }, { it.title }))
}

/** How MusicBrainz and beets tag a compilation's album artist. */
const val VARIOUS_ARTISTS = "Various Artists"

/** MediaStore's album artist column: public from API 30 (AudioColumns.ALBUM_ARTIST), present on the Nexus 4's API 27 too. */
private const val ALBUM_ARTIST = "album_artist"

private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "mpc")

/** What a rescan changed, for the message shown afterwards; [complete] is false if the scanner stopped answering. */
data class ScanResult(val added: Int, val removed: Int, val complete: Boolean = true)

/** The status line after a rescan: "Library updated: 3 files added, 1 file removed". */
fun rescanSummary(res: Resources, result: ScanResult): String = when {
    !result.complete -> res.getString(R.string.rescan_incomplete)
    result.added == 0 && result.removed == 0 -> res.getString(R.string.rescan_up_to_date)
    else -> res.getString(
        R.string.rescan_updated,
        listOfNotNull(
            result.added.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.files_added, it, it) },
            result.removed.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.files_removed, it, it) },
        ).joinToString(", "),
    )
}

/**
 * Progress of a batch of media-scanner callbacks: [finished] when every file
 * has answered, [stalled] when none has for [stallMs]. A long scan that
 * keeps going is waited for to the end (2,660 files take minutes on the
 * Nexus 4); only silence ends the wait.
 */
class ScanProgress(val total: Int, private val stallMs: Long = 30_000, private val now: () -> Long = System::currentTimeMillis) {
    private val done = AtomicInteger(0)
    @Volatile private var last = now()

    val scanned: Int get() = done.get()
    val finished: Boolean get() = scanned >= total
    val stalled: Boolean get() = !finished && now() - last > stallMs

    fun onScanned() {
        done.incrementAndGet()
        last = now()
    }
}

/**
 * Brings Android's media index up to date with the Music folder: files
 * pushed over ADB or USB are not always indexed, and deleted files can
 * linger. Only the differences are scanned; scanning a path that no longer
 * exists removes it from the index.
 */
@Suppress("DEPRECATION") // DATA: the file path, still readable on every API level
suspend fun rescanMusicFolder(context: Context, onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }): ScanResult = withContext(Dispatchers.IO) {
    val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    fun indexed() = mutableSetOf<String>().also { paths ->
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media.DATA} LIKE ?", arrayOf("${root.absolutePath}/%"), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(paths::add) }
    }
    suspend fun scan(paths: Array<String>, progress: ScanProgress, report: Boolean) {
        MediaScannerConnection.scanFile(context, paths, null) { _, _ -> progress.onScanned() }
        while (!progress.finished && !progress.stalled) { // never hang the UI on a lost callback
            if (report) onProgress(progress.scanned, paths.size)
            delay(500)
        }
    }
    val before = indexed()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Since Android 11 listing a shared folder shows only what MediaStore
        // already knows, so new files can't be found by walking it: ask the
        // scanner to walk the folder itself. It answers once, at the end.
        val progress = ScanProgress(1, stallMs = 10 * 60_000)
        scan(arrayOf(root.absolutePath), progress, report = false)
        val after = indexed()
        return@withContext ScanResult((after - before).size, (before - after).size, complete = progress.finished)
    }
    val onDisk = root.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in AUDIO }
        .map(File::getAbsolutePath)
        .toSet()
    val added = onDisk - before
    val removed = before - onDisk
    val paths = (added + removed).toTypedArray()
    val progress = ScanProgress(paths.size)
    if (paths.isNotEmpty()) scan(paths, progress, report = true)
    ScanResult(added.size, removed.size, complete = progress.finished)
}
