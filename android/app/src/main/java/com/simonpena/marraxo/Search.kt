package com.simonpena.marraxo

import java.text.Normalizer

/** Lowercase without accents, so "camaron" finds "Camarón". */
fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

private val MARKS = Regex("\\p{Mn}+")

/** Album, artist and song search over the library, folded once up front. */
class SearchIndex(library: List<Album>) {
    private val entries = library.map { a -> Triple(a, fold("${a.title} ${a.artist}"), a.tracks.map { fold(it.title) }) }

    /** Albums whose title or artist matches, and songs (album, track index) whose title matches. */
    fun search(query: String, maxSongs: Int = 60): Pair<List<Album>, List<Pair<Album, Int>>> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList<Album>() to emptyList()
        val albums = entries.filter { q in it.second }.map { it.first }
        val songs = entries.asSequence()
            .flatMap { (a, _, titles) -> titles.indices.filter { q in titles[it] }.map { a to it } }
            .take(maxSongs)
            .toList()
        return albums to songs
    }
}
