package com.simonpena.makofi

import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream

/**
 * Lyrics and liner notes from a file the user picks, for phones without adb
 * (the S25). A file is told by its tables, not its name, and replaces the
 * current one only once it has been checked.
 */
object DataImport {
    enum class Kind(val fileName: String, val schema: String) {
        LYRICS("lyrics.db", LyricsDb.SCHEMA),
        NOTES("linernotes.db", LinerNotes.SCHEMA),
    }

    enum class Why { NOT_DATA, OTHER_VERSION }

    sealed interface Result
    /** [count]: songs with lyrics, or albums with notes. */
    data class Imported(val kind: Kind, val count: Int) : Result
    data class Rejected(val why: Why) : Result

    /** Copies [source] into [dir] as the lyrics or the liner notes, if it is either. */
    fun install(source: InputStream, dir: File): Result {
        val incoming = File.createTempFile("import", ".importing", dir)
        try {
            source.use { input -> incoming.outputStream().use { input.copyTo(it) } }
            val result = identify(incoming)
            if (result is Imported && !incoming.renameTo(File(dir, result.kind.fileName))) return Rejected(Why.NOT_DATA)
            return result
        } finally {
            incoming.delete()
        }
    }

    private fun identify(file: File): Result {
        val db = runCatching { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull()
            ?: return Rejected(Why.NOT_DATA)
        return db.use {
            val tables = runCatching {
                db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
            }.getOrNull() ?: return Rejected(Why.NOT_DATA)
            val kind = when {
                "lyrics" in tables -> Kind.LYRICS
                "album" in tables && "credit" in tables -> Kind.NOTES
                else -> return Rejected(Why.NOT_DATA)
            }
            val schema = runCatching {
                db.rawQuery("SELECT value FROM meta WHERE key = 'schema'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            if (schema != kind.schema) return Rejected(Why.OTHER_VERSION)
            val table = if (kind == Kind.LYRICS) "lyrics" else "album"
            Imported(kind, db.rawQuery("SELECT count(*) FROM $table", null).use { c -> c.moveToFirst(); c.getInt(0) })
        }
    }
}

/** What an import brought in, for the library's status line. */
fun importSummary(res: Resources, results: List<DataImport.Result>): String = results.joinToString(" · ") { r ->
    when (r) {
        is DataImport.Imported -> when (r.kind) {
            DataImport.Kind.LYRICS -> res.getQuantityString(R.plurals.import_lyrics, r.count, r.count)
            DataImport.Kind.NOTES -> res.getQuantityString(R.plurals.import_notes, r.count, r.count)
        }
        is DataImport.Rejected -> res.getString(if (r.why == DataImport.Why.NOT_DATA) R.string.import_not_data else R.string.import_other_version)
    }
}
