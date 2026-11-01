package com.simonpena.mako

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

/**
 * Lyrics for the library, from lyrics.db (docs/lyrics.md): fetched on the
 * laptop from LRCLIB by tools/lyrics, copied by tools/sync/sync.py into the
 * app's own folder. Songs are found by their path inside the Music folder.
 */
class LyricsDb private constructor(private val db: SQLiteDatabase) : Closeable {

    /** A song's lyrics, or null if there are none for it. */
    fun lyrics(path: String): Lyrics? =
        db.rawQuery("SELECT synced, plain, instrumental FROM lyrics WHERE path = ?", arrayOf(path)).use { c ->
            if (!c.moveToFirst()) return null
            val timed = c.getString(0)?.let(::parseLrc).orEmpty()
            Lyrics(timed, c.getString(1), c.getInt(2) != 0)
        }

    override fun close() = db.close()

    companion object {
        const val SCHEMA = "1"

        fun file(context: Context) = File(context.getExternalFilesDir(null), "lyrics.db")

        /** Null if there are no lyrics, or they are of a version this app doesn't know. */
        fun open(file: File): LyricsDb? {
            if (!file.exists()) return null
            val db = runCatching { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull() ?: return null
            val schema = runCatching {
                db.rawQuery("SELECT value FROM meta WHERE key = 'schema'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            if (schema != SCHEMA) {
                db.close()
                return null
            }
            return LyricsDb(db)
        }
    }
}

/** [timed]: lines with times, empty if only [plain] words are known. */
data class Lyrics(val timed: List<LrcLine>, val plain: String?, val instrumental: Boolean)

/** One line of timed lyrics: when it starts, in ms into the song, and its words ("" for a pause). */
data class LrcLine(val timeMs: Long, val text: String)

private val STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
private val WORD_TIME = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

/**
 * Timed lyrics in LRC: "[mm:ss.xx] words", several stamps on a line for a
 * repeated chorus, "[offset:+ms]" moving every line earlier, other tags
 * ([ar:], [ti:]…) ignored, word timings ("<mm:ss.xx>") dropped. In song order.
 */
fun parseLrc(text: String): List<LrcLine> {
    val raw = text.lines()
    val offset = raw.firstNotNullOfOrNull { OFFSET.find(it.trim())?.groupValues?.get(1)?.toLongOrNull() } ?: 0
    return raw.flatMap { line ->
        val stamps = generateSequence(STAMP.find(line.trimStart())) { m -> STAMP.find(line.trimStart(), m.range.last + 1)?.takeIf { it.range.first == m.range.last + 1 } }.toList()
        if (stamps.isEmpty()) return@flatMap emptyList()
        val words = line.trimStart().substring(stamps.last().range.last + 1).replace(WORD_TIME, "").trim().replace(Regex("\\s+"), " ")
        stamps.map { m ->
            val (min, sec, frac) = m.destructured
            val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.toLong() }
            LrcLine(maxOf(0, min.toLong() * 60_000 + sec.toLong() * 1_000 + ms - offset), words)
        }
    }.sortedBy { it.timeMs }
}

/** The index of the line being sung at [positionMs], or -1 before the first. */
fun lineAt(lines: List<LrcLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else hi = mid - 1
    }
    return found
}

/** What a lyrics card shows: a few [lines], and which is being sung ([current], -1 for none). */
data class CardLines(val lines: List<String>, val current: Int)

/**
 * Four lines for Now Playing's lyrics card: around the line being sung at
 * [positionMs] (one before, two after), or the first lines of words without
 * times. Pauses are left out: a card has no room for silence.
 */
fun cardLines(lyrics: Lyrics, positionMs: Long, size: Int = 4): CardLines {
    if (lyrics.timed.isEmpty()) {
        return CardLines(lyrics.plain.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.take(size), -1)
    }
    val sung = lyrics.timed.filter { it.text.isNotEmpty() }
    val now = lineAt(sung, positionMs)
    val end = minOf(sung.size, maxOf(now - 1, 0) + size)
    val start = maxOf(0, end - size)
    return CardLines(sung.subList(start, end).map { it.text }, if (now < 0) -1 else now - start)
}
