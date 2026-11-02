package com.simonpena.makofi

import kotlin.random.Random

/**
 * Shuffle that spreads out what repeats are noticed by ([key]: the artist
 * on a genre, the album on an artist). Martin Fiedler's balanced shuffle,
 * which Spotify adopted in 2014 after people heard plain random as "not
 * random": each group's items get evenly spaced places across the list
 * from a random start, with a little jitter; then any two neighbours that
 * still share a key are split by the next item that doesn't.
 */
fun <T> spreadShuffle(items: List<T>, random: Random = Random.Default, key: (T) -> Any?): List<T> {
    val placed = items.groupBy(key).values.flatMap { group ->
        val n = group.size
        val start = random.nextDouble() / n
        group.shuffled(random).mapIndexed { k, item -> start + (k + random.nextDouble() * JITTER) / n to item }
    }
    val out = placed.sortedBy { it.first }.mapTo(ArrayList()) { it.second }
    var i = 1
    while (i < out.size) {
        val k = key(out[i])
        if (k != key(out[i - 1])) { i++; continue }
        val later = (i + 1 until out.size).firstOrNull { key(out[it]) != k }
        if (later != null) {
            out.add(i, out.removeAt(later)) // the next different item goes between them
            i++
            continue
        }
        // Nothing different after: move this one back to the first gap between two others.
        val item = out.removeAt(i)
        val gap = (0..out.size).firstOrNull { p -> (p == 0 || key(out[p - 1]) != k) && (p == out.size || key(out[p]) != k) }
        out.add(gap ?: i, item)
        if (gap == null) break // nowhere to go: one artist left over
        i = 1
    }
    return out
}

private const val JITTER = 0.2
