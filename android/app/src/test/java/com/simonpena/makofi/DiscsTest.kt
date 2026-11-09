package com.simonpena.makofi

import org.junit.Assert.assertEquals
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class) // Track holds a Uri
class DiscsTest {
    @Test fun `a double album has a disc for each, with where it starts and how long it is`() {
        val tracks = listOf(
            track(1, "Eternity", number = 1, disc = 1, durationMs = 189_000), track(2, "The Outside", number = 2, disc = 1, durationMs = 183_000),
            track(3, "Burning Down", number = 1, disc = 2, durationMs = 179_000),
        )
        assertEquals(listOf(Disc(1, first = 0, count = 2, durationMs = 372_000), Disc(2, first = 2, count = 1, durationMs = 179_000)), discs(tracks))
    }

    @Test fun `a single disc has no disc rows`() {
        assertEquals(emptyList<Disc>(), discs(listOf(track(1, "a", disc = 1), track(2, "b", disc = 1))))
    }

    @Test fun `untagged or partly tagged discs have no disc rows`() {
        assertEquals(emptyList<Disc>(), discs(listOf(track(1, "a"), track(2, "b"))))
        assertEquals(emptyList<Disc>(), discs(listOf(track(1, "a", disc = 1), track(2, "b"))))
    }
}
