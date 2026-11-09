package com.simonpena.marraxo

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Lyrics and liner notes imported from files (for phones without adb, like the S25). */
@RunWith(RobolectricTestRunner::class)
class DataImportTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir get() = tmp.root

    private fun db(name: String, schema: String = "1", vararg sql: String): File = File(tmp.newFolder(), name).also { f ->
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("INSERT INTO meta VALUES ('schema', '$schema')")
            sql.forEach(db::execSQL)
        }
    }

    private fun lyrics(schema: String = "1") = db("whatever.db", schema,
        "CREATE TABLE lyrics (path TEXT PRIMARY KEY, synced TEXT, plain TEXT, instrumental INTEGER, lrclib_id INTEGER)",
        "INSERT INTO lyrics VALUES ('A/B/01 a.mp3', NULL, 'la', 0, 1)",
        "INSERT INTO lyrics VALUES ('A/B/02 b.mp3', NULL, 'le', 0, 2)")

    private fun notes() = db("notes (1).db", "1",
        "CREATE TABLE album (id TEXT PRIMARY KEY)",
        "CREATE TABLE credit (album TEXT, name TEXT)",
        "INSERT INTO album VALUES ('a1')")

    private fun import(file: File) = DataImport.install(file.inputStream(), dir)

    @Test fun `lyrics are recognised by their contents, not their name, and counted`() {
        assertEquals(DataImport.Imported(DataImport.Kind.LYRICS, 2), import(lyrics()))
        assertNotNull(LyricsDb.open(File(dir, "lyrics.db")))
    }

    @Test fun `liner notes are counted by album`() {
        assertEquals(DataImport.Imported(DataImport.Kind.NOTES, 1), import(notes()))
        assertNotNull(LinerNotes.open(File(dir, "linernotes.db")))
    }

    @Test fun `a file that isn't a database is refused`() {
        val text = tmp.newFile("lyrics.db").apply { writeText("not a database") }
        assertEquals(DataImport.Rejected(DataImport.Why.NOT_DATA), import(text))
    }

    @Test fun `a version this app doesn't know is refused`() {
        assertEquals(DataImport.Rejected(DataImport.Why.OTHER_VERSION), import(lyrics(schema = "2")))
    }

    @Test fun `a refused file leaves what was there, and no temporary file`() {
        import(lyrics())
        val before = File(dir, "lyrics.db").readBytes()
        import(lyrics(schema = "2"))
        assertEquals(before.toList(), File(dir, "lyrics.db").readBytes().toList())
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".importing") })
    }

    @Test fun `the summary names what came in and why a file was refused`() {
        val res = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val results = listOf(
            DataImport.Imported(DataImport.Kind.LYRICS, 3864),
            DataImport.Imported(DataImport.Kind.NOTES, 1),
            DataImport.Rejected(DataImport.Why.NOT_DATA),
        )
        assertEquals("Lyrics: 3864 songs · Liner notes: 1 album · A file wasn't lyrics or liner notes", importSummary(res, results))
    }
}
