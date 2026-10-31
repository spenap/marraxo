package com.simonpena.mako

/** A label on a scrubber at the row where its section starts. */
data class ScrubMark(val label: String, val row: Int, val fraction: Float)

/**
 * The strip beside a long list: marks where sections start (decades on the
 * Years tab, letters on Albums and Artists), and the mapping between a
 * position along it and a row.
 */
class Scrub(val size: Int, val marks: List<ScrubMark>) {
    /**
     * The marks that fit on a scrubber [heightPx] tall, at least [minGapPx]
     * apart. Where two would collide, the section with more rows keeps its mark.
     */
    fun visibleMarks(heightPx: Float, minGapPx: Float): List<ScrubMark> {
        val span = marks.mapIndexed { i, m -> m to ((marks.getOrNull(i + 1)?.row ?: size) - m.row) }
        val kept = mutableListOf<ScrubMark>()
        for ((mark, _) in span.sortedByDescending { it.second }) {
            if (kept.none { kotlin.math.abs(it.fraction - mark.fraction) * heightPx < minGapPx }) kept += mark
        }
        return kept.sortedBy { it.row }
    }

    /** The row at a position along the scrubber, 0 (top) to 1 (bottom). */
    fun rowAt(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * size).toInt().coerceAtMost(size - 1)

    fun fractionOf(row: Int): Float = (row + 0.5f) / size

    /** The section [row] is in, for the label shown while dragging. */
    fun labelAt(row: Int): String = marks.lastOrNull { it.row <= row }?.label ?: marks.firstOrNull()?.label.orEmpty()
}

/** An artist's letter on the scrubber, from the sort key so it matches the order: "The Corrs" is C, "Álvaro" A. */
fun initial(artist: String): String {
    val c = sortKey(artist).firstOrNull() ?: return "#"
    return if (c in 'a'..'z') c.uppercase() else "#"
}

/** A mark at the first row of each letter, for rows in sort order. */
fun letterMarks(letters: List<String>): Scrub {
    val seen = mutableSetOf<String>()
    val marks = letters.mapIndexedNotNull { row, letter ->
        if (seen.add(letter)) ScrubMark(letter, row, row.toFloat() / letters.size) else null
    }
    return Scrub(letters.size, marks)
}

/** A list gets a scrubber when it's more than three screens long. */
fun needsScrubber(total: Int, visible: Int): Boolean = visible > 0 && total > 3 * visible
