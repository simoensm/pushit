package com.pushitup.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankSystemTest {

    @Test
    fun `tier order is Recruit to Immortal`() {
        assertEquals(
            listOf("Recruit", "Soldier", "Warrior", "Champion", "Legend", "Immortal"),
            RankSystem.baseNames,
        )
    }

    @Test
    fun `16 tiers total - 5 base ranks x 3 divisions + Immortal`() {
        assertEquals(16, RankSystem.tiers.size)
    }

    @Test
    fun `zero stars is Recruit I`() {
        assertEquals("Recruit I", RankSystem.tierFor(0L).name)
    }

    @Test
    fun `tier boundaries`() {
        assertEquals("Recruit III",  RankSystem.tierFor(249L).name)
        assertEquals("Soldier I",    RankSystem.tierFor(250L).name)
        assertEquals("Soldier III",  RankSystem.tierFor(1_999L).name)
        assertEquals("Warrior I",    RankSystem.tierFor(2_000L).name)
        assertEquals("Warrior III",  RankSystem.tierFor(5_999L).name)
        assertEquals("Champion I",   RankSystem.tierFor(6_000L).name)
        assertEquals("Champion III", RankSystem.tierFor(74_999L).name)
        assertEquals("Legend I",     RankSystem.tierFor(75_000L).name)
        assertEquals("Legend III",   RankSystem.tierFor(199_999L).name)
        assertEquals("Immortal",     RankSystem.tierFor(200_000L).name)
    }

    @Test
    fun `Immortal has no division and no next tier`() {
        val immortal = RankSystem.tierFor(250_000L)
        assertNull(immortal.division)
        assertNull(RankSystem.nextTier(250_000L))
        assertEquals(1f, RankSystem.progressToNext(250_000L), 0f)
    }

    @Test
    fun `thresholds are strictly increasing`() {
        RankSystem.tiers.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.name} (${a.minStars}) should be below ${b.name} (${b.minStars})",
                a.minStars < b.minStars)
        }
    }

    @Test
    fun `progress within tier is between 0 and 1`() {
        val p = RankSystem.progressToNext(300L)   // inside Soldier I
        assertTrue(p in 0f..1f)
    }

    @Test
    fun `maxStarsFor returns last star before next group`() {
        assertEquals(249L, RankSystem.maxStarsFor("Recruit"))
        assertEquals(1_999L, RankSystem.maxStarsFor("Soldier"))
        assertEquals(5_999L, RankSystem.maxStarsFor("Warrior"))
        assertEquals(74_999L, RankSystem.maxStarsFor("Champion"))
        assertEquals(199_999L, RankSystem.maxStarsFor("Legend"))
        assertNull(RankSystem.maxStarsFor("Immortal"))
    }
}
