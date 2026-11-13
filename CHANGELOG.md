# Changelog

## v1.1 (versionCode 3)

**Fixes**
- Google onboarding: username field is now editable (shared AuthViewModel across auth screens)
- Google onboarding: profile photo upload now works
- Burst counting (4–5 reps at once): added 700 ms debounce, 5-frame DOWN gate, and wider hysteresis

**New**
- UCrop circular photo picker on Google onboarding (matches Profile screen)
- Runtime detection tuning panel in Settings (debounce, DOWN frames, threshold margin)
- Distance tip on workout start screen ("place phone ~30 cm away")

**Build**
- Package renamed `com.pushit` → `com.pushitup`, versionCode 2 → 3, versionName 1.0 → 1.1
- `google-services.json` updated to match
- Unit tests updated to match current `noseY`-based detector API

---

## v1.0 (versionCode 2)
Initial internal release.
