<img width="1200" height="800" alt="rsmap" src="rsmap.png" />

# RSC Single Player — Mobile

RSC Single Player Mobile is a standalone RuneScape Classic reproduction and sandbox for Android: a fully self-contained app that runs locally with no server, internet connection, or database required. It is aimed at preservation, experimentation, and nostalgic single‑player exploration of (nearly) the full original game content — on your phone or tablet.

---

## Table of Contents
- [Features](#features)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Touch Controls](#touch-controls)
- [Gameplay Notes](#gameplay-notes)
- [Commands & Utilities](#commands--utilities)
- [Hardcore Mode](#hardcore-mode)
- [Experience Rates](#experience-rates)
- [Skill Batching System](#skill-batching-system)
- [Building from Source](#building-from-source)
- [Saving & Data](#saving--data)
- [Administrator Account](#administrator-account)
- [FAQ](#faq)
- [Disclaimer](#disclaimer)

---

## Features

Core:
- 100% single-process design — no external DB, server, or internet required
- All core content including all 50 quests
- Batched skill actions (configurable logic per skill/tool)
- Dynamic login screen
- Multi-account capable (see notes below)
- If you want to be not hardcore make sure to actually click on new account and make sure to not check hardcore
- If you bypass the create account and just login it may not do what you want

Quality of Life:
- Bank accessible anywhere via `::bank`
- Teleport utility via `::tele <area>`
- Item swapping in bank via right-click (long press)
- Flexible experience modifier (1x–50x)
- Hardcore mode (save deletion on death; see below)

Mobile Controls:
- **Tap** — left click
- **Long press** — right click
- **Double tap** — toggle keyboard
- **Pinch zoom** — zoom camera in/out
- **Two-finger drag** — rotate camera

Recent Shop Update:
- Bob's Axes now stocks ONLY woodcutting hatchets: Bronze, Iron, Steel, Rune (Mithril/Adamant deliberately omitted)
- Bob's also stocks Bronze through Rune pickaxes
- Battle axes intentionally excluded from Bob's inventory

---

## Requirements
- Android 7.0+ (API 24)
- ~6 MB storage

---

## Quick Start
1. Download `runescape-classic.apk` from the [Releases](https://github.com/theKennethy/SingleRSC-Mobile/releases) page.
2. Install the APK (you may need to enable "Install from unknown sources" in your device settings).
3. Launch the app.
4. Click `New User` and create an account.
5. Log in and play.

Tip: To use administrator shortcuts, create a user named exactly `root` (case sensitive).

---

## Touch Controls

| Gesture | Action |
|---------|--------|
| **Tap** | Left click (walk, interact, select) |
| **Long press** | Right click (context menu, examine) |
| **Double tap** | Toggle on-screen keyboard |
| **Pinch out** | Zoom in (camera closer) |
| **Pinch in** | Zoom out (camera farther) |
| **Two-finger drag** | Rotate camera left/right |
| **Single-finger drag** | Move mouse cursor |

Chat commands are typed using the on-screen keyboard — double-tap anywhere to bring it up.

---

## Gameplay Notes
- You may create multiple accounts, but avoid actively playing more than one simultaneously in the same session.
- Woodcutting uses the hatchet items; battle axes are purely combat weapons (and not sold in Bob's shop).
- Hardcore mode can be toggled; death in Hardcore will delete the save and close the app.

---

## Commands & Utilities
| Command | Description |
|---------|-------------|
| `::bank` | Opens the bank interface anywhere. |
| `::tele <area>` | Teleports to a predefined area alias. |
| (Admin/root) Mini-map right-click | Teleport utility for quick navigation. |

---

## Player Automation

Player-controlled botting has been removed. The Android app does not register commands that automate combat, gathering, production, banking, or movement for your character.

---

## Hardcore Mode
- Enable/toggle via in-game option (once enabled, death enforcement applies).
- On death: character save file is removed; app shuts down.
- Logging back in starts a fresh profile.

---

## Experience Rates
- Global modifier: 1x–50x, saved per character.

---

## Skill Batching System
The batching system centralizes repeated actions (e.g., woodcutting swings, mining strikes, cooking batches) using a `BatchEvent` scheduler.

Pattern (simplified):
```java
player.setBatchEvent(new BatchEvent(player, delayMs, repeatCount) {
    @Override
    public void action() {
        // Perform one attempt
        // Stop early with interrupt() if resource depleted, inv full, fatigue maxed, etc.
    }
});
```

---

## Building from Source

1. Clone:
   ```bash
   git clone https://github.com/theKennethy/SingleRSC-Mobile.git
   cd SingleRSC-Mobile
   ```
2. Requirements:
   - Android SDK (API 34, Build Tools 34.0.0)
   - JDK 11+
3. Build:
   ```bash
   export ANDROID_SDK_ROOT=/path/to/android-sdk
   ./gradlew assembleRelease
   ```
4. APK output: `app/build/outputs/apk/release/runescape-classic.apk`

---

## Saving & Data
- Saves are stored in the app's internal storage on your device.
- Uninstalling the app will delete save data — back up if needed.
- Hardcore deletion events are irreversible unless you manually back up the save file beforehand.

---

## Administrator Account
- Create user: `root`
- Grants admin privileges:
  - Mini-map right-click teleport
  - Additional internal debug or management hooks

---

## FAQ

**Q: Does this connect to any external server?**
A: No. Everything runs locally on your device.

**Q: How do I type commands?**
A: Double-tap anywhere to open the keyboard, then type your command.

**Q: Are battle axes usable for Woodcutting?**
A: No, only hatchets (Bronze/Iron/Steel/Rune in current Bob's inventory).

**Q: How do I right-click on mobile?**
A: Long press (hold your finger down for about a second).

**Q: How do I zoom?**
A: Pinch with two fingers — spread apart to zoom in, pinch together to zoom out.

**Q: How do I rotate the camera?**
A: Drag left/right with two fingers.

**Q: How do I restore a Hardcore character after death?**
A: Intended behavior is permanent loss; only manual file backups would bypass it.

**Q: Do bot scripts work on mobile?**
A: Player-controlled bot scripts have been removed. Normal gameplay remains manual.

---

## Disclaimer
This project is a preservation / educational single-player reimplementation. All original game assets, names, and concepts belong to their respective owners. Use responsibly and in accordance with applicable laws and terms.

---

## License
GPL v3.0 (see LICENSE or https://www.gnu.org/licenses/gpl-3.0.txt)
