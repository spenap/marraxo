package com.simonpena.mako

import android.content.res.Resources
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How a radio chooses (docs/radio.md): what counts as close, and how far
 * below the closest a song may score and still be picked ([band]).
 */
enum class RadioMode(val label: Int, val genre: Double, val decade: Double, val people: Double, val artist: Double, val band: Double) {
    Similar(R.string.radio_similar, genre = 3.0, decade = 2.0, people = 3.0, artist = 0.5, band = 0.6),
    Era(R.string.radio_era, genre = 1.0, decade = 4.0, people = 0.5, artist = 0.3, band = 0.75),
    People(R.string.radio_people, genre = 1.0, decade = 0.5, people = 5.0, artist = 1.5, band = 0.5),
    Anything(R.string.radio_anything, genre = 0.3, decade = 0.3, people = 0.3, artist = 0.0, band = 0.0),
}

/** Why a song was picked, for Now Playing. */
sealed interface Reason {
    /** A person or studio on both albums, from the liner notes. */
    data class Shares(val name: String) : Reason
    data class Genre(val name: String) : Reason
    data class Decade(val decade: Int) : Reason
    data class Artist(val name: String) : Reason
    /** Nothing close was left; the radio went further. */
    data object Wider : Reason
}

data class Pick(val track: Track, val reason: Reason)

/** A [Reason] as Now Playing shows it: "Hugh Padgham on both", "1980s". */
fun reasonText(res: Resources, reason: Reason): String = when (reason) {
    is Reason.Shares -> res.getString(R.string.radio_shares, reason.name)
    is Reason.Genre -> reason.name
    is Reason.Decade -> yearLabel(res, reason.decade, coarse = true)
    is Reason.Artist -> res.getString(R.string.radio_artist, reason.name)
    Reason.Wider -> res.getString(R.string.radio_wider)
}

/**
 * A radio from [seed]: each song is scored against the seed, and at half
 * weight against the song before it, so the radio keeps its course
 * instead of drifting. A song is drawn at random, weighted by score, from
 * those scoring near the best, so it never settles into a loop. When nothing
 * close is left, the band falls to whatever remains and the radio widens
 * instead of stopping.
 *
 * [links]: each album's people and studios from the liner notes, by album id.
 */
class Radio(
    library: List<Album>,
    private val mode: RadioMode,
    private val seed: Track,
    private val links: Map<Long, Set<String>> = emptyMap(),
    private val random: Random = Random.Default,
) {
    private val albums = library.associateBy { it.id }
    private val tracks = library.flatMap { it.tracks }
    private val genres: Map<Long, Set<String>> = genreLabels(library)
    /** On how many albums each person or studio is. */
    private val reach: Map<String, Int> = links.values.flatten().groupingBy { it }.eachCount()

    /** The next song after [played] (this session's, the seed first and the queue ahead last); null when all have played. */
    fun next(played: List<Track>): Pick? {
        val heard = played.mapTo(HashSet()) { it.id }
        val unheard = tracks.filter { it.id !in heard }
        if (unheard.isEmpty()) return null
        val recentArtists = played.takeLast(ARTIST_GAP).mapTo(HashSet()) { it.artist }
        val recentAlbums = played.takeLast(ALBUM_GAP).mapTo(HashSet()) { it.albumId }
        val spaced = unheard.filter { it.artist !in recentArtists && it.albumId !in recentAlbums }
            .ifEmpty { unheard.filter { it.albumId !in recentAlbums } }
            .ifEmpty { unheard }

        val current = played.lastOrNull() ?: seed
        // Songs from one album by one artist score alike: score each pair once.
        val byAlbum = HashMap<Pair<Long, String>, Features>()
        val scored = spaced.map { it to byAlbum.getOrPut(it.albumId to it.artist) { features(it, current) } }
        val best = scored.maxOf { it.second.total }
        val band = scored.filter { it.second.total >= best * mode.band }
        val weights = band.map { (t, f) -> (f.total + FLOOR) / sqrt(albums[t.albumId]?.tracks?.size?.toDouble() ?: 1.0) }
        var r = random.nextDouble() * weights.sum()
        val i = weights.indexOfFirst { w -> (r - w).also { r = it } < 0 }.takeIf { it >= 0 } ?: band.lastIndex
        val (track, f) = band[i]
        return Pick(track, f.reason)
    }

    private class Features(val byReason: Map<Reason, Double>, val fromSeed: Double) {
        val total = byReason.values.sum()
        val reason: Reason get() = if (fromSeed == 0.0) Reason.Wider else byReason.maxBy { it.value }.key
    }

    private fun features(candidate: Track, current: Track): Features {
        val byReason = HashMap<Reason, Double>()
        val fromSeed = closeness(seed, candidate, 1.0, byReason)
        if (current.id != seed.id) closeness(current, candidate, 0.5, byReason)
        return Features(byReason, fromSeed)
    }

    /** Adds what [b] has in common with [a] to [into], at [scale]; returns how much that was. */
    private fun closeness(a: Track, b: Track, scale: Double, into: MutableMap<Reason, Double>): Double {
        var sum = 0.0
        fun add(reason: Reason, weight: Double) {
            if (weight == 0.0) return
            into.merge(reason, weight * scale, Double::plus)
            sum += weight * scale
        }
        val albumA = albums[a.albumId]
        val albumB = albums[b.albumId]
        val sharedGenre = genres[a.albumId].orEmpty().intersect(genres[b.albumId].orEmpty()).minOrNull()
        if (sharedGenre != null) add(Reason.Genre(sharedGenre), mode.genre)
        val yearA = albumA?.year ?: 0
        val yearB = albumB?.year ?: 0
        if (yearA > 0 && yearB > 0) {
            val apart = abs(yearA / 10 - yearB / 10)
            if (apart <= 1) add(Reason.Decade(yearB / 10 * 10), if (apart == 0) mode.decade else mode.decade / 2)
        }
        val shared = links[a.albumId].orEmpty().intersect(links[b.albumId].orEmpty()) - if (a.artist == b.artist) setOf(a.artist) else emptySet()
        // The person on fewest albums says most: "Hugh Padgham" rather than a big studio.
        shared.minByOrNull { reach[it] ?: 0 }?.let { add(Reason.Shares(it), mode.people) }
        if (a.artist == b.artist) add(Reason.Artist(b.artist), mode.artist)
        return sum
    }

    companion object {
        /** No artist again within this many songs, no album within [ALBUM_GAP]; relaxed when nothing else is left. */
        const val ARTIST_GAP = 5
        const val ALBUM_GAP = 3
        /** Gives every song in the band a chance, even one with nothing in common. */
        private const val FLOOR = 1.0
    }
}

/** Each album's genres, as the Genres tab names them: by family where the genre has one. */
private fun genreLabels(library: List<Album>): Map<Long, Set<String>> {
    val all = genres(library)
    val shelves = genreShelves(all).filter { !it.isOther }
    val labels = all.associate { g -> g.key to shelves.filter { g in it.genres }.map { it.name }.ifEmpty { listOf(g.name) } }
    val byAlbum = HashMap<Long, MutableSet<String>>()
    all.forEach { g -> g.albums.forEach { a -> byAlbum.getOrPut(a.id) { HashSet() } += labels.getValue(g.key) } }
    return byAlbum
}
