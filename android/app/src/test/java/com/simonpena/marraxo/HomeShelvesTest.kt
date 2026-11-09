package com.simonpena.marraxo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeShelvesTest {
    private fun a(id: Long, title: String, artist: String, year: Int, genre: String, vararg songs: String) =
        album(id, title, artist, *songs, genre = genre).copy(year = year)

    private val blueIdol = a(1, "The Blue Idol", "Altan", 2002, "Celtic", "Daily Growing", "Uncle Rat", "Roisin")
    private val secretPeople = a(2, "Secret People", "Capercaillie", 1993, "Celtic", "Bonaparte", "Grace and Pride")
    private val bothy = a(3, "Bothy Culture", "Martyn Bennett", 1997, "Celtic; Electronic", "Tryptic")
    private val shadow = a(4, "Shadow of Time", "Nightnoise", 1993, "Celtic", "Shadow")
    private val isThisIt = a(5, "Is This It", "The Strokes", 2001, "Indie Rock", "Is This It", "The Modern Age")
    private val library = listOf(blueIdol, secretPeople, bothy, shadow, isThisIt)

    private fun play(album: Album, song: Int, at: Long, ended: String = "completed") =
        Play(album.tracks[song].title, album.artist, album.title, at, ended)

    @Test fun continueListsTheAlbumsPlayedNewestFirstOnceEach() {
        val plays = listOf(play(isThisIt, 0, 300), play(blueIdol, 1, 200), play(blueIdol, 0, 100), play(isThisIt, 1, 50))
        assertEquals(listOf(isThisIt, blueIdol), continueAlbums(plays, library).map { it.album })
    }

    @Test fun anAlbumResumesAfterTheLastSongHeardToTheEnd() {
        assertEquals(2, continueAlbums(listOf(play(blueIdol, 1, 100)), library)[0].index)
    }

    @Test fun anAlbumResumesAtASongLeftHalfway() {
        assertEquals(1, continueAlbums(listOf(play(blueIdol, 1, 100, ended = "stopped")), library)[0].index)
    }

    @Test fun anAlbumHeardToTheEndStartsAgainAndIsNotInProgress() {
        val resume = continueAlbums(listOf(play(blueIdol, 2, 100)), library)[0]
        assertEquals(0, resume.index)
        assertEquals(false, resume.inProgress)
    }

    @Test fun songsOutsideTheLibraryAreLeftOut() {
        val plays = listOf(Play("Zombie", "The Cranberries", "No Need to Argue", 100, "completed"))
        assertTrue(continueAlbums(plays, library).isEmpty())
    }

    @Test fun anAlbumIsFoundDespiteAccentsAndCase() {
        val plays = listOf(Play("daily growing", "ALTAN", "the blue idol", 100, "stopped"))
        assertEquals(blueIdol, continueAlbums(plays, library).single().album)
    }

    @Test fun theLatestAlbumInProgressLeadsAsTheResumeCard() {
        val plays = listOf(play(isThisIt, 1, 300), play(blueIdol, 0, 200, ended = "stopped"))
        val resumes = continueAlbums(plays, library)
        assertEquals(isThisIt, resumes[0].album) // heard to the end
        assertEquals(blueIdol, pickUp(resumes)?.album)
    }

    @Test fun nothingToPickUpWhenEveryAlbumWasHeardToTheEnd() {
        assertNull(pickUp(continueAlbums(listOf(play(isThisIt, 1, 300)), library)))
    }

    @Test fun moreOfTheSameGenreAndDecadeAsARecentAlbum() {
        val shelves = discoverShelves(continueAlbums(listOf(play(secretPeople, 0, 100)), library), library, day = 0, minAlbums = 2)
        val first = shelves.first()
        assertEquals(secretPeople, first.seed)
        assertEquals("Celtic", first.genre)
        assertEquals(1990, first.decade)
        assertEquals(setOf(bothy, shadow), first.albums.toSet())
    }

    @Test fun theGenreAloneWhenItsDecadeHasTooFew() {
        val shelf = discoverShelves(continueAlbums(listOf(play(blueIdol, 0, 100)), library), library, day = 0, minAlbums = 2).first()
        assertEquals("Celtic", shelf.genre)
        assertNull(shelf.decade)
        assertEquals(setOf(secretPeople, bothy, shadow), shelf.albums.toSet())
    }

    @Test fun noShelfWhenNothingIsAlike() {
        assertTrue(discoverShelves(continueAlbums(listOf(play(isThisIt, 0, 100)), library), library, day = 0, minAlbums = 2).isEmpty())
    }

    @Test fun twoRecentAlbumsOfOneGenreAndDecadeMakeOneShelf() {
        val plays = listOf(play(secretPeople, 0, 200), play(shadow, 0, 100))
        assertEquals(1, discoverShelves(continueAlbums(plays, library), library, day = 0, minAlbums = 1).count { it.genre == "Celtic" && it.decade == 1990 })
    }

    @Test fun aShelfShowsItsAlbumsInAnotherOrderEachDay() {
        val many = (10L..40L).map { a(it, "Celtic $it", "Band $it", 1995, "Celtic", "Song") }
        val all = library + many
        val resumes = continueAlbums(listOf(play(secretPeople, 0, 100)), all)
        val orders = (0L..3L).map { day -> discoverShelves(resumes, all, day).first().albums }
        assertTrue(orders.toSet().size > 1)
        assertEquals(orders[0], discoverShelves(resumes, all, 0L).first().albums) // the same all day
    }
}
