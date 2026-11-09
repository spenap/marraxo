package com.simonpena.marraxo

import kotlin.random.Random

/** One row of [PlayHistory], as the Home tab reads it. */
data class Play(val title: String, val artist: String, val album: String, val startedAt: Long, val ended: String)

/** An album played lately and the song to go on from: [index] into its tracks. */
data class Resume(val album: Album, val index: Int, val inProgress: Boolean)

/**
 * The albums in [plays] (newest first), once each, newest first. Each goes
 * on after the last song heard to its end, or at a song left halfway; an
 * album heard to its end starts again.
 */
fun continueAlbums(plays: List<Play>, library: List<Album>, limit: Int = 12): List<Resume> {
    val songs = HashMap<String, Pair<Album, Int>>()
    for (album in library) album.tracks.forEachIndexed { i, t -> songs.putIfAbsent(fold(album.title) + "\u0000" + fold(t.title), album to i) }
    val seen = HashSet<Long>()
    val resumes = ArrayList<Resume>()
    for (play in plays.sortedByDescending { it.startedAt }) {
        val (album, i) = songs[fold(play.album) + "\u0000" + fold(play.title)] ?: continue
        if (!seen.add(album.id)) continue
        val next = if (play.ended == "completed") i + 1 else i
        resumes += if (next < album.tracks.size) Resume(album, next, inProgress = true) else Resume(album, 0, inProgress = false)
        if (resumes.size == limit) break
    }
    return resumes
}

/** The large card at the top: the latest album not heard to its end. */
fun pickUp(resumes: List<Resume>): Resume? = resumes.firstOrNull { it.inProgress }

/** More like [seed]: albums of its [genre], and of its [decade] unless that leaves too few. */
data class DiscoverShelf(val seed: Album, val genre: String, val decade: Int?, val albums: List<Album>)

/**
 * A shelf for each of the latest albums played, of others with one of its
 * genres from the same decade, or of the genre alone when the decade has
 * fewer than [minAlbums]. Each shelf's order changes once a [day].
 */
fun discoverShelves(resumes: List<Resume>, library: List<Album>, day: Long, seeds: Int = 3, minAlbums: Int = 4, size: Int = 12): List<DiscoverShelf> {
    val shelves = ArrayList<DiscoverShelf>()
    for (resume in resumes) {
        if (shelves.size == seeds) break
        val seed = resume.album
        val keys = genreTags(seed.genre).associateBy(::genreKey)
        if (keys.isEmpty()) continue
        val decade = seed.year.takeIf { it > 0 }?.let { it / 10 * 10 }
        for ((key, genre) in keys) {
            val same = library.filter { it.id != seed.id && genreTags(it.genre).any { g -> genreKey(g) == key } }
            val ofDecade = if (decade == null) emptyList() else same.filter { it.year / 10 * 10 == decade }
            val (albums, d) = when {
                ofDecade.size >= minAlbums -> ofDecade to decade
                same.size >= minAlbums -> same to null
                else -> continue
            }
            if (shelves.any { genreKey(it.genre) == key && it.decade == d }) continue
            shelves += DiscoverShelf(seed, genre, d, albums.shuffled(Random(day * 31 + seed.id)).take(size))
            break
        }
    }
    return shelves
}
