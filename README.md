<p align="center">
  <img src="pushit.png" alt="PushUp Royale logo" width="140"/>
</p>

<h1 align="center">PushUp Royale</h1>

<p align="center">
  <b>Turn push-ups into a game worth winning.</b><br/>
  Your front camera counts your reps in real time — on-device AI, no wearables, no honor system.
</p>

<p align="center">
  <code>Kotlin</code> · <code>Jetpack Compose</code> · <code>MediaPipe</code> · <code>CameraX</code> · <code>Firebase</code> · <code>Room</code><br/>
  minSdk 26 · targetSdk 35 · v1.1.3
</p>

---

## What it does

PushUp Royale is a native Android fitness game. Prop your phone against the wall, drop into position, and the front camera detects and counts every push-up live using on-device pose estimation. A progression system built around stars, 16 ranks, achievements, and challenges keeps you coming back — and shared leaderboards let you compete with friends.

## Features

**📸 AI Rep Counting** — MediaPipe Pose Landmarker runs on every CameraX frame. A state machine tracks your elbow angle and nose-to-floor distance: UP (angle > 150°) → DOWN (angle < 80°, nose near floor) → rep counted on the way back up, with a 300 ms debounce against double counts. Auto-calibration on the first frame adapts to your body size and camera distance. Supports 6 variants: Standard, Wide, Diamond, Archer, Pike, Decline.

**🦴 Live Skeleton Overlay** — your detected pose is drawn over the camera preview in real time; bad form (hips sagging off the shoulder–ankle axis) triggers a gentle warning, and moving the phone freezes counting so you can't cheat by shaking it.

**⭐ Star Economy** — 1 star per rep, streak bonuses, and a combo multiplier (keep reps within 3 s: ×2 at 20 consecutive reps up to ×6 at 60+). Skipping days costs stars, so consistency matters as much as volume.

**🏆 16 Ranks** — six tiers, the first five split into I / II / III divisions, each with a unique Canvas-drawn emblem:

| Tier | Emblem | Stars |
|---|---|---|
| Recruit I–III | Circular shield | 0 – 249 |
| Soldier I–III | Hexagonal crest | 250 – 1 999 |
| Warrior I–III | Anvil diamond | 2 000 – 5 999 |
| Champion I–III | Sunburst star | 6 000 – 74 999 |
| Legend I–III | Layered crown | 75 000 – 199 999 |
| Immortal | Flaming orb | 200 000+ |

**🎯 Challenges** — rotating weekly and monthly challenges (e.g. *Century Club*: 100 reps in one session, *No Days Off*: 28-day streak) with star entry fees and rewards.

**🎖️ Achievements** — collectible card-style badges across milestones, streaks, single-session records, and social goals, with rarity tiers, unlock progress bars on locked cards, and equippable profile tags.

**👥 Social** — Google / email sign-in, add friends by username, global and friends-only leaderboards with an animated top-3 podium, public profile cards, and FCM push notifications for friend challenges and weekly recaps.

**📊 Dashboard** — daily goal ring, streak flame, 7-day bar chart, rank progress card, and a one-tap quick start into the camera.

**🧭 Guided onboarding** — an interactive mascot-led tour walks new users through every screen.

Works offline: sessions are stored locally in Room first and synced to Firestore when connectivity returns. Dark and light themes, English + French localization.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin (JVM 17), coroutines + Flow |
| UI | Jetpack Compose, Material 3, single-activity NavGraph |
| Pose detection | MediaPipe Pose Landmarker on CameraX `ImageAnalysis` |
| Backend | Firebase — Auth, Firestore, Cloud Functions, FCM |
| Local storage | Room (offline-first), DataStore (preferences) |
| DI | Hilt |
| Images | Coil (+ UCrop for avatar cropping) |
| Architecture | MVVM + Clean Architecture (UseCase / Repository) |

## Project structure

```
app/src/main/kotlin/com/pushitup/
├── core/            # theme, navigation, shared UI, onboarding tour, notifications, updates
├── data/            # Room (local) + Firestore (remote) repositories
├── domain/          # models, rank system, star/XP calculators, repository interfaces
└── feature/
    ├── workout/     # camera, pose analysis, rep state machine, skeleton overlay
    ├── dashboard/   # home screen
    ├── challenges/  # weekly & monthly challenges
    ├── leaderboard/ # podium + global/friends rankings
    ├── friends/     # friend graph & requests
    ├── profile/     # stats, achievements, body metrics
    ├── auth/        # login / sign-up / Google setup
    └── settings/    # preferences, account management
firebase/            # Firestore rules & indexes, Storage rules
functions/           # Cloud Functions (aggregates, notifications)
```

## Getting started

### Prerequisites

- Android Studio (latest stable) with JDK 17
- An Android device with a front camera (API 26+) — pose detection is unpleasant on emulators
- A Firebase project

### Setup

1. **Clone**

   ```bash
   git clone https://github.com/simoensm/pushit.git
   ```

2. **Firebase config** — this repo intentionally ships without credentials. Create a Firebase project, register an Android app with package `com.pushitup`, enable **Auth** (Google + email/password), **Firestore**, and **Cloud Messaging**, then download your own `google-services.json` into `app/`.

3. **Deploy backend rules & functions** (optional, needed for social features)

   ```bash
   firebase deploy --only firestore:rules,firestore:indexes,functions
   ```

4. **Build & run**

   ```bash
   ./gradlew installDebug
   ```

### Tests

Unit tests cover the business logic — the rep-detection state machine, rank thresholds, and XP calculation:

```bash
./gradlew test
```

## How rep detection works

```
          elbow angle > 150°
        ┌──────── UP ◄───────┐
        │                    │  ← rep counted here (+ debounce 300 ms)
        ▼                    │
  elbow angle < 80°          │
  AND nose near floor ── DOWN┘
```

Landmarks come from MediaPipe's 33-point pose model; angles are computed from shoulder–elbow–wrist triples on whichever side is more visible. Target detection latency is under 100 ms per rep.

## License

No license has been granted yet — all rights reserved. Open an issue if you'd like to use any part of this project.
