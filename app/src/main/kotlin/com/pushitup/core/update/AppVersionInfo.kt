package com.pushitup.core.update

/**
 * Changelog entries bundled inside the APK.
 * Key   = versionCode (Int)
 * Value = VersionChangelog
 *
 * Add an entry here every time you release a new version.
 * The What's New dialog shows the entry whose key matches the current versionCode.
 */
object AppChangelog {

    data class VersionChangelog(
        val versionName : String,
        val sections    : List<Section>,
    )

    data class Section(
        val title : String,   // e.g. "New Features", "Bug Fixes", "Improvements"
        val items : List<String>,
    )

    val entries: Map<Int, VersionChangelog> = mapOf(

        6 to VersionChangelog(
            versionName = "1.1.2",
            sections = listOf(
                Section("New Features", listOf(
                    "Light / Dark / System theme toggle in Settings",
                    "Achievement detail dialog — tap any badge to see how to unlock it",
                )),
                Section("Improvements", listOf(
                    "Star colour now adapts to light mode for better readability",
                    "Profile hero gradient uses full rank colour at top",
                    "\"Continue with Google\" button is now green with white text",
                    "Info bubbles on Profile use a green background in light mode",
                )),
                Section("Bug Fixes", listOf(
                    "Sign-out and account deletion now always redirect to login",
                    "Leaderboard correctly handles fewer than 3 players",
                    "Workout warning banners stay visible for at least 2 seconds",
                )),
            ),
        ),

        7 to VersionChangelog(
            versionName = "1.1.3",
            sections = listOf(
                Section("New Features", listOf(
                    "Game-style rank emblems — unique Canvas badge per tier (Rookie → Immortal)",
                    "Clash Royale-style achievement cards with illustrated art per condition type",
                    "Achievement detail dialog now shows the full card illustration",
                    "Rank emblems visible everywhere: Dashboard, Leaderboard, profile dialogs",
                )),
                Section("Improvements", listOf(
                    "Star colour updated to lime-gold — gold with a green tone",
                    "Rookie, Iron, Steel tiers now have clearly distinct colours",
                    "Removed blue from all rank and achievement colours",
                    "Leaderboard tier chips show the rank emblem alongside the name",
                )),
            ),
        ),
    )
}
