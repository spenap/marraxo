package com.simonpena.marraxo

import android.content.res.Resources
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
    val decades: List<ScrubMark> = buildList {
        var last: Int? = null
        years.forEachIndexed { row, year ->
            val decade = decadeOf(year)
            if (decade != last) {
                add(ScrubMark(shortDecade(year), row, row.toFloat() / years.size))
                last = decade
            }
        }
    }

    val scrub = Scrub(years.size, decades)

    fun visibleMarks(heightPx: Float, minGapPx: Float) = scrub.visibleMarks(heightPx, minGapPx)

    fun rowAt(fraction: Float) = scrub.rowAt(fraction)

    fun fractionOf(row: Int) = scrub.fractionOf(row)

    private fun decadeOf(year: Int) = if (year > 0) year / 10 else -1

    private fun shortDecade(year: Int) = if (year > 0) "’" + (year / 10 % 10 * 10).toString().padStart(2, '0') else "?"
}

/** "1990s" while moving fast, "1994" when slow. */
fun yearLabel(res: Resources, year: Int, coarse: Boolean): String = when {
    year <= 0 -> res.getString(R.string.unknown_year)
    coarse -> res.getString(R.string.decade, year / 10 * 10)
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
