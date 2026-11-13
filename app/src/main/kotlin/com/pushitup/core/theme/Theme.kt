package com.pushitup.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// PushUp Royale game palette — "fitness tracker meets gaming HUD".
// Invariant colours — same value in every theme.
// Safe to use in non-composable helpers (Canvas draw lambdas, pure functions …).
// ─────────────────────────────────────────────────────────────────────────────

/** Primary brand — logo green. */
val AccentGreen       = Color(0xFF1DB954)
val AccentGreenDark   = Color(0xFF15803D)
val OnAccent         = Color.Black

/** Neon accent glows (secondary game accents). */
val NeonPurple       = Color(0xFFC200FF)
val HotPink          = Color(0xFFFF006E)

/** Semantic colours. */
val SuccessLime      = Color(0xFF39FF14)
val ErrorRed         = Color(0xFFEF4444)
val WarningAmber     = Color(0xFFF59E0B)

// Ranking-tier colours
val BronzeGold       = Color(0xFFCD7F32)
val SilverLight      = Color(0xFFC0C0C0)
val GoldYellow       = Color(0xFFFFD700)
val PlatinumCyan     = Color(0xFF00E5FF)
val DiamondPurple    = Color(0xFFB347FE)

/** Legendary gradient stops (HotPink → NeonPurple). */
val LegendaryGradient = listOf(HotPink, NeonPurple)

// ─────────────────────────────────────────────────────────────────────────────
// Theme-sensitive colour bundle
// ─────────────────────────────────────────────────────────────────────────────

data class PushItColors(
    val backgroundDeep  : Color,
    val surfaceGlass    : Color,
    val surfaceElevated : Color,
    val glassBorder     : Color,
    val textPrimary     : Color,
    val textSecondary   : Color,
    val textMuted       : Color,
    /** Star colour — gold in dark mode, dark gold in light mode. */
    val starColor       : Color,
    /** True when this is the dark bundle — lets composables make per-theme visual decisions. */
    val isDark          : Boolean,
)

private val darkPushItColors = PushItColors(
    backgroundDeep  = Color(0xFF080F08),   // deep green-black
    surfaceGlass    = Color(0xFF0F1A0F),   // glass card fill
    surfaceElevated = Color(0xFF172217),   // elevated green-tinted (chips, ring tracks)
    glassBorder     = Color(0x331DB954),   // 20% green border
    textPrimary     = Color(0xFFFFFFFF),
    textSecondary   = Color(0xFFB3B3B3),
    textMuted       = Color(0xFF535353),
    starColor       = Color(0xFFC8D400),   // lime-gold star — gold with green tone on dark backgrounds
    isDark          = true,
)

private val lightPushItColors = PushItColors(
    // Inversion of the dark pattern: light green page bg, WHITE card surfaces.
    // This gives clear card-on-background depth without extra shadow.
    backgroundDeep  = Color(0xFFF0F7F0),   // light green-tinted page background
    surfaceGlass    = Color(0xFFFFFFFF),   // white cards — clearly stand out from bg
    surfaceElevated = Color(0xFFD4EBD4),   // greenish elevated (ring track, chips, tags)
    glassBorder     = Color(0xBB1DB954),   // 73% opacity green border — clearly visible
    // Text: all values pass WCAG AA (≥4.5:1) on white and on the light bg.
    textPrimary     = Color(0xFF0A120A),   // near-black   (contrast ~18:1 on white)
    textSecondary   = Color(0xFF374151),   // dark slate    (contrast ~10:1 on white)
    textMuted       = Color(0xFF5E6E73),   // dark gray     (contrast  ~5:1 on white)
    starColor       = Color(0xFF767D00),   // dark olive-gold — readable on light backgrounds
    isDark          = false,
)

/** Current bundle — read inside any @Composable via LocalPushItColors.current */
val LocalPushItColors = staticCompositionLocalOf { darkPushItColors }

// ─────────────────────────────────────────────────────────────────────────────
// Backward-compatible @Composable property accessors
//
// All existing screens import these names from com.pushitup.core.theme.*.
// Switching to @Composable get() means every composable caller gets the right
// value for the current theme — zero changes needed in existing screen files.
//
// ⚠ These cannot be accessed inside non-composable lambdas (DrawScope, plain
//   functions).  For those cases, capture the colour BEFORE entering the lambda:
//       val bg = SurfaceElevated   ← in composable scope
//       Canvas { drawRect(bg) }    ← bg is captured, not re-read
// ─────────────────────────────────────────────────────────────────────────────

val BackgroundDeep: Color
    @Composable get() = LocalPushItColors.current.backgroundDeep

val SurfaceGlass: Color
    @Composable get() = LocalPushItColors.current.surfaceGlass

val SurfaceElevated: Color
    @Composable get() = LocalPushItColors.current.surfaceElevated

val GlassBorder: Color
    @Composable get() = LocalPushItColors.current.glassBorder

val TextPrimary: Color
    @Composable get() = LocalPushItColors.current.textPrimary

val TextSecondary: Color
    @Composable get() = LocalPushItColors.current.textSecondary

val TextMuted: Color
    @Composable get() = LocalPushItColors.current.textMuted

/** Star colour — gold (#FFD700) in dark mode, dark gold in light mode. */
val StarColor: Color
    @Composable get() = LocalPushItColors.current.starColor

/** True when the active theme is dark. Useful for per-theme visual tweaks. */
val IsAppDark: Boolean
    @Composable get() = LocalPushItColors.current.isDark

// ─────────────────────────────────────────────────────────────────────────────
// Material 3 colour schemes
// ─────────────────────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary          = AccentGreen,
    onPrimary        = Color.Black,
    primaryContainer = AccentGreenDark,
    secondary        = AccentGreen,
    background       = darkPushItColors.backgroundDeep,
    surface          = darkPushItColors.surfaceGlass,
    onBackground     = darkPushItColors.textPrimary,
    onSurface        = darkPushItColors.textPrimary,
    onSurfaceVariant = darkPushItColors.textSecondary,
    error            = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary             = AccentGreen,
    onPrimary           = Color.Black,
    primaryContainer    = Color(0xFFB9F6CA),
    onPrimaryContainer  = Color(0xFF002112),
    secondary           = AccentGreen,
    onSecondary         = Color.Black,
    secondaryContainer  = Color(0xFFD4EBD4),
    onSecondaryContainer = lightPushItColors.textPrimary,
    background          = lightPushItColors.backgroundDeep,
    onBackground        = lightPushItColors.textPrimary,
    surface             = lightPushItColors.surfaceGlass,
    onSurface           = lightPushItColors.textPrimary,
    surfaceVariant      = lightPushItColors.surfaceElevated,
    onSurfaceVariant    = lightPushItColors.textSecondary,
    outline             = Color(0xFF6B7280),
    outlineVariant      = lightPushItColors.glassBorder,
    error               = ErrorRed,
    onError             = Color.White,
)

// ─────────────────────────────────────────────────────────────────────────────
// Theme entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PushItTheme(
    darkTheme : Boolean = isSystemInDarkTheme(),
    content   : @Composable () -> Unit,
) {
    val pushItColors = if (darkTheme) darkPushItColors else lightPushItColors
    val colorScheme  = if (darkTheme) DarkColorScheme  else LightColorScheme

    CompositionLocalProvider(LocalPushItColors provides pushItColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = PushItTypography,
            content     = content,
        )
    }
}
