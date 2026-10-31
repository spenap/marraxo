package com.simonpena.mako

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

/**
 * Credits, studios and stories for the library, from linernotes.db (roadmap
 * C3): built on the laptop by tools/linernotes/build.py from MusicBrainz and
 * Wikipedia, and copied by tools/sync/sync.py into the app's own folder.
 * Tracks are found by their path inside the Music folder ([musicPath]).
 */
class LinerNotes private constructor(private val db: SQLiteDatabase) : Closeable {

    /** A track's people and places, its album's included; null if the notes don't know the track. */
    fun track(path: String): TrackNotes? {
        val (title, albumId) = db.rawQuery("SELECT title, album_id FROM track WHERE path = ?", arrayOf(path)).use { c ->
            if (!c.moveToFirst()) return null
            c.getString(0) to c.getLong(1)
        }
        val album = albumById(albumId) ?: return null
        val credits = db.rawQuery(
            "SELECT role, name, detail FROM credit WHERE path = ? OR (album_id = ? AND path IS NULL) ORDER BY rowid",
            arrayOf(path, albumId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Credit(c.getString(0), c.getString(1), c.getString(2).orEmpty())) } }
        return TrackNotes(title, album, sections(credits))
    }

    /** The album a track belongs to, found from the track's path. */
    fun album(path: String): AlbumNotes? =
        db.rawQuery("SELECT album_id FROM track WHERE path = ?", arrayOf(path)).use { c ->
            if (c.moveToFirst()) albumById(c.getLong(0)) else null
        }

    /** The other albums a person or place appears on, by year, with what they did there. */
    fun albumsWith(name: String, except: Long): List<NotesAlbum> =
        db.rawQuery(
            "SELECT a.id, a.artist, a.title, a.year, c.role FROM credit c JOIN album a ON a.id = c.album_id " +
                "WHERE c.name = ? AND a.id != ? ORDER BY c.rowid",
            arrayOf(name, except.toString()),
        ).use { c ->
            val roles = LinkedHashMap<Long, Pair<NotesAlbum, LinkedHashSet<String>>>()
            while (c.moveToNext()) {
                val (_, set) = roles.getOrPut(c.getLong(0)) { NotesAlbum(c.getString(1), c.getString(2), c.getInt(3), "") to LinkedHashSet() }
                set += c.getString(4)
            }
            roles.values.map { (a, set) -> a.copy(roles = set.joinToString(", ")) }.sortedWith(compareBy({ it.year }, { it.title }))
        }

    private fun albumById(id: Long): AlbumNotes? =
        db.rawQuery(
            "SELECT id, artist, title, year, label, catno, country, summary, summary_lang, wikipedia_url FROM album WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c ->
            if (!c.moveToFirst()) return null
            AlbumNotes(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4), c.getString(5), c.getString(6),
                c.getString(7), c.getString(8), c.getString(9))
        }

    override fun close() = db.close()

    companion object {
        const val SCHEMA = "1"

        fun file(context: Context) = File(context.getExternalFilesDir(null), "linernotes.db")

        /** Null if there are no notes, or they are of a version this app doesn't know. */
        fun open(file: File): LinerNotes? {
            if (!file.exists()) return null
            val db = runCatching { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull() ?: return null
            val schema = runCatching {
                db.rawQuery("SELECT value FROM meta WHERE key = 'schema'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            if (schema != SCHEMA) {
                db.close()
                return null
            }
            return LinerNotes(db)
        }
    }
}

/** A track's path inside the Music folder, as Android 10+ tells it. */
fun musicPath(relativePath: String, displayName: String) = relativePath.removePrefix("Music/") + displayName

/** The same, from the file path Android 8–9 give. */
fun musicPathFromFile(data: String, musicRoot: String) = data.removePrefix("$musicRoot/")

data class AlbumNotes(
    val id: Long,
    val artist: String,
    val title: String,
    val year: Int,
    val label: String?,
    val catno: String?,
    val country: String?,
    val summary: String?,
    val summaryLang: String?,
    val wikipediaUrl: String?,
)

data class TrackNotes(val title: String, val album: AlbumNotes, val sections: List<Pair<Section, List<Person>>>)

/** Another album a person or place appears on. */
data class NotesAlbum(val artist: String, val title: String, val year: Int, val roles: String)

/** Someone or somewhere in a section, with what they did ("guitar", "producer, engineer"). */
data class Person(val name: String, val roles: String)

enum class Section { Written, Played, Produced, Recorded }

private data class Credit(val role: String, val name: String, val detail: String)

/** Each MusicBrainz role: its section, and how it reads next to a name. Unlisted roles aren't shown. */
private val ROLES: Map<String, Pair<Section, (String) -> String>> = mapOf(
    "composer" to (Section.Written to { _ -> "music" }),
    "lyricist" to (Section.Written to { _ -> "lyrics" }),
    "writer" to (Section.Written to { _ -> "songwriter" }),
    "arranger" to (Section.Written to { _ -> "arrangement" }),
    "instrument arranger" to (Section.Written to { _ -> "arrangement" }),
    "orchestrator" to (Section.Written to { _ -> "orchestration" }),
    "instrument" to (Section.Played to { d -> d }),
    "vocal" to (Section.Played to { d -> d.ifEmpty { "vocals" } }),
    "performer" to (Section.Played to { d -> d }),
    "performing orchestra" to (Section.Played to { _ -> "orchestra" }),
    "conductor" to (Section.Played to { _ -> "conductor" }),
    "producer" to (Section.Produced to { d -> listOf(d, "producer").filter { it.isNotEmpty() }.joinToString(" ") }),
    "engineer" to (Section.Produced to { d -> listOf(d, "engineer").filter { it.isNotEmpty() }.joinToString(" ") }),
    "recording" to (Section.Produced to { _ -> "recording" }),
    "mix" to (Section.Produced to { _ -> "mixing" }),
    "mastering" to (Section.Produced to { _ -> "mastering" }),
    "programming" to (Section.Produced to { _ -> "programming" }),
    "recorded at" to (Section.Recorded to { _ -> "" }), // the heading says it
    "mixed at" to (Section.Recorded to { _ -> "mixed" }),
    "mastered at" to (Section.Recorded to { _ -> "mastered" }),
)

/** Within a person, roles read in this order: "producer, engineer", "music, lyrics". */
private val ROLE_ORDER = ROLES.keys.toList()

/** Credits grouped into the screen's sections, each person once, in order of first appearance. */
private fun sections(credits: List<Credit>): List<Pair<Section, List<Person>>> {
    val bySection = sortedMapOf<Section, LinkedHashMap<String, MutableList<Credit>>>()
    for (c in credits) {
        val section = ROLES[c.role]?.first ?: continue
        bySection.getOrPut(section) { LinkedHashMap() }.getOrPut(c.name) { mutableListOf() } += c
    }
    return bySection.map { (section, people) ->
        section to people.map { (name, cs) ->
            val roles = cs.sortedBy { ROLE_ORDER.indexOf(it.role) }.map { ROLES.getValue(it.role).second(it.detail) }
            Person(name, roles.filter { it.isNotEmpty() }.distinct().joinToString(", "))
        }
    }
}
