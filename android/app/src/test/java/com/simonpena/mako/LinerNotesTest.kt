package com.simonpena.mako

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Reading linernotes.db (tools/linernotes/build.py) on the phone, with rows from the real one. */
@RunWith(RobolectricTestRunner::class)
class LinerNotesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val faceValue = "Phil Collins/Face Value/01 In the Air Tonight.mp3"
    private val partings = "Silly Wizard/So Many Partings/02 The Valley of Strathmore.mp3"

    /** The schema of build.py, SCHEMA 1. */
    private fun notes(schema: String = "1"): File = tmp.newFile("linernotes.db").also { f ->
        f.delete()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            """
            CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
            CREATE TABLE album (id INTEGER PRIMARY KEY, mb_release TEXT, artist TEXT, title TEXT, year INTEGER,
                                label TEXT, catno TEXT, country TEXT, summary TEXT, summary_lang TEXT, wikipedia_url TEXT);
            CREATE TABLE track (path TEXT PRIMARY KEY, album_id INTEGER, title TEXT, mb_recording TEXT);
            CREATE TABLE credit (album_id INTEGER, path TEXT, role TEXT, name TEXT, detail TEXT)
            """.split(";").forEach(db::execSQL)
            db.execSQL("INSERT INTO meta VALUES ('schema', '$schema')")
            db.execSQL("INSERT INTO album VALUES (258, 'x', 'Phil Collins', 'Face Value', 1981, 'Atlantic', NULL, 'XE', 'Face Value es el álbum debut…', 'es', 'https://es.wikipedia.org/wiki/Face_Value')")
            db.execSQL("INSERT INTO album VALUES (314, 'y', 'Silly Wizard', 'So Many Partings', 1979, 'Shanachie', '79016', 'US', NULL, NULL, NULL)")
            db.execSQL("INSERT INTO album VALUES (260, 'z', 'Phil Collins', 'No Jacket Required', 1985, NULL, NULL, NULL, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO album VALUES (342, 'w', 'The Police', 'Greatest Hits', 1986, NULL, NULL, NULL, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO track VALUES ('$faceValue', 258, 'In the Air Tonight', 'r1')")
            db.execSQL("INSERT INTO track VALUES ('$partings', 314, 'The Valley of Strathmore', 'r2')")
            listOf(
                "258, '$faceValue', 'composer', 'Phil Collins', ''",
                "258, '$faceValue', 'lyricist', 'Phil Collins', ''",
                "258, '$faceValue', 'instrument', 'Daryl Stuermer', 'guitar'",
                "258, '$faceValue', 'vocal', 'Phil Collins', 'lead vocals'",
                "258, '$faceValue', 'engineer', 'Hugh Padgham', ''",
                "258, '$faceValue', 'producer', 'Hugh Padgham', ''",
                "258, NULL, 'recorded at', 'The Town House', ''",
                "258, NULL, 'photography', 'Trevor Key', ''",
                "314, '$partings', 'recorded at', 'R.E.L. Studios', ''",
                "260, NULL, 'recorded at', 'The Town House', ''",
                "260, NULL, 'mixed at', 'The Town House', ''",
                "342, NULL, 'producer', 'Hugh Padgham', ''",
                "342, NULL, 'engineer', 'Hugh Padgham', ''",
            ).forEach { db.execSQL("INSERT INTO credit VALUES ($it)") }
        }
    }

    @Test fun `a track's path inside the Music folder, from either Android's way of telling it`() {
        assertEquals(faceValue, musicPath(relativePath = "Music/Phil Collins/Face Value/", displayName = "01 In the Air Tonight.mp3"))
        assertEquals(faceValue, musicPathFromFile(data = "/storage/emulated/0/Music/$faceValue", musicRoot = "/storage/emulated/0/Music"))
    }

    @Test fun `no notes on the phone, or notes of a version the app doesn't know, are no notes`() {
        assertNull(LinerNotes.open(File(tmp.root, "missing.db")))
        assertNull(LinerNotes.open(notes(schema = "2")))
    }

    @Test fun `a track's credits, its album's included, in the order the screen shows them`() {
        val n = LinerNotes.open(notes())!!.track(faceValue)!!
        assertEquals("In the Air Tonight", n.title)
        assertEquals("Face Value", n.album.title)
        assertEquals(
            listOf(
                Section.Written to listOf(Person("Phil Collins", "music, lyrics")),
                Section.Played to listOf(Person("Daryl Stuermer", "guitar"), Person("Phil Collins", "lead vocals")),
                Section.Produced to listOf(Person("Hugh Padgham", "producer, engineer")),
                Section.Recorded to listOf(Person("The Town House", "")),
            ),
            n.sections,
        ) // photography is left out: not about the sound
    }

    @Test fun `an album's notes, found from any of its tracks`() {
        val a = LinerNotes.open(notes())!!.album(faceValue)!!
        assertEquals("Atlantic", a.label)
        assertEquals("es", a.summaryLang)
        assertEquals(1981, a.year)
    }

    @Test fun `who links this album to others in the library, by year`() {
        val n = LinerNotes.open(notes())!!
        assertEquals(
            listOf(NotesAlbum("Phil Collins", "No Jacket Required", 1985, "recorded at, mixed at")),
            n.albumsWith("The Town House", except = 258),
        )
        assertEquals(listOf("Greatest Hits"), n.albumsWith("Hugh Padgham", except = 258).map { it.title })
    }

    @Test fun `a track the notes don't know has none`() {
        assertNull(LinerNotes.open(notes())!!.track("Someone/Unknown/01 Song.mp3"))
    }
}
