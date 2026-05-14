# CheckItOut

> **Save the song you love the moment you love it.** No screen unlock needed.

[日本語版 README はこちら](README_ja.md)

---

Modern streaming makes it easy to listen — but hard to *remember*. Songs flow past, and by the time you think "I should save that," it's already the next track.

CheckItOut runs silently in the background, captures what's playing from **any** music app (Spotify, YouTube Music, Apple Music, Amazon Music, etc.), and lets you "like" a song **with a single physical button press** — even when your screen is off.

## Key Features

### Instant Capture, Zero Friction
- **Volume-down long press** (0.7s) — save the current song, screen off
- **Volume key triple-press** — save the *previous* song (missed-it recovery)
- **Headset triple-click** — same, hands-free
- **Quick Settings tile** — one tap from the notification shade
- **Lock-screen notification buttons** — visible without unlock
- **Home-screen widget** — also works in lock-screen widget area (Android 16 beta)

### Works With Any Player
Uses Android's `MediaSessionManager` + `NotificationListenerService` to read structured metadata from whatever app is playing. No scraping, no hacks.

### "Oops, the Song Changed" Protection
A ring buffer holds the last 10 tracks. When you press the button, `RecentBuffer.bestCandidate()` checks whether the song *just* switched (< 3 seconds ago) and automatically picks the **previous** one. You can also manually go back with triple-press or the in-app "previous" button.

### Voice Feedback
TTS announces *"Added {track} to {playlist}"* so you never need to look at the screen.

### Music Service Links
Every saved track gets links to **Spotify**, **Apple Music**, and **Last.fm**. Tapping opens the respective app directly (via App Links).

### Export
One-tap **CSV** or **Markdown** export with Spotify / Apple Music / Last.fm URLs included in every row.

### Extensible Sinks
`PlaylistSink` is an interface. The MVP ships with `LocalDbSink` (Room). Adding Spotify Web API write-back, YouTube Music, webhooks, etc. is a single-class addition.

## Architecture

```
[Any music app]
      │  MediaSession metadata
      ▼
MediaNotificationListener ──push──▶ RecentBuffer (last 10 tracks)
                                          ▲
                                          │
  ┌───────────────────────────────────────┘
  │
  ├─ Volume long-press     (AccessibilityService)
  ├─ Headset triple-click  (MediaButtonReceiver)
  ├─ Quick Settings tile   (TileService)
  ├─ Lock-screen notif     (Notification action → LikeReceiver)
  ├─ Home-screen widget    (AppWidgetProvider → LikeReceiver)
  └─ In-app button         (Compose UI)
       │
       ▼
  LikeAction ──▶ PlaylistSink(s) ──▶ Room DB
       │
       └──▶ TTS "Added ○○"
```

## Project Structure

```
app/src/main/java/com/example/checkitout/
├── CheckItOutApp.kt              # Application, holds AppContainer
├── action/
│   └── LikeAction.kt             # Central "like" entry point for all triggers
├── data/
│   ├── AppContainer.kt            # Manual DI container
│   ├── Database.kt                # Room entities, DAO, database
│   ├── PlaylistSink.kt            # Sink interface + LocalDbSink
│   ├── RecentBuffer.kt            # Thread-safe ring buffer with grace period
│   └── TrackInfo.kt               # In-memory track snapshot model
├── service/
│   ├── BootReceiver.kt            # Start CaptureService on boot
│   ├── CaptureService.kt          # Foreground service + lock-screen notification
│   ├── HeadsetButtonReceiver.kt   # Bluetooth/wired headset triple-click
│   ├── LikeReceiver.kt            # Broadcast receiver for notif/widget actions
│   ├── LikeTileService.kt         # Quick Settings tile
│   ├── MediaNotificationListener.kt  # Reads MediaSession from any player
│   └── VolumeKeyAccessibilityService.kt  # Volume long-press (screen off)
├── ui/
│   ├── MainActivity.kt            # Compose UI: permissions, buffer, liked list
│   └── Permissions.kt             # Permission check & navigation helpers
├── util/
│   ├── Exporter.kt                # CSV / Markdown export with music links
│   ├── MusicLinks.kt              # Spotify / Apple Music / Last.fm URL builders
│   └── Speaker.kt                 # TTS wrapper
└── widget/
    └── LikeWidgetProvider.kt      # Home-screen / lock-screen widget
```

## Setup

### Build

Open in Android Studio → Sync → Run. Min SDK 26 (Android 8.0).

### First Launch (two permissions required)

> **On Android 13+ sideloaded installs**, you must first go to **Settings → Apps → CheckItOut → ⋮ → "Allow restricted settings"** before enabling these.

1. **Notification access** — Settings → Notifications → Notification access → enable *CheckItOut*
2. **Accessibility service** — Settings → Accessibility → *CheckItOut Volume Trigger* → enable

The app shows guidance cards on launch if either permission is missing.

## Trigger Reference

| Trigger | Gesture | What it saves |
|---|---|---|
| Volume ↓ long-press (0.7s) | Hold volume-down | Current / smart-recent track |
| Volume key triple-press | 3 presses within 0.8s | Previous track |
| Headset button triple-click | 3 clicks within 0.9s | Current / smart-recent track |
| Quick Settings tile | Tap tile | Current / smart-recent track |
| Lock-screen notification | Tap "👍" or "Previous" | Current or previous track |
| Home-screen widget | Tap "👍" or "Previous" | Current or previous track |
| In-app buttons | Tap | Current or previous track |

## Known Limitations

- Headset button may be intercepted by the music player; volume long-press and QS tile are the most reliable triggers.
- Some DRM-heavy apps may withhold title/artist from MediaSession.
- On some devices, Doze mode may delay Accessibility Service key events. Exclude CheckItOut from battery optimization.

## Roadmap

- **v0.2 "Moment Capture"** — Save location, weather, activity, and voice memo alongside each "like"
- **v0.3 "Reflection"** — Weekly playlist generation (Spotify write-back) + 30-day re-listen nudge
- **v0.4 "Artist Bond"** — Artist deep-dive screen, new release alerts, nearby concert notifications
- **v0.5 "Lyric Snapshot"** — Save the lyrics around the playback position at the moment of "like"

## License

TBD
