package com.simonpena.makofi

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.random.Random

/** Runs only from scripts/station.sh, which passes the options as station.* properties. */
@RunWith(RobolectricTestRunner::class)
class StationSimulator {
    @Test fun station() {
        val option = { name: String -> System.getProperty("station.$name") }
        val artist = option("artist")
        assumeTrue("run through scripts/station.sh", artist != null)
        val title = option("title") ?: error("--title is required")
        val count = option("picks")?.toInt() ?: 20
        val seed = option("seed")?.toLong() ?: 0L
        val weights = option("weights") ?: "Radio"
        val compare = option("compare")
        val inventory = File("../../tools/inventory/out-clean/inventory.jsonl")
        val notes = File("../../tools/linernotes/out/linernotes.db")
        val library = RealCollection.load(inventory)
        val links = LinerNotes.open(notes)?.use { it.links(library) }.orEmpty()
        val track = library.asSequence().flatMap { it.tracks.asSequence() }
            .firstOrNull { it.artist.equals(artist, true) && it.title.equals(title, true) }
            ?: error("Seed not found: $artist — $title")
        val refs = readReferences(File("../../docs/radio-reference.tsv"))
        runStation(weights, library, links, track, parseWeights(weights), count, seed, refs)
        if (compare != null) runStation(compare, library, links, track, parseWeights(compare), count, seed, refs)
    }

    private fun runStation(label: String, library: List<Album>, links: Map<Long, Set<String>>, seed: Track, weights: RadioWeights, count: Int, randomSeed: Long, refs: List<ReferencePick>) {
        println("\n$label — seed ${seed.artist} — ${seed.title}; $count picks; random seed $randomSeed")
        val radio = Radio(library, weights, seed, links, Random(randomSeed))
        val played = mutableListOf(seed)
        val picks = mutableListOf<StationPick>()
        repeat(count) { index ->
            val pick = radio.next(played) ?: return@repeat
            val album = library.first { it.id == pick.track.albumId }
            val reasons = pick.scores.filterValues { it > 0 }.keys
            val widened = pick.reason == Reason.Wider
            val pretty = reasons.sortedBy { it.toString() }.joinToString(", ") { ("${reasonLabel(it)}: %.2f".format(pick.scores[it])) }
                .ifEmpty { "Wider" }
            println("%2d. %s | %s | %s | %s | %s".format(index + 1, pick.track.artist, album.title, pick.track.title, album.year.takeIf { it > 0 }?.toString() ?: "?", pretty))
            picks += StationPick(pick.track.artist, album.title, album.id, pick.track.title, album.year, reasons, widened)
            played += pick.track
        }
        val metrics = StationMetrics.from(picks)
        println("Only genre and era %.1f%% | albums %d | artists %d | most from one album %d | widened %d".format(metrics.onlyGenreAndEra * 100, metrics.albums, metrics.artists, metrics.mostFromOneAlbum, metrics.widened))
        val refsForSeed = refs.filter { it.seed.equals("${seed.artist}|${seed.title}", true) }
        if (refsForSeed.isNotEmpty()) {
            val selected = picks.flatMap { listOf(it.artist.lowercase(), "${it.artist}|${it.title}".lowercase()) }.toSet()
            println("Reference score %.0f%%".format(referenceScore(refsForSeed, selected) * 100))
        }
    }

    private fun reasonLabel(reason: Reason): String = when (reason) {
        is Reason.Genre -> "genre ${reason.name}"
        is Reason.Decade -> "decade ${reason.decade}s"
        is Reason.Shares -> "person ${reason.name}"
        is Reason.Artist -> "artist ${reason.name}"
        Reason.Wider -> "wider"
    }

    private fun parseWeights(spec: String): RadioWeights = when (spec.lowercase()) {
        "radio" -> RadioWeights.Radio
        "similar" -> RadioWeights.Similar
        "era" -> RadioWeights.Era
        "people" -> RadioWeights.People
        "anything" -> RadioWeights.Anything
        else -> {
            val fields = spec.split(',').associate { part -> part.substringBefore('=').trim().lowercase() to part.substringAfter('=').toDouble() }
            RadioWeights(fields.getValue("genre"), fields.getValue("decade"), fields.getValue("people"), fields.getValue("artist"), fields.getValue("band"))
        }
    }

    private fun readReferences(file: File): List<ReferencePick> = file.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { row ->
        val (seed, judgement, candidate) = row.split('\t')
        ReferencePick(seed, candidate, ReferenceJudgement.valueOf(judgement.uppercase()))
    }
}
