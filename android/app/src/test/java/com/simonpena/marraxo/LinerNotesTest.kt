package com.simonpena.marraxo

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
            db.execSQL("INSERT INTO track VALUES ('Phil Collins/No Jacket Required/01 Sussudio.mp3', 260, 'Sussudio', 'r3')")
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
                "314, '$partings', 'composer', '[traditional]', ''",
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
            listOf(NotesAlbum("Phil Collins", "No Jacket Required", 1985, "recorded at, mixed at", "Phil Collins/No Jacket Required/01 Sussudio.mp3")),
            n.albumsWith("The Town House", except = 258),
        )
        assertEquals(listOf("Greatest Hits"), n.albumsWith("Hugh Padgham", except = 258).map { it.title })
    }

    @Test fun `each album's people and studios, for the radio, without MusicBrainz's placeholders`() {
        val faceValue = Album(7, "Face Value", "Phil Collins", 1981, listOf(track(1, "In the Air Tonight").copy(path = faceValue)))
        val partings = Album(8, "So Many Partings", "Silly Wizard", 1979, listOf(track(2, "The Valley").copy(path = partings)))
        val unknown = Album(9, "Demo", "Nobody", 2000, listOf(track(3, "Song").copy(path = "Nobody/Demo/01 Song.mp3")))
        assertEquals(
            mapOf(
                7L to setOf("Phil Collins", "Daryl Stuermer", "Hugh Padgham", "The Town House"), // not the photographer
                8L to setOf("R.E.L. Studios"), // not "[traditional]"
            ),
            LinerNotes.open(notes())!!.links(listOf(faceValue, partings, unknown)),
        )
    }

    @Test fun `an album's people from all its credits, each once`() {
        val n = LinerNotes.open(notes())!!
        assertEquals(
            listOf(
                Section.Written to listOf(Person("Phil Collins", "music, lyrics")),
                Section.Played to listOf(Person("Daryl Stuermer", "guitar"), Person("Phil Collins", "lead vocals")),
                Section.Produced to listOf(Person("Hugh Padgham", "producer, engineer")),
                Section.Recorded to listOf(Person("The Town House", "")),
            ),
            n.albumSections(258),
        )
    }

    @Test fun `who on a screen is also on other albums, found in one go`() {
        val n = LinerNotes.open(notes())!!
        val linked = n.linked(listOf("Phil Collins", "Hugh Padgham", "The Town House", "Daryl Stuermer"), except = 258)
        assertEquals(setOf("Hugh Padgham", "The Town House"), linked.keys) // Phil Collins is credited only on Face Value here
        assertEquals(listOf("Greatest Hits"), linked.getValue("Hugh Padgham").map { it.title })
        assertEquals(null, linked.getValue("Hugh Padgham")[0].path) // no tracks of it in the notes: not openable
    }

    @Test fun `players are led by the artist, a string section folds away, linked players stay in view`() {
        val played = listOf(
            Person("Clive Anstee", "cello"), Person("Irvine Arditti", "violin"), Person("Bruce Dukov", "violin"),
            Person("Roger Best", "viola"), Person("Chris Lawrence", "double bass"),
            Person("Phil Collins", "drums, vocals"), Person("Daryl Stuermer", "guitar"),
        )
        val p = arrangePlayers(played, artist = "Phil Collins", keep = setOf("Bruce Dukov"))
        assertEquals(listOf("Phil Collins", "Daryl Stuermer", "Bruce Dukov"), p.lead.map { it.name })
        assertEquals(listOf("Clive Anstee", "Irvine Arditti", "Roger Best", "Chris Lawrence"), p.strings.map { it.name })
    }

    @Test fun `a small string section isn't worth folding`() {
        val played = listOf(Person("Sharokav", "violin"), Person("Phil Collins", "drums"))
        val p = arrangePlayers(played, artist = "Phil Collins", keep = emptySet())
        assertEquals(listOf("Phil Collins", "Sharokav"), p.lead.map { it.name })
        assertEquals(emptyList<Person>(), p.strings)
    }

    @Test fun `a track the notes don't know has none`() {
        assertNull(LinerNotes.open(notes())!!.track("Someone/Unknown/01 Song.mp3"))
    }
}
