package com.simonpena.marraxo

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceSetTest {
    @Test fun `reference score rewards good picks and avoiding weak picks`() {
        val refs = listOf(
            ReferencePick("seed", "good", ReferenceJudgement.GOOD),
            ReferencePick("seed", "weak", ReferenceJudgement.WEAK)
        )
        assertEquals(1.0, referenceScore(refs, setOf("good")), 0.0001)
        assertEquals(0.0, referenceScore(refs, setOf("weak")), 0.0001)
        assertEquals(0.5, referenceScore(refs, emptySet()), 0.0001)
    }
}
