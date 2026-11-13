package com.pushitup.domain.model

import androidx.compose.ui.graphics.Color

data class RankTier(
    val name      : String,   // e.g. "Soldier II"
    val baseName  : String,   // e.g. "Soldier"
    val division  : Int?,     // 1, 2, 3 — null for Immortal
    val minStars  : Long,
    val color     : Color,
    val description: String,
)

object RankSystem {

    // ── Tier definitions ──────────────────────────────────────────────────────
    // Game progression: Recruit → Soldier → Warrior → Champion → Legend → Immortal.
    // Five base ranks with 3 divisions (I / II / III) each, plus the pinnacle
    // Immortal rank (16 tiers total). Original star thresholds and base
    // colours preserved — the live economy is ~1 star per rep + combos.

    val tiers: List<RankTier> = buildList {
        fun add3(baseName: String, a: Long, b: Long, c: Long, color: Color, desc: String) {
            add(RankTier("$baseName I",   baseName, 1, a, color, desc))
            add(RankTier("$baseName II",  baseName, 2, b, color, desc))
            add(RankTier("$baseName III", baseName, 3, c, color, desc))
        }

        // Recruit   0 → 249   — first steps into the arena
        add3("Recruit", 0L, 83L, 167L,
            Color(0xFFA0785A), "Enlisted in the arena")       // warm clay-brown

        // Soldier   250 → 1 999  — forging discipline
        add3("Soldier", 250L, 750L, 1_250L,
            Color(0xFF9E9E9E), "Forged by daily discipline")  // neutral mid-grey

        // Warrior   2 000 → 5 999  — tempered by sweat
        add3("Warrior", 2_000L, 3_334L, 4_667L,
            Color(0xFFE0E0E0), "Tempered by relentless sweat") // bright silver-white

        // Champion  6 000 → 74 999  — dominating the grind
        add3("Champion", 6_000L, 28_000L, 50_000L,
            Color(0xFFFFCA28), "Dominating the grind")         // gold

        // Legend    75 000 → 199 999  — extraordinary warrior
        add3("Legend", 75_000L, 117_000L, 158_000L,
            Color(0xFFCE93D8), "Extraordinary warrior")        // light purple

        // Immortal  200 000+ (no sub-divisions)
        add(RankTier("Immortal", "Immortal", null, 200_000L,
            Color(0xFFFF5722), "Ascended beyond mortals"))     // blazing orange
    }

    /** Unique base-rank names in order. */
    val baseNames: List<String> = tiers.map { it.baseName }.distinct()

    /** All sub-tiers that belong to a given base rank. */
    fun divisionsOf(baseName: String): List<RankTier> = tiers.filter { it.baseName == baseName }

    /**
     * The inclusive upper star bound of a rank group — i.e. the last star that still belongs
     * to this group before the next group starts.  Returns null for Elite (no upper bound).
     *
     * Example: maxStarsFor("Recruit") == 249  because Soldier I starts at 250.
     */
    fun maxStarsFor(baseName: String): Long? {
        val idx = baseNames.indexOf(baseName)
        return if (idx >= 0 && idx < baseNames.size - 1) {
            divisionsOf(baseNames[idx + 1]).first().minStars - 1L
        } else null   // Elite
    }

    /**
     * The inclusive upper star bound of a specific tier division.
     * e.g. maxStarsForDivision(BeginnerI) == 16, BeginnerIII == 49.
     */
    fun maxStarsForDivision(tier: RankTier): Long? {
        val idx = tiers.indexOf(tier)
        return if (idx >= 0 && idx < tiers.size - 1) tiers[idx + 1].minStars - 1L else null
    }

    // ── Core helpers ──────────────────────────────────────────────────────────

    /** Current tier for a given star count. */
    fun tierFor(stars: Long): RankTier =
        tiers.lastOrNull { stars >= it.minStars } ?: tiers.first()

    /** Next tier above the current one, or null at Elite. */
    fun nextTier(stars: Long): RankTier? {
        val idx = tiers.indexOfLast { stars >= it.minStars }
        return if (idx < tiers.size - 1) tiers[idx + 1] else null
    }

    /** Progress 0f–1f within the current tier toward the next. */
    fun progressToNext(stars: Long): Float {
        val current = tierFor(stars)
        val next    = nextTier(stars) ?: return 1f
        val range   = (next.minStars - current.minStars).toFloat()
        val earned  = (stars - current.minStars).toFloat()
        return (earned / range).coerceIn(0f, 1f)
    }

    // ── Inactivity-penalty constants ──────────────────────────────────────────

    /** Stars lost for each full day without a workout (after the first missed day). */
    const val PENALTY_PER_MISSED_DAY = 5L

    /**
     * Additional flat penalty that kicks in when the user misses a full week (7+ days).
     * Stacks on top of the daily penalty.
     */
    const val PENALTY_EXTRA_WEEK = 25L

    // ── Combo-multiplier constants ─────────────────────────────────────────────

    /** Consecutive reps required before the bonus multiplier activates. */
    const val COMBO_START_REPS = 20

    /**
     * Maximum gap (ms) between two reps that still counts as an unbroken combo.
     * If the user rests longer than this, the consecutive counter resets.
     */
    const val COMBO_WINDOW_MS = 3_000L

    /** Hard cap on the multiplier (reached at [COMBO_START_REPS] + 40 consecutive reps). */
    const val COMBO_MAX_MULTI = 6

    /**
     * Current stars-per-rep multiplier for an ongoing in-session combo.
     *
     * Formula: x2 at 20 reps, x3 at 30, x4 at 40 … capped at [COMBO_MAX_MULTI].
     * Returns 1 (no bonus) for fewer than [COMBO_START_REPS] consecutive reps.
     */
    fun comboMultiplier(consecutiveReps: Int): Int = when {
        consecutiveReps < COMBO_START_REPS -> 1
        else -> minOf(COMBO_MAX_MULTI, 1 + (consecutiveReps - 10) / 10)
    }

    // ── Star-earning ──────────────────────────────────────────────────────────

    /**
     * Stars earned at the end of a workout session.
     *   - [comboStars]  stars accumulated via per-rep combo multiplier during the session
     *   - +(streak − 1) bonus stars  e.g. 3-day streak → +2
     * [reps] is kept for a sanity minimum: at least 1 star per rep even if [comboStars] is 0.
     */
    fun starsForSession(reps: Int, streakAfterSession: Int, comboStars: Long = 0L): Long {
        val base        = reps.toLong()
        val streakBonus = (streakAfterSession - 1).coerceAtLeast(0).toLong()
        // comboStars already includes the base (1× per rep) — add only the delta above base
        return maxOf(base, comboStars) + streakBonus
    }
}
