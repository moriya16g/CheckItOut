# CheckItOut

> **Save the song you love the moment you love it.** No screen unlock needed.

[日本語版 README はこちら](README_ja.md)

---

Modern streaming makes it easy to listen — but hard to *remember*. Songs flow past, and by the time you think "I should save that," it's already the next track.

CheckItOut runs silently in the background, captures what's playing from **any** music app (Spotify, YouTube Music, Apple Music, Amazon Music, etc.), and lets you "like" a song **with a single physical button press** — even when your screen is off.

## Key Features

### Instant Capture, Zero Friction
- **Headset triple-click** — same, hands-free
- **Quick Settings tile** — one tap from the notification shade
- **Lock-screen notification buttons** — visible without unlock
- **Home-screen widget** — also works in lock-screen widget area (Android 16 beta)

### Works With Any Player
Uses Android's `MediaSessionManager` + `NotificationListenerService` to read structured metadata from whatever app is playing. No scraping, no hacks.

### "Oops, the Song Changed" Protection
A ring buffer holds the last 10 tracks. When you press the button, `RecentBuffer.bestCandidate()` checks whether the song *just* switched (< 3 seconds ago) and automatically picks the **previous** one. You can also manually go back with the lock-screen / widget "previous" action or the in-app "previous" button.

### Voice Feedback
TTS announces *"Added {track} to {playlist}"* so you never need to look at the screen.

### Music Service Links
Every saved track gets links to **Spotify**, **Apple Music**, and **Last.fm**. Tapping opens the respective app directly (via App Links).

### Export
One-tap **CSV** or **Markdown** export with Spotify / Apple Music / Last.fm URLs included in every row.

### Selection Delete
Both the recent-playback buffer and the liked-song list support multi-select deletion from the app UI.

### Cross-Device Sync
Pick or create a single `checkitout_sync.json` file in Google Drive, Dropbox, OneDrive, or another SAF-backed provider. CheckItOut reads and writes that file directly, which works even with providers that do not expose folder-tree selection. **WorkManager** automatically retries when offline — or hit the manual "Sync now" button.

### Every Like Is a Unique Moment
Liking the same song twice is intentional, not a bug. Each "like" is a separate log entry with its own timestamp (and, in future versions, location / weather / mood). The sync engine preserves every entry across devices — it never collapses duplicates.

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
  ├─ Headset triple-click  (MediaButtonReceiver)
  ├─ Quick Settings tile   (TileService)
  ├─ Lock-screen notif     (Notification action → LikeReceiver)
  ├─ Home-screen widget    (AppWidgetProvider → LikeReceiver)
  └─ In-app button         (Compose UI)
       │
       ▼
    LikeAction ──▶ PlaylistSink(s) ──▶ Room DB ──sync──▶ JSON file (single cloud/local document)
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
│   └── MediaNotificationListener.kt  # Reads MediaSession from any player
├── sync/
│   ├── SyncManager.kt             # SAF document-based JSON read/write + bidirectional merge
│   └── SyncWorker.kt              # WorkManager worker with offline retry
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

### First Launch (permission required)

> **On Android 13+ sideloaded installs**, you may need to go to **Settings → Apps → CheckItOut → ⋮ → "Allow restricted settings"** before enabling Notification access.

1. **Notification access** — Settings → Notifications → Notification access → enable *CheckItOut*

The app shows guidance cards on launch if this permission is missing.

## Trigger Reference

| Trigger | Gesture | What it saves |
|---|---|---|
| Headset button triple-click | 3 clicks within 0.9s | Current / smart-recent track |
| Quick Settings tile | Tap tile | Current / smart-recent track |
| Lock-screen notification | Tap "👍" or "Previous" | Current or previous track |
| Home-screen widget | Tap "👍" or "Previous" | Current or previous track |
| In-app buttons | Tap | Current or previous track |

### Cross-Device Sync

1. In the app, tap **"新規ファイルを作成"** or **"既存ファイルを選択"**
2. In the picker, choose Google Drive / Dropbox / OneDrive or another document provider
3. Create or select `checkitout_sync.json`
4. Other devices pointing to the same file will merge automatically

| Aspect | Detail |
|---|---|
| Merge strategy | Union by `syncId` (title + artist + ms-timestamp). Each like is unique |
| Storage model | Single JSON document selected through SAF (`CreateDocument` / `OpenDocument`) |
| Background sync | WorkManager, every 1 hour, requires network |
| Offline | Queued with exponential back-off; auto-retries on reconnect |
| Manual | "Sync now" button for immediate push/pull |

## Known Limitations

- Headset button may be intercepted by the music player; lock-screen notification actions and QS tile are the most reliable triggers.
- Some DRM-heavy apps may withhold title/artist from MediaSession.
- Different OEM lock-screen policies may change how prominently action buttons are shown.

## Roadmap

- **v0.2 "Moment Capture"** — Save location, weather, activity, and voice memo alongside each "like"
- **v0.3 "Reflection"** — Weekly playlist generation (Spotify write-back) + 30-day re-listen nudge
- **v0.4 "Artist Bond"** — Artist deep-dive screen, new release alerts, nearby concert notifications
- **v0.5 "Lyric Snapshot"** — Save the lyrics around the playback position at the moment of "like"

## License

TBD
