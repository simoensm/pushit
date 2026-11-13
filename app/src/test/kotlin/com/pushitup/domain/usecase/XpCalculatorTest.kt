package com.pushitup.domain.usecase

import com.pushitup.domain.usecase.XpCalculator.Tier
import com.pushitup.feature.workout.domain.PoseAngles
import org.junit.Assert.*
import org.junit.Test

class XpCalculatorTest {

    // ── xpForSession ─────────────────────────────────────────────────────────

    @Test
    fun `xpForSession returns 1 xp per rep plus 10 bonus`() {
        assertEquals(20L, XpCalculator.xpForSession(10))
    }

    @Test
    fun `xpForSession with zero reps returns only bonus`() {
        assertEquals(10L, XpCalculator.xpForSession(0))
    }

    @Test
    fun `xpForSession with 100 reps returns 110`() {
        assertEquals(110L, XpCalculator.xpForSession(100))
    }

    // ── Tier boundaries ───────────────────────────────────────────────────────

    @Test
    fun `0 xp is Bronze tier`() {
        assertEquals(Tier.BRONZE, XpCalculator.tierFor(0L))
    }

    @Test
    fun `499 xp is still Bronze`() {
        assertEquals(Tier.BRONZE, XpCalculator.tierFor(499L))
    }

    @Test
    fun `500 xp unlocks Silver tier`() {
        assertEquals(Tier.SILVER, XpCalculator.tierFor(500L))
    }

    @Test
    fun `1999 xp is still Silver`() {
        assertEquals(Tier.SILVER, XpCalculator.tierFor(1_999L))
    }

    @Test
    fun `2000 xp unlocks Gold tier`() {
        assertEquals(Tier.GOLD, XpCalculator.tierFor(2_000L))
    }

    @Test
    fun `5000 xp unlocks Platinum tier`() {
        assertEquals(Tier.PLATINUM, XpCalculator.tierFor(5_000L))
    }

    @Test
    fun `10000 xp unlocks Diamond tier`() {
        assertEquals(Tier.DIAMOND, XpCalculator.tierFor(10_000L))
    }

    // ── levelInfo: level number ───────────────────────────────────────────────

    @Test
    fun `0 xp is level 1`() {
        assertEquals(1, XpCalculator.levelInfo(0L).level)
    }

    @Test
    fun `99 xp is level 1 (within first 100-xp level slot)`() {
        assertEquals(1, XpCalculator.levelInfo(99L).level)
    }

    @Test
    fun `100 xp is level 2`() {
        assertEquals(2, XpCalculator.levelInfo(100L).level)
    }

    @Test
    fun `500 xp is level 5 (first Silver level)`() {
        assertEquals(5, XpCalculator.levelInfo(500L).level)
    }

    @Test
    fun `600 xp is level 6`() {
        assertEquals(6, XpCalculator.levelInfo(600L).level)
    }

    @Test
    fun `2000 xp is level 10 (first Gold level)`() {
        assertEquals(10, XpCalculator.levelInfo(2_000L).level)
    }

    @Test
    fun `10000 xp is level 30 (first Diamond level)`() {
        assertEquals(30, XpCalculator.levelInfo(10_000L).level)
    }

    // ── levelInfo: progress fraction ─────────────────────────────────────────

    @Test
    fun `progress fraction is 0 at tier boundary`() {
        val info = XpCalculator.levelInfo(500L)  // exact Silver start
        assertEquals(0f, info.progressFraction, 0.001f)
    }

    @Test
    fun `progress fraction is 0·5 halfway through a level`() {
        val info = XpCalculator.levelInfo(550L)  // 50 xp into 100-xp Silver level
        assertEquals(0.5f, info.progressFraction, 0.001f)
    }

    @Test
    fun `progress fraction is close to 1 near end of level`() {
        val info = XpCalculator.levelInfo(599L)  // 99 xp into 100-xp level
        assertEquals(0.99f, info.progressFraction, 0.001f)
    }

    // ── didLevelUp ────────────────────────────────────────────────────────────

    @Test
    fun `adding xp within same level does not trigger level-up`() {
        assertFalse(XpCalculator.didLevelUp(previousXp = 50L, xpGained = 30L))
    }

    @Test
    fun `adding xp that crosses a level boundary triggers level-up`() {
        // Start at 90 xp (level 1), gain 20 → crosses into level 2 at 100 xp
        assertTrue(XpCalculator.didLevelUp(previousXp = 90L, xpGained = 20L))
    }

    @Test
    fun `crossing tier boundary (Bronze to Silver) triggers level-up`() {
        // Start at 490 xp (level 4), gain 20 → crosses Silver at 500
        assertTrue(XpCalculator.didLevelUp(previousXp = 490L, xpGained = 20L))
    }

    @Test
    fun `gaining 0 xp never triggers level-up`() {
        assertFalse(XpCalculator.didLevelUp(previousXp = 99L, xpGained = 0L))
    }

    // ── PoseAngles geometry ───────────────────────────────────────────────────

    @Test
    fun `angleDegrees returns 180 for straight line`() {
        val angle = PoseAngles.angleDegrees(
            ax = 0f, ay = 0f,   // shoulder
            bx = 1f, by = 0f,   // elbow (pivot)
            cx = 2f, cy = 0f,   // wrist
        )
        assertEquals(180f, angle, 0.5f)
    }

    @Test
    fun `angleDegrees returns 90 for right angle`() {
        val angle = PoseAngles.angleDegrees(
            ax = 0f, ay = 0f,   // shoulder
            bx = 1f, by = 0f,   // elbow
            cx = 1f, cy = 1f,   // wrist perpendicular
        )
        assertEquals(90f, angle, 0.5f)
    }

    @Test
    fun `angleDegrees returns approximately 45 for 45-degree bend`() {
        val angle = PoseAngles.angleDegrees(
            ax = 0f, ay = 0f,
            bx = 1f, by = 0f,
            cx = 1f + 0.707f, cy = 0.707f,  // 45° from (1,0)
        )
        assertEquals(45f, angle, 1f)
    }

    @Test
    fun `angleDegrees is symmetric`() {
        val a1 = PoseAngles.angleDegrees(0f, 0f, 1f, 0f, 1f, 1f)
        val a2 = PoseAngles.angleDegrees(1f, 1f, 1f, 0f, 0f, 0f)
        assertEquals(a1, a2, 0.01f)
    }

    // ── hipOffset helper ──────────────────────────────────────────────────────

    @Test
    fun `hipOffset is 0 when hip is exactly at midpoint of shoulder-ankle line`() {
        val offset = PoseAngles.hipOffset(shoulderY = 0.2f, hipY = 0.6f, ankleY = 1.0f)
        assertEquals(0f, offset, 0.001f)
    }

    @Test
    fun `hipOffset is positive when hip is above midpoint`() {
        val offset = PoseAngles.hipOffset(shoulderY = 0.2f, hipY = 0.3f, ankleY = 1.0f)
        assertTrue(offset > 0f)
    }

    @Test
    fun `hipOffset exceeds threshold when hips are severely piked`() {
        val offset = PoseAngles.hipOffset(shoulderY = 0.5f, hipY = 0.1f, ankleY = 0.9f)
        assertTrue(offset > PoseAngles.BAD_FORM_THRESHOLD)
    }
}
