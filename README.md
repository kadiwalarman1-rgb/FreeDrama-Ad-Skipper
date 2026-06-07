# ⚡ FreeDrama Ad Skipper

<p align="center">
  <img src="https://img.shields.io/badge/Android-14%2B-brightgreen?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-blueviolet?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/AI-ML%20Kit%20OCR-orange?style=for-the-badge&logo=google" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" />
</p>

> **Auto-skips every ad in FreeDrama instantly.** Zero delay. No interaction needed. Works in background.

---

## ✨ Features

| Feature | Description |
|---|---|
| ⚡ **Zero-delay clicking** | Tap fires the instant a Skip Ad button is detected |
| 🤖 **ML Kit OCR** | On-device AI detects "Skip Ad", "Ad Skip", cross/X buttons |
| 🎯 **Top-right ROI** | Only analyzes the ad button area — ultra fast |
| 🪟 **Floating panel** | Draggable overlay control panel — Start / Pause / Stop |
| 🔄 **Background service** | Works even when FreeDrama is minimized |
| 📵 **No internet needed** | All detection is 100% on-device |
| 🔋 **Battery smart** | 15 FPS capture with frame skipping when busy |

---

## 📱 Requirements

- Android **14.0+** (API 34+)
- FreeDrama app installed
- 3 one-time permissions (guided setup)

---

## 🚀 Quick Start

### Option A — Download APK (Recommended)

1. Go to [**Releases**](../../releases/latest)
2. Download `FreeDramaAdSkipper-v1.0.0.apk`
3. Install on your Android device (enable "Install from unknown sources")
4. Follow the in-app setup wizard

### Option B — Build from Source

```bash
git clone https://github.com/YOUR_USERNAME/FreeDramaAdSkipper.git
cd FreeDramaAdSkipper
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 Setup (3 Steps, Done Once)

### Step 1 — Overlay Permission
Allows the floating control panel to appear on top of FreeDrama.

### Step 2 — Screen Capture
One-time system dialog to allow reading your screen for ad detection.

### Step 3 — Accessibility Service
Go to **Settings → Accessibility → FreeDrama Ad Skipper → Enable**

> ✅ After all 3 steps, tap **Launch** — the floating panel appears and you're done!

---

## 🎛️ Floating Control Panel

The panel floats on top of all apps. **Drag** it anywhere.

| Button | Action |
|---|---|
| ▶ | Resume detection |
| ⏸ | Pause (keeps panel visible) |
| ⏹ | **Fully stop** — removes panel & kills service |

---

## 🧠 How It Works

```
FreeDrama Screen
      ↓
MediaProjection (screen capture @ 15 FPS)
      ↓
Crop top-right 40% × 20% (ROI)
      ↓
ML Kit Text OCR (on-device)
      ↓
Match: "Skip Ad" / "Ad Skip" / "Skip" / "✕" / "×"
      ↓
AccessibilityService.dispatchGesture() → INSTANT TAP ⚡
```

---

## ⚙️ Customization

Edit [`SkipConfig.kt`](app/src/main/kotlin/com/freedrama/adskipper/SkipConfig.kt):

```kotlin
// Add more button text patterns
val SKIP_BUTTON_TEXTS = listOf(
    "skip ad",
    "ad skip",
    "skip",
    // Add your custom patterns here
)

// Adjust capture speed (FPS)
const val CAPTURE_FPS = 15

// Adjust click cooldown
const val CLICK_COOLDOWN_MS = 2000L
```

---

## ❓ FAQ

**Q: Does this work when FreeDrama is minimized?**  
A: Yes! The service runs as a foreground service and captures the screen continuously.

**Q: Will it drain my battery?**  
A: Minimal impact — detection runs at 15 FPS only on a small screen region, and pauses during cooldown.

**Q: FreeDrama updated and skipping stopped working?**  
A: Update the `SKIP_BUTTON_TEXTS` list in `SkipConfig.kt` and rebuild. No ML retraining needed.

**Q: Does it work on rooted devices?**  
A: Yes, and even better — on rooted devices it has a secondary `adb tap` fallback.

**Q: Can I use this on other streaming apps?**  
A: The OCR patterns are universal — you can configure it for any app's skip button text.

---

## 🛡️ Privacy

- **No data collected** — all processing is 100% on-device
- **No internet permission** — cannot send data anywhere
- **Screen capture is local-only** — pixels are processed in RAM and immediately discarded

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

## ⚠️ Disclaimer

This app is for personal use only. Not affiliated with FreeDrama. Using automation tools may violate app Terms of Service — use responsibly.
