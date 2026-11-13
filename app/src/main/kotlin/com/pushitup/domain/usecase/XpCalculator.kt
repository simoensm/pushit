package com.pushitup.domain.usecase

/**
 * XP and level progression logic.
 *
 * XP awards:
 *   • 1 XP per rep
 *   • 10 XP session completion bonus
 *   • Challenge completion XP is defined on the Challenge document itself
 *
 * Level thresholds (cumulative XP):
 *   0    → Bronze  (level 1–4)
 *   500  → Silver  (level 5–9)
 *   2000 → Gold    (level 10–19)
 *   5000 → Platinum (level 20–29)
 *   10000 → Diamond (level 30+)
 *
 * Within each tier, each level costs [XP_PER_LEVEL_IN_TIER] additional XP.
 */
object XpCalculator {

    const val XP_PER_REP            = 1L
    const val XP_SESSION_BONUS      = 10L
    const val XP_PER_LEVEL_IN_TIER  = 100L

    data class LevelInfo(
        val level: Int,
        val tier: Tier,
        val xpIntoLevel: Long,
        val xpNeededForNext: Long,
        val progressFraction: Float,   // 0.0 – 1.0
    )

    enum class Tier(val displayName: String, val minXp: Long, val startLevel: Int) {
        BRONZE("Bronze",    0L,      1),
        SILVER("Silver",    500L,    5),
        GOLD("Gold",        2_000L,  10),
        PLATINUM("Platinum",5_000L,  20),
        DIAMOND("Diamond",  10_000L, 30),
    }

    fun xpForSession(reps: Int): Long = reps * XP_PER_REP + XP_SESSION_BONUS

    fun levelInfo(totalXp: Long): LevelInfo {
        val tier = tierFor(totalXp)
        val xpIntoTier = totalXp - tier.minXp
        val levelsIntoTier = (xpIntoTier / XP_PER_LEVEL_IN_TIER).toInt()
        val level = tier.startLevel + levelsIntoTier
        val xpIntoLevel = xpIntoTier % XP_PER_LEVEL_IN_TIER
        val progressFraction = xpIntoLevel.toFloat() / XP_PER_LEVEL_IN_TIER.toFloat()

        return LevelInfo(
            level            = level,
            tier             = tier,
            xpIntoLevel      = xpIntoLevel,
            xpNeededForNext  = XP_PER_LEVEL_IN_TIER,
            progressFraction = progressFraction,
        )
    }

    fun tierFor(totalXp: Long): Tier =
        Tier.values().reversed().first { totalXp >= it.minXp }

    /** Returns true if adding [xpGained] to [previousXp] crosses a level boundary. */
    fun didLevelUp(previousXp: Long, xpGained: Long): Boolean {
        val before = levelInfo(previousXp)
        val after  = levelInfo(previousXp + xpGained)
        return after.level > before.level
    }
}
