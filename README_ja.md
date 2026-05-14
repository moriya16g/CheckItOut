# CheckItOut

> **好きだと思った瞬間に、その曲を残す。** 画面ロック解除は不要。

[English README](README.md)

---

サブスクで音楽を聴いていると、曲が次々と流れていき、「いいな」と思ったのにタイトルを思い出せない、ということがよくあります。

CheckItOut はバックグラウンドで静かに動作し、**どんな音楽アプリ**（Spotify, YouTube Music, Apple Music, Amazon Music など）で再生中の曲でも検出します。そして**物理ボタン 1 回の操作**で — 画面がオフのままでも — 曲を「いいね」として保存できます。

## 主な機能

### 瞬間キャプチャ、操作ゼロ
- **音量↓ 長押し** (0.7秒) — 現在の曲を保存（画面オフでOK）
- **音量キー 3連打** — *ひとつ前の曲* を保存（聴き逃し回復）
- **ヘッドセットボタン 3連打** — ハンズフリーで保存
- **クイック設定タイル** — 通知シェードからワンタップ
- **ロック画面の通知ボタン** — ロック解除なしで操作可能
- **ホーム画面ウィジェット** — Android 16 β のロック画面ウィジェットエリアにも対応

### どのプレイヤーでも動く
Android の `MediaSessionManager` + `NotificationListenerService` を使い、再生中アプリの構造化メタデータを読み取ります。通知本文のスクレイピングではなく、正規 API による取得です。

### 「次の曲に移ってしまった」対策
直近 10 曲をリングバッファに保持。ボタンを押した瞬間に `RecentBuffer.bestCandidate()` が曲の切り替わりタイミングを確認し、切り替わりから 3 秒以内なら自動的に **ひとつ前の曲** を選択します。さらに三連打やアプリ内「ひとつ前」ボタンで手動で遡ることも可能です。

### 音声フィードバック
TTS で *「○○ を ローカル保存 に追加しました」* と読み上げるので、画面を見る必要がありません。

### 音楽サービスへのリンク
保存した曲ごとに **Spotify**、**Apple Music**、**Last.fm** へのリンクを生成。タップすると各サービスのアプリが直接開きます（App Links 経由）。

### エクスポート
ワンタップで **CSV** または **Markdown** にエクスポート。各行に Spotify / Apple Music / Last.fm の URL を含みます。

### 端末間同期
Google Drive / Dropbox / OneDrive が同期しているフォルダを選ぶだけ。CheckItOut が JSON ファイルを書き出し、クラウドアプリが残りをやってくれます。**WorkManager** がオフライン時に自動リトライ。手動の「いま同期」ボタンも用意しています。

### すべての「いいね」はユニークな瞬間
同じ曲を何度いいねしてもOK。各「いいね」はそれぞれ独立したログエントリとして、固有のタイムスタンプ（将来的には位置・天気・気分も）と共に保持されます。同期エンジンは端末間でこれらをすべて保持し、重複として潰すことはありません。

### 拡張可能な保存先
`PlaylistSink` はインターフェースです。MVP では `LocalDbSink`（Room）のみですが、Spotify Web API 書き込みや YouTube Music 連携などを 1 クラス追加するだけで全トリガーから自動的に呼ばれます。

## アーキテクチャ

```
[任意の音楽アプリ]
      │  MediaSession メタデータ
      ▼
MediaNotificationListener ──push──▶ RecentBuffer (直近10曲)
                                          ▲
                                          │
  ┌───────────────────────────────────────┘
  │
  ├─ 音量長押し         (AccessibilityService)
  ├─ ヘッドセット3連打   (MediaButtonReceiver)
  ├─ クイック設定タイル   (TileService)
  ├─ ロック画面通知       (Notification action → LikeReceiver)
  ├─ ホーム画面ウィジェット (AppWidgetProvider → LikeReceiver)
  └─ アプリ内ボタン       (Compose UI)
       │
       ▼
    LikeAction ──▶ PlaylistSink(s) ──▶ Room DB ──sync──▶ JSON ファイル (クラウドフォルダ)
       │
       └──▶ TTS 「○○を追加しました」
```

## プロジェクト構成

```
app/src/main/java/com/example/checkitout/
├── CheckItOutApp.kt              # Application、AppContainer を保持
├── action/
│   └── LikeAction.kt             # 全トリガー共通の「いいね」エントリポイント
├── data/
│   ├── AppContainer.kt            # 手動 DI コンテナ
│   ├── Database.kt                # Room エンティティ、DAO、データベース
│   ├── PlaylistSink.kt            # Sink インターフェース + LocalDbSink
│   ├── RecentBuffer.kt            # スレッドセーフなリングバッファ（猶予期間付き）
│   └── TrackInfo.kt               # インメモリの曲スナップショットモデル
├── service/
│   ├── BootReceiver.kt            # 起動時に CaptureService を開始
│   ├── CaptureService.kt          # フォアグラウンドサービス + ロック画面通知
│   ├── HeadsetButtonReceiver.kt   # Bluetooth/有線ヘッドセットの三連打
│   ├── LikeReceiver.kt            # 通知/ウィジェットアクション用ブロードキャストレシーバ
│   ├── LikeTileService.kt         # クイック設定タイル
│   ├── MediaNotificationListener.kt  # 任意プレイヤーの MediaSession を読み取り
│   └── VolumeKeyAccessibilityService.kt  # 音量キー長押し（画面オフ対応）
├── sync/
│   ├── SyncManager.kt             # SAF ベースの JSON 読み書き + 双方向マージ
│   └── SyncWorker.kt              # WorkManager ワーカー（オフラインリトライ付き）
├── ui/
│   ├── MainActivity.kt            # Compose UI: 権限案内、バッファ表示、いいね一覧
│   └── Permissions.kt             # 権限チェック＆設定画面遷移ヘルパー
├── util/
│   ├── Exporter.kt                # CSV / Markdown エクスポート（音楽リンク付き）
│   ├── MusicLinks.kt              # Spotify / Apple Music / Last.fm URL 生成
│   └── Speaker.kt                 # TTS ラッパー
└── widget/
    └── LikeWidgetProvider.kt      # ホーム画面 / ロック画面ウィジェット
```

## セットアップ

### ビルド

Android Studio で開く → Sync → Run。最小 SDK 26（Android 8.0）。

### 初回起動（2 つの権限が必要）

> **Android 13 以降のサイドロードインストールの場合**、先に **設定 → アプリ → CheckItOut → ⋮ →「制限付き設定を許可」** が必要です。

1. **通知へのアクセス** — 設定 → 通知 → 通知へのアクセス → *CheckItOut* を有効にする
2. **ユーザー補助サービス** — 設定 → ユーザー補助 → *CheckItOut Volume Trigger* を有効にする

アプリ起動時に未設定の項目があれば案内カードが表示されます。

## トリガー一覧

| トリガー | 操作 | 保存対象 |
|---|---|---|
| 音量↓ 長押し (0.7秒) | 音量ダウンを長押し | 現在 / スマート選択 |
| 音量キー 3連打 | 0.8秒以内に3回押す | ひとつ前の曲 |
| ヘッドセットボタン 3連打 | 0.9秒以内に3回クリック | 現在 / スマート選択 |
| クイック設定タイル | タイルをタップ | 現在 / スマート選択 |
| ロック画面通知 | 「👍」または「前の曲」をタップ | 現在 / ひとつ前 |
| ホーム画面ウィジェット | 「👍」または「前の曲」をタップ | 現在 / ひとつ前 |
| アプリ内ボタン | タップ | 現在 / ひとつ前 |

## 端末間同期

1. アプリ内で **「同期フォルダを選択」** をタップ
2. Google Drive / Dropbox / OneDrive が管理するフォルダを選ぶ
3. CheckItOut が `checkitout_sync.json` をそのフォルダに作成
4. 同じフォルダを指す別の端末が自動的にマージ

| 項目 | 詳細 |
|---|---|
| マージ戦略 | `syncId`（タイトル＋アーティスト＋ミリ秒タイムスタンプ）による和集合。各いいねはユニーク |
| バックグラウンド同期 | WorkManager、1時間ごと、ネットワーク必須 |
| オフライン | 指数バックオフでキューイング。接続回復時に自動リトライ |
| 手動 | 「いま同期」ボタンで即時プッシュ/プル |

## 既知の制限

- ヘッドセットボタンは音楽プレイヤーに奪われることがあります。音量長押し・QS タイルが最も確実です。
- DRM の厳しいアプリは MediaSession からタイトル/アーティストを出さないことがあります。
- 一部端末では Doze モードで Accessibility Service の onKeyEvent が遅延します。電池の最適化対象から CheckItOut を除外してください。

## ロードマップ

- **v0.2 "Moment Capture"** — 「いいね」と同時に位置情報・天気・活動・音声メモを保存
- **v0.3 "Reflection"** — 週次プレイリスト自動生成（Spotify 書き込み対応）+ 30日後の再聴リマインド
- **v0.4 "Artist Bond"** — アーティスト深掘り画面・新譜アラート・近隣ライブ通知
- **v0.5 "Lyric Snapshot"** — 「いいね」した瞬間の再生位置付近の歌詞を保存

## ライセンス

TBD
