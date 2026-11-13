package com.pushitup.domain.model

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class Difficulty { AMATEUR, MEDIUM, ADVANCED }

enum class ChallengeType {
    REPS_TOTAL,      // accumulate N reps since activation
    STREAK,          // reach N-day consecutive streak
    SINGLE_SESSION,  // N reps in a single session
    DAILY_REPS,      // N reps per day for D days (tracked via total delta)
}

// ── Challenge definition ──────────────────────────────────────────────────────

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: Difficulty,
    val type: ChallengeType,
    val target: Int,           // reps or days depending on type
    val targetDays: Int = 1,   // for DAILY_REPS: how many days
    val starReward: Int,
    val starCost: Int = 0,     // stars deducted on activation (entry fee)
    val durationDays: Int = 7, // window before challenge expires
)

// ── User's activated challenge ────────────────────────────────────────────────

data class UserChallenge(
    val uid: String = "",
    val challengeId: String = "",
    val startedAt: Long = 0L,
    val baseReps: Long = 0L,
    val baseStreak: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long = 0L,
)

// ── UI model combining both ───────────────────────────────────────────────────

data class ChallengeWithState(
    val challenge: Challenge,
    val userChallenge: UserChallenge?,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val isExpired: Boolean = false,
)

// ── Achievement ───────────────────────────────────────────────────────────────

/**
 * @param tag  Short identity tag (e.g. "#IronMan") unlocked alongside this achievement.
 *             Null means the achievement grants no equippable tag.
 *             Users can equip up to 3 unlocked tags on their public profile.
 */
data class Achievement(
    val id         : String,
    val title      : String,
    val description: String,
    val starReward : Int,
    val condition  : AchievementCondition,
    val tag        : String? = null,
)

sealed class AchievementCondition {
    data class TotalReps    (val count: Long) : AchievementCondition()
    data class Streak       (val days : Int)  : AchievementCondition()
    data class FriendsCount (val count: Int)  : AchievementCondition()
    /** Triggered when the user's personal-best rep count in a single session reaches [count]. */
    data class SingleSession(val count: Long) : AchievementCondition()
}

/**
 * Fractional progress (0f–1f) toward unlocking this achievement.
 * Mirrors the unlock semantics in ProfileViewModel.isUnlocked.
 * Used by the badge UI to render locked-badge progress bars.
 */
fun Achievement.progressFraction(
    totalReps       : Long,
    longestStreak   : Int,
    friendsCount    : Int,
    bestSessionReps : Long,
): Float = when (val c = condition) {
    is AchievementCondition.TotalReps     -> totalReps.toFloat()       / c.count
    is AchievementCondition.Streak        -> longestStreak.toFloat()   / c.days
    is AchievementCondition.FriendsCount  -> friendsCount.toFloat()    / c.count
    is AchievementCondition.SingleSession -> bestSessionReps.toFloat() / c.count
}.coerceIn(0f, 1f)

// ── Static challenge data ─────────────────────────────────────────────────────

val AllChallenges: List<Challenge> = listOf(

    // AMATEUR  (cost ≈ 25% of reward)
    Challenge("c_warm_up",        "Warm Up",        "Accumulate 25 push-ups in a single day",    Difficulty.AMATEUR, ChallengeType.REPS_TOTAL,    25,  starReward = 100,  starCost = 25,   durationDays = 1),
    Challenge("c_3day_streak",    "3 Days Strong",  "Work out 3 days in a row",                  Difficulty.AMATEUR, ChallengeType.STREAK,         3,   starReward = 150,  starCost = 35,   durationDays = 7),
    Challenge("c_half_century",   "Half Century",   "Accumulate 50 push-ups in a week",          Difficulty.AMATEUR, ChallengeType.REPS_TOTAL,    50,  starReward = 150,  starCost = 35,   durationDays = 7),
    Challenge("c_rise_grind",     "Rise & Grind",   "20 push-ups per day for 3 days",            Difficulty.AMATEUR, ChallengeType.DAILY_REPS,    20,  targetDays = 3, starReward = 200, starCost = 50,  durationDays = 5),
    Challenge("c_century_rookie", "Century Rookie", "Accumulate 100 push-ups in a week",         Difficulty.AMATEUR, ChallengeType.REPS_TOTAL,   100,  starReward = 250,  starCost = 60,   durationDays = 7),
    Challenge("c_5day_habit",     "5-Day Habit",    "Work out 5 days in a row",                  Difficulty.AMATEUR, ChallengeType.STREAK,         5,   starReward = 300,  starCost = 75,   durationDays = 10),

    // MEDIUM  (cost ≈ 25% of reward)
    Challenge("c_session_star",    "Session Star",    "Complete a session with 75+ reps",        Difficulty.MEDIUM, ChallengeType.SINGLE_SESSION,  75,  starReward = 350,  starCost = 90,   durationDays = 14),
    Challenge("c_week_warrior",    "Week Warrior",    "Maintain a 7-day workout streak",         Difficulty.MEDIUM, ChallengeType.STREAK,           7,  starReward = 500,  starCost = 125,  durationDays = 14),
    Challenge("c_daily_fifty",     "Daily Fifty",     "50 push-ups per day for 5 days",          Difficulty.MEDIUM, ChallengeType.DAILY_REPS,      50,  targetDays = 5, starReward = 450, starCost = 110,  durationDays = 7),
    Challenge("c_500_sprint",      "500 Sprint",      "Accumulate 500 push-ups this week",       Difficulty.MEDIUM, ChallengeType.REPS_TOTAL,     500,  starReward = 500,  starCost = 125,  durationDays = 7),
    Challenge("c_double_down",     "Double Down",     "100 push-ups per day for 3 days",         Difficulty.MEDIUM, ChallengeType.DAILY_REPS,     100,  targetDays = 3, starReward = 600, starCost = 150,  durationDays = 5),
    Challenge("c_biweekly_grind",  "Bi-Weekly Grind", "Maintain a 14-day workout streak",       Difficulty.MEDIUM, ChallengeType.STREAK,          14,  starReward = 700,  starCost = 175,  durationDays = 21),
    Challenge("c_century_session", "Century Session", "Complete a session with 100+ reps",       Difficulty.MEDIUM, ChallengeType.SINGLE_SESSION, 100,  starReward = 400,  starCost = 100,  durationDays = 14),

    // ADVANCED  (cost ≈ 25% of reward)
    Challenge("c_iron_session",    "Iron Session",    "Complete a session with 200+ reps",       Difficulty.ADVANCED, ChallengeType.SINGLE_SESSION, 200, starReward = 1000, starCost = 250,  durationDays = 30),
    Challenge("c_iron_week",       "Iron Week",       "100 push-ups per day for 7 days",         Difficulty.ADVANCED, ChallengeType.DAILY_REPS,    100, targetDays = 7, starReward = 1000, starCost = 250,  durationDays = 10),
    Challenge("c_monthly_warrior", "Monthly Warrior", "30-day workout streak",                   Difficulty.ADVANCED, ChallengeType.STREAK,          30, starReward = 2000, starCost = 500,  durationDays = 40),
    Challenge("c_the_thousand",    "The Thousand",    "Accumulate 1,000 push-ups in 7 days",    Difficulty.ADVANCED, ChallengeType.REPS_TOTAL,    1000, starReward = 1500, starCost = 375,  durationDays = 7),
    Challenge("c_no_days_off",     "No Days Off",     "100 push-ups per day for 14 days",        Difficulty.ADVANCED, ChallengeType.DAILY_REPS,    100, targetDays = 14, starReward = 2000, starCost = 500,  durationDays = 18),
    Challenge("c_elite_session",   "Elite Session",   "Complete a session with 300+ reps",       Difficulty.ADVANCED, ChallengeType.SINGLE_SESSION, 300, starReward = 2000, starCost = 500,  durationDays = 30),
    Challenge("c_5k_month",        "5K Month",        "Accumulate 5,000 push-ups in 30 days",   Difficulty.ADVANCED, ChallengeType.REPS_TOTAL,    5000, starReward = 3000, starCost = 750,  durationDays = 30),
    Challenge("c_the_grinder",     "The Grinder",     "150 push-ups per day for 7 days",         Difficulty.ADVANCED, ChallengeType.DAILY_REPS,    150, targetDays = 7, starReward = 3000, starCost = 750,  durationDays = 10),
    Challenge("c_beast_mode",      "Beast Mode",      "Complete a session with 500+ reps",       Difficulty.ADVANCED, ChallengeType.SINGLE_SESSION, 500, starReward = 5000, starCost = 1250, durationDays = 60),
)

// ── Static achievement data ───────────────────────────────────────────────────

val AllAchievements: List<Achievement> = listOf(

    // ── Total-reps milestones ─────────────────────────────────────────────────
    Achievement("a_first_rep", "First Rep",     "Complete your very first push-up",  50,    AchievementCondition.TotalReps(1),      tag = "#Starter"),
    Achievement("a_100",       "Centurion",      "Reach 100 total push-ups",          100,   AchievementCondition.TotalReps(100),    tag = "#Centurion"),
    Achievement("a_500",       "Half Thousand",  "Reach 500 total push-ups",          200,   AchievementCondition.TotalReps(500),    tag = "#HalfK"),
    Achievement("a_1000",      "The Thousand",   "Reach 1,000 total push-ups",        500,   AchievementCondition.TotalReps(1000),   tag = "#Kilolifter"),
    Achievement("a_2500",      "2500 Club",      "Reach 2,500 total push-ups",        750,   AchievementCondition.TotalReps(2500),   tag = "#Grinder"),
    Achievement("a_5000",      "Five Thousand",  "Reach 5,000 total push-ups",        1000,  AchievementCondition.TotalReps(5000),   tag = "#FiveK"),
    Achievement("a_10000",     "Iron Man",       "Reach 10,000 total push-ups",       2500,  AchievementCondition.TotalReps(10000),  tag = "#IronMan"),
    Achievement("a_25000",     "Legend",         "Reach 25,000 total push-ups",       5000,  AchievementCondition.TotalReps(25000),  tag = "#Legend"),
    Achievement("a_50000",     "Immortal",       "Reach 50,000 total push-ups",       10000, AchievementCondition.TotalReps(50000),  tag = "#Immortal"),

    // ── Streak milestones (longestStreak) ─────────────────────────────────────
    Achievement("a_streak_3",   "On A Roll",       "Hit a 3-day streak",               100,   AchievementCondition.Streak(3),    tag = "#OnARoll"),
    Achievement("a_streak_7",   "Week Warrior",    "Hit a 7-day streak",               300,   AchievementCondition.Streak(7),    tag = "#WeekWarrior"),
    Achievement("a_streak_14",  "Iron Fortnight",  "Hit a 14-day streak",              600,   AchievementCondition.Streak(14),   tag = "#IronFortnight"),
    Achievement("a_streak_30",  "Monthly Grind",   "Hit a 30-day streak",              1500,  AchievementCondition.Streak(30),   tag = "#MonthlyGrind"),
    Achievement("a_streak_90",  "Quarterly Beast", "Hit a 90-day streak",              5000,  AchievementCondition.Streak(90),   tag = "#QuarterlyBeast"),
    Achievement("a_streak_180", "Half Year Hero",  "Hit a 180-day streak",             8000,  AchievementCondition.Streak(180),  tag = "#HalfYearHero"),
    Achievement("a_streak_365", "Year of Iron",    "Hit a 365-day streak",             15000, AchievementCondition.Streak(365),  tag = "#YearOfIron"),

    // ── Single-session personal-best ──────────────────────────────────────────
    Achievement("a_sess_10",   "Quick Start",       "10 reps in a single session",      50,   AchievementCondition.SingleSession(10),  tag = "#QuickStart"),
    Achievement("a_sess_50",   "Fifty Challenge",   "50 reps in a single session",      150,  AchievementCondition.SingleSession(50),  tag = "#FiftyReps"),
    Achievement("a_sess_100",  "Session Centurion", "100 reps in a single session",     400,  AchievementCondition.SingleSession(100), tag = "#SessionPro"),
    Achievement("a_sess_200",  "Destroyer",         "200 reps in a single session",     1000, AchievementCondition.SingleSession(200), tag = "#Destroyer"),
    Achievement("a_sess_500",  "Machine",           "500 reps in a single session",     3000, AchievementCondition.SingleSession(500), tag = "#Machine"),

    // ── Social ────────────────────────────────────────────────────────────────
    Achievement("a_friend_1",  "Social Butterfly",  "Add your first friend",            100,  AchievementCondition.FriendsCount(1),  tag = "#Social"),
    Achievement("a_friend_5",  "Squad Goals",       "Have 5 friends",                   250,  AchievementCondition.FriendsCount(5),  tag = "#SquadGoals"),
    Achievement("a_friend_10", "Community Leader",  "Have 10 friends",                  500,  AchievementCondition.FriendsCount(10), tag = "#Leader"),
)
