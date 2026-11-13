# PushUp Royale — Project Description

## Concept

PushUp Royale is a native Android fitness app that turns the humble push-up into a competitive, game-like experience. The front camera detects and counts reps in real time using on-device AI, while a progression system built around stars, ranks, achievements, and weekly challenges keeps users coming back. Friends can compete on shared leaderboards, send challenge notifications, and track each other's daily activity.

The core philosophy is simple: make exercise feel like a game worth winning.

---

## Target Platform

| Property | Value |
|---|---|
| Platform | Android |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Version | 1.1.3 (versionCode 7) |

---

## Technical Stack

### On-Device AI — Rep Detection
- **MediaPipe Pose Landmarker** runs on each CameraX `ImageAnalysis` frame
- Elbow angle (shoulder → elbow → wrist landmarks) + nose-to-floor Y-ratio drive a state machine: UP (angle > 150°) → DOWN (angle < 80°, nose near floor) → COUNT on transition back to UP
- 300 ms debounce prevents double-counting
- Auto-calibration on first frame: the user's standing posture sets the baseline, so reps are relative to body size and camera distance
- Bad-form detection: hip misalignment from the shoulder-ankle axis triggers a warning overlay (minimum 2 s visible)
- Phone motion detection freezes counting when the device is moved (accelerometer-based)
- Target latency: < 100 ms per rep detection

### Camera
- **CameraX** `ImageAnalysis` + `Preview` use case, front-facing camera
- Live skeleton overlay drawn on a `Canvas` composable over the preview
- Supports 6 push-up variants: Standard, Wide, Diamond, Archer, Pike, Decline

### Backend
- **Firebase Auth** — Google Sign-In + email/password
- **Firestore** — user profiles, sessions, daily logs, challenges, friend graphs, leaderboard data
- **Cloud Functions** — triggered on session write to update aggregates and award stars
- **FCM** — push notifications for friend challenges and weekly recap

### Local Storage
- **Room** — offline-first daily logs and session cache; synced to Firestore on reconnect

### Architecture
- **MVVM + Clean Architecture**: UI → ViewModel → UseCase → Repository
- `StateFlow` / `Flow` throughout; coroutines for all async work
- Hilt for dependency injection
- Single-Activity, all screens are Composable destinations in a NavGraph

### Build
- Gradle with Kotlin DSL
- Coil for image loading (avatars)
- UCrop for profile photo cropping
- DataStore for preferences (notifications, language, theme, changelog seen state)

---

## Features

### 1. Camera Workout Screen
The heart of the app. Full-screen CameraX preview with:
- Live MediaPipe skeleton overlay drawn in real time
- Large animated rep counter with bounce animation on each count
- Session timer and set tracker
- Start / Pause / End controls
- Post-session summary: total reps, duration, average pace, personal best badge if earned
- Audio feedback (beep) + haptic on each counted rep
- Bad-form warning banner with auto-dismiss timer

### 2. Daily Dashboard
- Circular progress ring toward the user's daily rep goal (configurable in profile)
- Streak counter with flame icon
- 7-day bar chart (reps per day, Mon–Sun)
- Friends ranking panel showing today's reps for each friend
- Rank progress card with the user's current tier emblem and XP bar
- Quick-start button directly to the camera screen

### 3. Star Progression System
- Every rep earns 1 star; consecutive-day streaks add a bonus (+1 per extra day)
- Combo multiplier: keep reps within 3 s of each other for ×2 at 20 reps, ×3 at 30, up to ×6 at 60+
- Inactivity penalty: −5 stars per missed day; additional −25 if a full week is missed
- Stars fund entry fees for weekly challenges and are used for leaderboard ranking

### 4. Rank System — 16 Tiers
Six warrior tiers, the first five split into I / II / III divisions:

| Tier | Color | Star Range |
|---|---|---|
| Rookie I–III | Clay-brown | 0 – 249 |
| Iron I–III | Mid-grey | 250 – 1 999 |
| Steel I–III | Bright silver | 2 000 – 5 999 |
| Titan I–III | Gold | 6 000 – 74 999 |
| Legend I–III | Amethyst | 75 000 – 199 999 |
| Immortal | Fire orange | 200 000+ |

Each tier has a unique **Canvas-drawn emblem**:
- **Rookie** — circular shield with inner gem
- **Iron** — wide flat diamond with facet highlight
- **Steel** — three nested hexagons at alternating rotations
- **Titan** — 6-point star with 12-ray sunburst
- **Legend** — layered crown with three gem dots
- **Immortal** — flaming orb with 8 spike rays and radial glow

Division pips (I / II / III) are drawn as small filled circles at the bottom of each emblem.

### 5. Challenges
**Weekly** (reset every Monday):
- *Century Club* — 100 reps in a single session
- *Consistency King* — work out 5 days this week
- *Speed Demon* — 30 reps in under 60 seconds

**Monthly**:
- *Iron Month* — 1 000 total reps this month
- *No Days Off* — 28-day streak

Each challenge shows: description, progress bar, star reward, time remaining, and an entry star cost.

### 6. Achievements — Clash Royale-Style Cards
Achievements are displayed as portrait game cards (108 × 148 dp) with:
- **Rarity tier** (Common / Rare / Epic / Legendary) based on star reward, each with its own gradient frame and glow colour
- **Canvas-drawn illustration** unique to the condition type:
  - *Total Reps* — fist with energy rings
  - *Streak* — 4-layer bezier flame (outer → core)
  - *Single Session* — glowing zigzag lightning bolt with sparkle dots
  - *Friends Count* — two overlapping heraldic shields
- Locked cards: greyscale art + hand-drawn padlock overlay
- Claimable cards: pulsing gold border + animated "Claim" chip
- Tapping opens a detail dialog with the full card art + condition text + star reward

Four condition types drive achievements:
- **TotalReps** — cumulative push-up milestones
- **Streak** — consecutive active days
- **SingleSession** — reps in one session
- **FriendsCount** — number of friends added

Unlocked achievements award tags (equippable cosmetic badges on the profile) and stars.

### 7. Social & Leaderboard
- Add friends by username search or shareable invite link
- Friends-only and global leaderboards
- Filters: today / this week / this month / all-time
- Profile cards: avatar, rank emblem, current streak, total reps
- Send a "Challenge" push notification to any friend
- Weekly Sunday recap notification showing rank among friends
- Outgoing friend request tracking (Pending state shown in search results)

### 8. Profile Screen
Hero card with:
- Avatar (letter initial fallback)
- Display name (editable inline)
- Stars pill, streak pill, rank emblem pill
- Rank progress card with emblem, tier name, progress bar, stars to next tier

Stats strip: total reps, personal best session, friends count, stars.

Sections below:
- Weekly activity bar chart
- Equipped tag badges (up to 3)
- Achievement card scroll row
- Recent sessions list with personal best markers

### 9. Notifications
- Daily reminder (configurable time)
- Streak-at-risk alert
- Weekly friend challenge recap (every Sunday)
- Friend request received
- Challenge completed
- All toggleable per-type in Settings

### 10. Settings & Admin
- Language: English / French
- Theme: Dark / Light / System
- Notification preferences per type
- Daily goal adjustment
- Account deletion (Google re-auth handled gracefully)
- Admin panel (visible only to admin UIDs): broadcast What's New, manage challenges

### 11. Onboarding
3-step carousel with Leo the mascot:
1. Camera permission request
2. Profile creation (username, height, weight, daily goal)
3. Add first friend (skippable)

Shown only after Google profile data is confirmed, not on first cold launch.

### 12. Home Screen Widget
Glance-based widget showing the friends leaderboard ranked by today's reps. Taps open the app directly to the Leaderboard tab.

---

## Design System

**Color palette (dark mode)**
| Token | Hex | Use |
|---|---|---|
| Background | `#080F08` | Page background |
| Surface Glass | `#0F1A0F` | Card surfaces |
| Accent Green | `#1DB954` | Primary actions, progress rings |
| Star / Gold-Green | `#C8D400` | Stars, PB badges, claim buttons |
| Error Red | `#EF4444` | Warnings, destructive actions |
| Warning Amber | `#F59E0B` | Streak, pace warnings |

**Typography**: bold sans-serif numbers for all counters; `MaterialTheme.typography` scale for text.

**Micro-animations**:
- Rep counter bounce on each counted rep (`AnimatedContent` slide transition)
- Streak flame pulse
- Achievement badge unlock pop
- Claimable achievement pulsing gold border (`InfiniteTransition`)
- Rank emblem pips drawn with per-division highlights

**Glassmorphism**: semi-transparent cards with green-tinted borders (`#331DB954`), no hard shadows.

---

## Data Models (Firestore)

```
users/{uid}
  username, displayName, photoUrl, level, xp, totalReps,
  currentStreak, longestStreak, friends: [uid],
  equippedTags: [String], dailyGoal: Int, height, weight

sessions/{sessionId}
  uid, date, reps, durationSeconds, avgPace, isPersonalBest,
  variant (Standard | Wide | Diamond | Archer | Pike | Decline)

dailyLogs/{uid_date}
  uid, date, totalReps, sessions: [sessionId]

challenges/{challengeId}
  type (Weekly | Monthly), title, description,
  xpReward, starCost, startDate, endDate

userChallenges/{uid_challengeId}
  uid, challengeId, progress, completed, completedAt

achievements/{achievementId}
  title, description, condition, starReward, tag

userAchievements/{uid_achievementId}
  uid, achievementId, unlockedAt, claimed
```

---

## File Structure (key modules)

```
app/src/main/kotlin/com/pushitup/
├── core/
│   ├── theme/          Theme.kt, PushItColors, StarColor, AccentGreen
│   ├── ui/             AchievementCard, RankEmblem, GemIcon, RanksDialog,
│   │                   PublicProfileDialog, UserAvatar, WhatsNewDialog,
│   │                   UpdateBanner, glassCard helpers
│   ├── navigation/     NavGraph, routes
│   ├── update/         AppVersionInfo (changelog), UpdatePrefs, UpdateWorker
│   └── onboarding/     TourOverlay, TourCoordinates, OnboardingScreen
├── domain/
│   ├── model/          UserProfile, WorkoutSession, DailyLog, Achievement,
│   │                   ChallengeModels, RankSystem, RankTier, FriendRankEntry
│   └── repository/     UserRepository interface
├── data/
│   ├── local/          Room DB, SessionDao, DailyLogDao
│   └── remote/         UserRepositoryImpl (Firestore), FirestoreMapper
├── feature/
│   ├── workout/        WorkoutScreen, WorkoutViewModel, PoseDetector,
│   │                   RepCounter, SkeletonOverlay, PostWorkoutCelebration
│   ├── dashboard/      DashboardScreen, DashboardViewModel
│   ├── profile/        ProfileScreen, ProfileViewModel
│   ├── leaderboard/    LeaderboardScreen, LeaderboardViewModel
│   ├── friends/        FriendsScreen, FriendsViewModel
│   ├── challenges/     ChallengesScreen, ChallengesViewModel
│   ├── auth/           LoginScreen, SignUpScreen, AuthViewModel
│   └── settings/       SettingsScreen, SettingsViewModel
└── widget/             FriendsRankingWidget (Glance)
```

---

## Current Version: 1.1.3

All core features are implemented and functional. Known open items:
- Leaderboard edge case: fewer than 3 players causes layout quirk (task #35)
- Star icon glyph (not gem shape) still used in DashboardScreen, ChallengesScreen, WorkoutScreen — cosmetically consistent due to theme-level gold-green color, but not yet updated to a custom shape
