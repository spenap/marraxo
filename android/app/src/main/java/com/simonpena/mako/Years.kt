package com.simonpena.mako

import kotlin.math.abs

/**
 * The Years list as rows: a heading per year, then one row per album, in
 * the order the tab shows them (newest first, unknown year last). Knows
 * which year each row belongs to, and where decades start, for the scroll
 * label and the scrubber.
 */
class YearRows(groups: List<Pair<Int, Int>>) {
    private val years: IntArray = groups.flatMap { (year, albums) -> List(1 + albums) { year } }.toIntArray()

    val size get() = years.size

    fun yearAt(row: Int): Int = years[row.coerceIn(0, years.lastIndex)]

    /** A mark on the scrubber where each decade begins. */
    data class Mark(val label: String, val row: Int, val fraction: Float)

    val decades: List<Mark> = buildList {
        var last: Int? = null
        years.forEachIndexed { row, year ->
            val decade = decadeOf(year)
            if (decade != last) {
                add(Mark(shortDecade(year), row, row.toFloat() / years.size))
                last = decade
            }
        }
    }

    /**
     * The marks that fit on a scrubber [heightPx] tall, at least [minGapPx]
     * apart. Where two would collide, the decade with more rows (albums)
     * keeps its mark.
     */
    fun visibleMarks(heightPx: Float, minGapPx: Float): List<Mark> {
        val span = decades.mapIndexed { i, m -> m to ((decades.getOrNull(i + 1)?.row ?: years.size) - m.row) }
        val kept = mutableListOf<Mark>()
        for ((mark, _) in span.sortedByDescending { it.second }) {
            if (kept.none { kotlin.math.abs(it.fraction - mark.fraction) * heightPx < minGapPx }) kept += mark
        }
        return kept.sortedBy { it.row }
    }

    /** The row at a position along the scrubber, 0 (top) to 1 (bottom). */
    fun rowAt(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * years.size).toInt().coerceAtMost(years.lastIndex)

    fun fractionOf(row: Int): Float = (row + 0.5f) / years.size

    private fun decadeOf(year: Int) = if (year > 0) year / 10 else -1

    private fun shortDecade(year: Int) = if (year > 0) "’" + (year / 10 % 10 * 10).toString().padStart(2, '0') else "?"
}

/** "1990s" while moving fast, "1994" when slow. */
fun yearLabel(year: Int, coarse: Boolean): String = when {
    year <= 0 -> "Unknown year"
    coarse -> "${year / 10 * 10}s"
    else -> "$year"
}

/**
 * Decides between decades and years from the scroll speed, in rows per
 * second so it feels the same on a slow and a fast phone. Two thresholds
 * keep it from flickering when the speed hovers around one of them.
 */
class ScrollZoom(private val fast: Float = 20f, private val slow: Float = 8f) {
    var coarse = false
        private set

    fun onSpeed(rowsPerSecond: Float) {
        val speed = abs(rowsPerSecond)
        if (speed >= fast) coarse = true else if (speed <= slow) coarse = false
    }
}
