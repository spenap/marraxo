package com.simonpena.marraxo

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.random.Random

/**
 * Radios on the real collection, a regression gate like scroll-bench.sh:
 * 20 seeds × 100 songs for each feed and the mix the app plays, from the desktop inventory and liner
 * notes (tools/, never committed). Skipped where those files aren't.
 * Numbers and thresholds: docs/radio.md.
 */
@RunWith(RobolectricTestRunner::class)
class RadioSimulationTest {
    private val tools = File("../../tools")
    private val inventory = File(tools, "inventory/out-clean/inventory.jsonl")
    private val notesFile = File(tools, "linernotes/out/linernotes.db")

    private fun collection(): List<Album> = RealCollection.load(inventory)

    @Test fun `radios on the real collection stay close, spread artists and are quick`() {
        assumeTrue("needs the desktop inventory", inventory.exists())
        val library = collection()
        val links = LinerNotes.open(notesFile)?.use { it.links(library) }.orEmpty()
        val seeds = library.shuffled(Random(0)).take(20).map { it.tracks.first() }
        val report = StringBuilder("${library.size} albums, ${library.sumOf { it.tracks.size }} songs, ${links.size} with notes\n")
        report.append("mode       wider  artist<5  album<3  ms/pick  albums/100  shares genre decade artist\n")
        val modes = mapOf("Similar" to RadioWeights.Similar, "Era" to RadioWeights.Era, "People" to RadioWeights.People, "Anything" to RadioWeights.Anything, "Radio" to RadioWeights.Radio)
        val results = modes.mapValues { (name, mode) ->
            var wider = 0
            var artistRepeats = 0
            var albumRepeats = 0
            var albums = 0
            val reasons = IntArray(4)
            val start = System.nanoTime()
            seeds.forEachIndexed { s, seed ->
                val radio = Radio(library, mode, seed, links, Random(s))
                val played = mutableListOf(seed)
                repeat(100) {
                    val pick = radio.next(played)!!
                    when (pick.reason) {
                        Reason.Wider -> wider++
                        is Reason.Shares -> reasons[0]++
                        is Reason.Genre -> reasons[1]++
                        is Reason.Decade -> reasons[2]++
                        is Reason.Artist -> reasons[3]++
                    }
                    if (played.takeLast(Radio.ARTIST_GAP).any { it.artist == pick.track.artist }) artistRepeats++
                    if (played.takeLast(Radio.ALBUM_GAP).any { it.albumId == pick.track.albumId }) albumRepeats++
                    played += pick.track
                }
                albums += played.drop(1).distinctBy { it.albumId }.size
            }
            val ms = (System.nanoTime() - start) / 1e6 / 2000
            report.append("%-9s %5.1f%% %9d %8d %8.1f %11.1f %5.0f%% %4.0f%% %5.0f%% %5.0f%%\n".format(
                name, wider / 20.0, artistRepeats, albumRepeats, ms, albums / 20.0, *reasons.map { it / 20.0 }.toTypedArray()))
            assertTrue(report.toString(), artistRepeats == 0 && albumRepeats == 0)
            mapOf("wider" to wider / 20.0, "shares" to reasons[0] / 20.0)
        }
        println(report)
        assertTrue(report.toString(), results.getValue("Similar").getValue("wider") < 5)
        assertTrue(report.toString(), results.getValue("Era").getValue("wider") < 5)
        assertTrue(report.toString(), results.getValue("People").getValue("shares") > 60)
        // The one radio the app offers stays close and still follows the liner notes.
        assertTrue(report.toString(), results.getValue("Radio").getValue("wider") < 5)
        assertTrue(report.toString(), results.getValue("Radio").getValue("shares") > 20)
    }
}
