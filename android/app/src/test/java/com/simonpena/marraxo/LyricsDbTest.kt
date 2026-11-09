package com.simonpena.marraxo

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Reading lyrics.db (tools/lyrics/lyrics_build.py, schema 1). */
@RunWith(RobolectricTestRunner::class)
class LyricsDbTest {
    @get:Rule val tmp = TemporaryFolder()

    private val air = "Phil Collins/Face Value/01 In the Air Tonight.mp3"
    private val reel = "Altan/Local Ground/03 Reel.mp3"
    private val plainOnly = "Maná/Amar Es Combatir/02 Labios Compartidos.mp3"

    private fun db(schema: String = "1"): File = File(tmp.root, "lyrics-$schema.db").also { f ->
        f.delete()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE lyrics (path TEXT PRIMARY KEY, synced TEXT, plain TEXT, instrumental INTEGER, lrclib_id INTEGER)")
            db.execSQL("INSERT INTO meta VALUES ('schema', '$schema')")
            db.execSQL("INSERT INTO lyrics VALUES ('$air', '[00:17.12] Well, if you told me\n[00:21.50] I would not lend a hand', 'Well, if you told me', 0, 1)")
            db.execSQL("INSERT INTO lyrics VALUES ('$reel', NULL, NULL, 1, 2)")
            db.execSQL("INSERT INTO lyrics VALUES ('$plainOnly', NULL, 'Amor mío', 0, 3)")
        }
    }

    @Test fun `a song's timed lines, and its words`() {
        val l = LyricsDb.open(db())!!.lyrics(air)!!
        assertEquals(listOf(LrcLine(17_120, "Well, if you told me"), LrcLine(21_500, "I would not lend a hand")), l.timed)
        assertEquals("Well, if you told me", l.plain)
    }

    @Test fun `words without times, and a tune without words`() {
        val n = LyricsDb.open(db())!!
        assertEquals(Lyrics(emptyList(), "Amor mío", instrumental = false), n.lyrics(plainOnly))
        assertTrue(n.lyrics(reel)!!.instrumental)
    }

    @Test fun `no file, another version, or a song it doesn't know means no lyrics`() {
        assertNull(LyricsDb.open(File(tmp.root, "missing.db")))
        assertNull(LyricsDb.open(db(schema = "2")))
        assertNull(LyricsDb.open(db())!!.lyrics("Someone/Else/01 Song.mp3"))
    }
}
