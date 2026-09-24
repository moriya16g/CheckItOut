# CheckItOut

> **Save the song you love the moment you love it.** Just tap your smartwatch.

[日本語版 README はこちら](README_ja.md)

---

Modern streaming makes it easy to listen — but hard to *remember*. Songs flow past, and by the time you think "I should save that," it's already the next track.

CheckItOut consists of a **Wear OS app** and a **phone app**. Tap your watch, and the phone captures what's playing from **any** music app (Spotify, YouTube Music, Apple Music, Amazon Music, etc.), saves it as a "like", and automatically records **Spotify / Apple Music track URLs**. The phone screen can stay off.

No accessibility permission is used. The only special permission required is **Notification access**.

## Key Features

### One Tap From Your Watch
- **Launch the Wear OS app** — sends a like to the phone instantly, then shows the track title and vibrates
- **Tile / complication** — one tap from a tile or the watch face
- **Hardware button** — most watches let you assign the app to a side-button double press

### Phone-Side Triggers
- **Quick Settings tile** — one tap from the notification shade
- **Home-screen widget** — "Like" / "Previous"
- **In-app buttons**

Phone-side triggers announce *"Added {track} to {playlist}"* via TTS.

### Works With Any Player
Uses Android's `MediaSessionManager` + `NotificationListenerService` to read structured metadata from whatever app is playing. No scraping, no hacks, and no foreground service.

### "Oops, the Song Changed" Protection
A ring buffer holds the last 10 tracks. Using the watch tap time, `RecentBuffer.bestCandidate()` checks whether the song *just* switched (< 3 seconds ago) and automatically picks the **previous** one. You can also go back manually with the widget / in-app "previous" action.

### Track URLs, No API Keys
After saving, a background WorkManager job resolves **Spotify** and **Apple Music** track URLs and stores them in the DB:

1. IDs exposed by the player's MediaSession (e.g. `spotify:track:…`, Apple Music catalog IDs)
2. [Odesli (song.link)](https://odesli.co/) to convert between services
3. [iTunes Search API](https://performance-partners.apple.com/search-api) title/artist matching

Tapping a link opens the Spotify / Apple Music app directly when installed (App Links / browser otherwise). Older rows without links are resolved and stored on first tap.

### Export
One-tap **CSV** or **Markdown** export with Spotify / Apple Music / Last.fm URLs in every row.

### Moment Capture Context
Each saved like can also preserve the surrounding moment: local time bucket, playback position, audio route (Bluetooth / wired / speaker), place label, weather, movement state, step count, Spotify audio-features, and a short lyrics snippet. Collection is **best-effort** and happens asynchronously after the like is saved, so the main action stays instant.

### Local Analytics
An on-device **Analytics** view turns your likes into something fun to browse: time-of-day bars, a day-hour heatmap, a mood quadrant (valence × energy), distribution donuts, top artists/places/apps, and automatically generated highlights such as peak listening time, weekend-vs-weekday energy shifts, and whether you tend to like songs early or near the chorus.

### Editable Logs & Selection Delete
Saved logs are editable from the in-app list: title/artist/album, Spotify / Apple Music URLs, and context fields (place, weather, activity, audio route, lyrics snippet, numeric values). Both the recent-playback buffer and the liked list support multi-select deletion.

### Cross-Device Sync
Pick or create a single `checkitout_sync.json` file in Google Drive, Dropbox, OneDrive, or another SAF-backed provider. CheckItOut reads and writes that file directly, which works even with providers that do not expose folder-tree selection. **WorkManager** automatically retries when offline — or hit the manual "Sync now" button.

### Every Like Is a Unique Moment
Liking the same song twice is intentional, not a bug. Each "like" is a separate log entry with its own timestamp and its own captured context.

## Architecture

```
┌──────────── Wear OS (:wear) ────────────┐
│ App launch / Tile / Complication        │
│            └─▶ PhoneClient               │
└──────────────┬───────────────▲──────────┘
   /checkitout/like            │ /checkitout/like/result
   (MessageClient)             │ (title / artist)
┌──────────────▼───────────────┴──────────── Phone (:mobile) ──┐
│ WearLikeListenerService                                       │
│ Quick Settings tile / Widget / In-app buttons                 │
│            │                                                  │
│            ▼                                                  │
│        LikeAction ◀── RecentBuffer ◀── MediaNotificationListener ◀── [Any music app]
│            │                                                  │
│            ├─▶ PlaylistSink ─▶ Room DB ──sync──▶ JSON file    │
│            ├─▶ LinkResolveWorker (Spotify / Apple Music URLs) │
│            └─▶ LikeContextCollector (place, weather, ...)     │
└───────────────────────────────────────────────────────────────┘
```

## Project Structure

| Module | Contents |
|---|---|
| `:mobile` | Phone app (minSdk 26) |
| `:wear` | Wear OS app (minSdk 30, not standalone) |
| `:shared` | Data Layer message paths and DTOs (`WearProtocol`) |

Both apps share the applicationId `net.sarotti.checkitout`. **The Wearable Data Layer only connects apps with the same applicationId and the same signing key**, so sign both APKs with the same key.

```
mobile/src/main/java/com/example/checkitout/
├── CheckItOutApp.kt              # Application, holds AppContainer, refreshes widget
├── action/
│   └── LikeAction.kt             # Central "like" entry point for all triggers
├── analytics/
│   └── LikeAnalytics.kt          # Local derived metrics + highlight generation
├── data/
│   ├── AppContainer.kt            # Manual DI container
│   ├── Database.kt                # Room entities, DAO, database (v6)
│   ├── LikeContext.kt             # Flat analytics-friendly context snapshot
│   ├── PlaylistSink.kt            # Sink interface + LocalDbSink
│   ├── RecentBuffer.kt            # Thread-safe ring buffer with grace period
│   ├── TrackInfo.kt               # In-memory track snapshot model
│   └── TriggerSource.kt           # APP / WIDGET / QS_TILE / WEAR
├── links/
│   ├── ITunesSearch.kt            # iTunes Search / Lookup API
│   ├── LinkResolveWorker.kt       # Post-save URL resolution worker
│   ├── LinkResolver.kt            # Key-free Spotify / Apple Music URL resolution
│   └── Odesli.kt                  # Cross-service conversion via song.link
├── service/
│   ├── LikeReceiver.kt            # Broadcast receiver for widget actions
│   ├── LikeTileService.kt         # Quick Settings tile
│   ├── MediaNotificationListener.kt  # Reads MediaSession from any player
│   └── WearLikeListenerService.kt # Receives like requests from the watch
├── sync/                          # SAF single-document JSON sync
├── ui/                            # Compose UI (home / analytics), permission helpers
├── util/                          # Export, HTTP, music links, TTS, context collectors
└── widget/
    └── LikeWidgetProvider.kt      # Home-screen widget

wear/src/main/java/net/sarotti/checkitout/wear/
├── MainActivity.kt                # Send on launch → show result → auto-finish
├── PhoneClient.kt                 # CapabilityClient + MessageClient
├── complication/LikeComplicationService.kt
└── tile/LikeTileService.kt

shared/src/main/java/net/sarotti/checkitout/shared/
└── WearProtocol.kt                # Paths, capability names, LikeRequest / LikeResult
```

## Setup

### Build

Open in Android Studio → Sync → Run, or from the command line:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

Outputs:
- `mobile/build/outputs/apk/debug/mobile-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

### Install

```powershell
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices
adb -s <phone serial> install -r mobile\build\outputs\apk\debug\mobile-debug.apk
adb -s <watch serial> install -r wear\build\outputs\apk\debug\wear-debug.apk
```

- Pair the watch with the phone first (Pixel Watch / Galaxy Wearable app, etc.).
- Watch ADB: Developer options → ADB debugging + Wireless debugging → `adb pair <IP>:<port>` → `adb connect <IP>:<port>`
- With two devices connected, `gradlew installDebug` installs to all of them; use `adb -s` per device instead.

> **Migrating from the old build (`com.example.checkitout`)**: the applicationId changed, so it is a separate app. Sync to the JSON file from the old app, choose "既存ファイルを選択" in the new app, then uninstall the old one.

### First Launch (permission required)

> **On Android 13+ sideloaded installs**, you may need to go to **Settings → Apps → CheckItOut → ⋮ → "Allow restricted settings"** before enabling Notification access.

1. **Notification access** — Settings → Notifications → Notification access → enable *CheckItOut*

The app shows guidance cards on launch if this permission is missing.

### Optional Permissions For Richer Context

- **Location** — enables place label and weather capture for each like.
- **Physical activity** — enables movement state and step count capture on Android 10+.

If you skip these, the core like flow still works. Those fields are just left null.

## Trigger Reference

| Trigger | Gesture | What it saves | Feedback |
|---|---|---|---|
| Wear OS app | Launch / tile / complication / hardware button | Current / smart-recent track | Title on watch + vibration |
| Quick Settings tile | Tap tile | Current / smart-recent track | TTS + vibration |
| Home-screen widget | Tap "👍" or "Previous" | Current or previous track | TTS + vibration |
| In-app buttons | Tap | Current or previous track | TTS + vibration |

## Debugging

```powershell
# Phone: receive / save / URL resolution logs
adb -s <phone> logcat -s LikeAction WearLikeListener MediaNL WM-WorkerWrapper
# Watch: app process only
adb -s <watch> logcat --pid=(adb -s <watch> shell pidof net.sarotti.checkitout)
# Launch the watch app (= send a like)
adb -s <watch> shell am start -n net.sarotti.checkitout/net.sarotti.checkitout.wear.MainActivity
# Nodes / capabilities (look for checkitout_phone / checkitout_wear)
adb -s <phone> shell dumpsys activity service com.google.android.gms/.wearable.service.WearableService
```

| Watch message | Likely cause |
|---|---|
| スマホに接続できません (cannot reach phone) | Phone app missing / signature mismatch / not paired |
| スマホから応答がありません (no response) | `WearLikeListenerService` not invoked |
| スマホで通知アクセスを許可してください | Notification access not granted |
| 再生中の曲が見つかりません (no track) | Nothing playing / MediaSession not detected |

Inspect saved rows with Android Studio **App Inspection → Database Inspector** (`liked_tracks`).

## Cross-Device Sync

1. In the app, tap **"新規ファイルを作成"** or **"既存ファイルを選択"**
2. In the picker, choose Google Drive / Dropbox / OneDrive or another document provider
3. Create or select `checkitout_sync.json`
4. Other devices pointing to the same file will merge automatically

| Aspect | Detail |
|---|---|
| Merge strategy | Union by `syncId` + last-write-wins updates by `updatedAt` (newer edit wins) |
| Storage model | Single JSON document selected through SAF (`CreateDocument` / `OpenDocument`) |
| Background sync | WorkManager, every 1 hour, requires network |
| Offline | Queued with exponential back-off; auto-retries on reconnect |
| Manual | "Sync now" button for immediate push/pull |

## Known Limitations

- No watch-side queue yet when the phone is unreachable (retry from the error screen).
- MediaSession ID formats are player-specific; when unavailable, resolution falls back to Odesli / iTunes search.
- Odesli allows roughly 10 requests/minute without a key; unresolved URLs are retried up to 2 times.
- Some DRM-heavy apps may withhold title/artist from MediaSession.

## Optional: Spotify audio-features

To enrich each like with BPM / energy / valence / danceability / key / loudness, register
an app at <https://developer.spotify.com/dashboard> and add the following to
`local.properties` at the project root (the file is git-ignored):

```
spotify.client.id=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
spotify.client.secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> Spotify apps created after November 2024 cannot access the audio-features API, and the client secret is embedded in the APK — keep this to personal builds. An alternative data source is under consideration.

Without these, the audio-features columns are simply left null. Track URL resolution does not need any Spotify key.

## Roadmap

- **v0.2 "Moment Capture"** ✅ — Time bucket, location, place label, weather, audio routing, activity, step count, audio-features, and lyrics snippet attached asynchronously
- **v0.3 "Wear"** ✅ — Wear OS trigger app, key-free Spotify / Apple Music URL resolution, dropped headset / foreground-service dependencies
- **Next** — Watch-side offline queue + persisted playback history, watch sensors (heart rate, steps) as context, audio-features alternative
- **v0.4 "Reflection"** — Weekly playlist generation, 30-day re-listen nudges
- **v0.5 "Artist Bond"** — Artist deep-dive screen, new release alerts, nearby concert notifications

## License

TBD
