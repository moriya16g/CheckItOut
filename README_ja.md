# CheckItOut

> **好きだと思った瞬間に、その曲を残す。** スマートウォッチをタップするだけ。

[English README](README.md)

---

サブスクで音楽を聴いていると、曲が次々と流れていき、「いいな」と思ったのにタイトルを思い出せない、ということがよくあります。

CheckItOut は **Wear OS アプリ** と **スマホアプリ** の 2 つで構成されます。ウォッチをタップすると、スマホが **どんな音楽アプリ**（Spotify, YouTube Music, Apple Music, Amazon Music など）で再生中の曲でも検出して「いいね」として保存し、**Spotify / Apple Music の曲 URL** も自動で記録します。スマホの画面はオフのままで構いません。

アクセシビリティ権限は使いません。必要な特別権限は **通知へのアクセス** のみです。

## 主な機能

### ウォッチからワンタップ
- **Wear OS アプリ起動** — 起動した瞬間にスマホへ「いいね」を送信し、曲名表示とバイブで結果を通知
- **タイル / コンプリケーション** — ウォッチフェイスやタイルから 1 タップ
- **物理ボタン割り当て** — 多くのウォッチでサイドボタンのダブルプレスにアプリを割り当て可能

### スマホ側のトリガー
- **クイック設定タイル** — 通知シェードからワンタップ
- **ホーム画面ウィジェット** — 「いいね」/「前の曲」
- **アプリ内ボタン**

スマホ側のトリガーでは TTS で *「○○ を ローカル保存 に追加しました」* と読み上げます。

### どのプレイヤーでも動く
Android の `MediaSessionManager` + `NotificationListenerService` を使い、再生中アプリの構造化メタデータを読み取ります。通知本文のスクレイピングではなく、正規 API による取得です。フォアグラウンドサービスは不要です。

### 「次の曲に移ってしまった」対策
直近 10 曲をリングバッファに保持。ウォッチをタップした時刻を基準に `RecentBuffer.bestCandidate()` が曲の切り替わりタイミングを確認し、切り替わりから 3 秒以内なら自動的に **ひとつ前の曲** を選択します。ウィジェットやアプリ内の「前の曲」で手動で遡ることも可能です。

### 曲 URL の自動取得（API キー不要）
保存後にバックグラウンド（WorkManager）で **Spotify** と **Apple Music** の曲 URL を解決し、DB に保存します。

1. プレイヤーの MediaSession が公開する ID（例: `spotify:track:…`、Apple Music のカタログ ID）
2. [Odesli (song.link)](https://odesli.co/) で既知のサービス URL を相互変換
3. [iTunes Search API](https://performance-partners.apple.com/search-api) によるタイトル / アーティスト照合

一覧のボタンをタップすると、インストール済みなら Spotify / Apple Music アプリで直接開きます（未インストール時は App Links / ブラウザ）。URL が未解決の古い行は、タップ時に解決して保存します。

### エクスポート
ワンタップで **CSV** または **Markdown** にエクスポート。各行に Spotify / Apple Music / Last.fm の URL を含みます。

### モーメントコンテキスト保存
各「いいね」には、その瞬間の文脈も保存できます。時間帯、再生位置、音声出力経路（Bluetooth / 有線 / スピーカー）、場所ラベル、天気、移動状態、歩数、Spotify の audio-features、短い歌詞スニペットを **best-effort** で収集します。これらは「いいね」保存後に非同期で付与されるため、メイン操作は即時のままです。

### ローカル分析画面
アプリ内の **分析** タブでは、保存した「いいね」をその場で楽しめる形に可視化します。時間帯ヒストグラム、曜日×時刻ヒートマップ、ムード象限（valence × energy）、分布ドーナツ、Top アーティスト / 場所 / アプリ、さらに「ピーク時間帯」「週末のほうが元気な曲が多いか」「イントロ即決派かサビまで聴く派か」といったハイライトを自動生成します。

### ログ編集・選択削除
保存済みログはアプリ内一覧から後編集できます。タイトル/アーティスト/アルバム、Spotify / Apple Music URL に加えて、場所・天気・活動・音声経路・歌詞スニペットなどのコンテキスト項目、数値項目も更新可能です。再生履歴バッファと「いいね」一覧は複数選択で削除できます。

### 端末間同期
Google Drive / Dropbox / OneDrive などの保存先に `checkitout_sync.json` を 1 つ作成または選択するだけです。CheckItOut はその JSON ファイルを直接読み書きするため、フォルダ選択に未対応のクラウドプロバイダでも使いやすくなっています。**WorkManager** がオフライン時に自動リトライ。手動の「いま同期」ボタンも用意しています。

### すべての「いいね」はユニークな瞬間
同じ曲を何度いいねしてもOK。各「いいね」はそれぞれ独立したログエントリとして、固有のタイムスタンプと、その瞬間のコンテキストを持って保持されます。

## アーキテクチャ

```
┌──────────── Wear OS (:wear) ────────────┐
│ アプリ起動 / タイル / コンプリケーション │
│            └─▶ PhoneClient               │
└──────────────┬───────────────▲──────────┘
   /checkitout/like            │ /checkitout/like/result
   (MessageClient)             │ (曲名・アーティスト)
┌──────────────▼───────────────┴──────────── スマホ (:mobile) ─┐
│ WearLikeListenerService                                       │
│ クイック設定タイル / ウィジェット / アプリ内ボタン            │
│            │                                                  │
│            ▼                                                  │
│        LikeAction ◀── RecentBuffer ◀── MediaNotificationListener ◀── [任意の音楽アプリ]
│            │                                                  │
│            ├─▶ PlaylistSink ─▶ Room DB ──sync──▶ JSON ファイル │
│            ├─▶ LinkResolveWorker (Spotify / Apple Music URL)  │
│            └─▶ LikeContextCollector (場所・天気・活動 …)      │
└───────────────────────────────────────────────────────────────┘
```

## プロジェクト構成

| モジュール | 内容 |
|---|---|
| `:mobile` | スマホアプリ（minSdk 26） |
| `:wear` | Wear OS アプリ（minSdk 30, スタンドアロン不可） |
| `:shared` | Data Layer のメッセージパス・DTO（`WearProtocol`） |

両アプリの applicationId は `net.sarotti.checkitout` で共通です。**Data Layer は同じ applicationId かつ同じ署名鍵のアプリ間でしか通信できない** ため、両 APK は同じ鍵で署名してください。

```
mobile/src/main/java/com/example/checkitout/
├── CheckItOutApp.kt              # Application、AppContainer 保持、ウィジェット更新
├── action/
│   └── LikeAction.kt             # 全トリガー共通の「いいね」エントリポイント
├── analytics/
│   └── LikeAnalytics.kt          # ローカル集計とハイライト生成
├── data/
│   ├── AppContainer.kt            # 手動 DI コンテナ
│   ├── Database.kt                # Room エンティティ、DAO、データベース（v6）
│   ├── LikeContext.kt             # 分析しやすいフラットなコンテキストスナップショット
│   ├── PlaylistSink.kt            # Sink インターフェース + LocalDbSink
│   ├── RecentBuffer.kt            # スレッドセーフなリングバッファ（猶予期間付き）
│   ├── TrackInfo.kt               # インメモリの曲スナップショットモデル
│   └── TriggerSource.kt           # APP / WIDGET / QS_TILE / WEAR
├── links/
│   ├── ITunesSearch.kt            # iTunes Search / Lookup API
│   ├── LinkResolveWorker.kt       # 保存後の URL 解決ワーカー
│   ├── LinkResolver.kt            # キー不要の Spotify / Apple Music URL 解決
│   └── Odesli.kt                  # song.link によるサービス間変換
├── service/
│   ├── LikeReceiver.kt            # ウィジェットアクション用ブロードキャストレシーバ
│   ├── LikeTileService.kt         # クイック設定タイル
│   ├── MediaNotificationListener.kt  # 任意プレイヤーの MediaSession を読み取り
│   └── WearLikeListenerService.kt # ウォッチからの「いいね」要求を受信
├── sync/                          # SAF 単一ドキュメント方式の JSON 同期
├── ui/                            # Compose UI（ホーム / 分析）、権限ヘルパー
├── util/                          # エクスポート、HTTP、音楽リンク、TTS、コンテキスト収集
└── widget/
    └── LikeWidgetProvider.kt      # ホーム画面ウィジェット

wear/src/main/java/net/sarotti/checkitout/wear/
├── MainActivity.kt                # 起動時に送信 → 結果表示 → 自動終了
├── PhoneClient.kt                 # CapabilityClient + MessageClient
├── complication/LikeComplicationService.kt
└── tile/LikeTileService.kt

shared/src/main/java/net/sarotti/checkitout/shared/
└── WearProtocol.kt                # パス・Capability 名・LikeRequest / LikeResult
```

## セットアップ

### ビルド

Android Studio で開く → Sync → Run、またはコマンドラインで:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

生成物:
- `mobile/build/outputs/apk/debug/mobile-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

### インストール

```powershell
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices
adb -s <スマホの serial> install -r mobile\build\outputs\apk\debug\mobile-debug.apk
adb -s <ウォッチの serial> install -r wear\build\outputs\apk\debug\wear-debug.apk
```

- ウォッチは事前にスマホとペアリング（Pixel Watch / Galaxy Wearable アプリ等）しておきます。
- ウォッチの ADB 接続: 開発者向けオプション → ADB デバッグ + ワイヤレスデバッグ → `adb pair <IP>:<port>` → `adb connect <IP>:<port>`
- 2 台接続時の `gradlew installDebug` は全端末に入れようとするため、`adb -s` で個別に入れるのが確実です。

> **旧版（`com.example.checkitout`）から移行する場合**: applicationId が変わったため別アプリ扱いです。旧版で同期 JSON に書き出し → 新版で「既存ファイルを選択」でデータを移し、旧版はアンインストールしてください。

### 初回起動（権限が必要）

> **Android 13 以降のサイドロードインストールの場合**、通知アクセス有効化前に **設定 → アプリ → CheckItOut → ⋮ →「制限付き設定を許可」** が必要な場合があります。

1. **通知へのアクセス** — 設定 → 通知 → 通知へのアクセス → *CheckItOut* を有効にする

アプリ起動時に未設定なら案内カードが表示されます。

### より豊かなコンテキストのための任意権限

- **位置情報** — 各「いいね」に場所ラベルと天気を付与します。
- **身体活動** — Android 10 以降で、移動状態と歩数を付与します。

許可しなくても、コアの「いいね」機能はそのまま使えます。未許可の項目だけ null のまま保存されます。

## トリガー一覧

| トリガー | 操作 | 保存対象 | フィードバック |
|---|---|---|---|
| Wear OS アプリ | 起動 / タイル / コンプリケーション / 物理ボタン | 現在 / スマート選択 | ウォッチに曲名 + バイブ |
| クイック設定タイル | タイルをタップ | 現在 / スマート選択 | TTS + バイブ |
| ホーム画面ウィジェット | 「👍」または「前の曲」 | 現在 / ひとつ前 | TTS + バイブ |
| アプリ内ボタン | タップ | 現在 / ひとつ前 | TTS + バイブ |

## デバッグ

```powershell
# スマホ: 受信・保存・URL 解決のログ
adb -s <phone> logcat -s LikeAction WearLikeListener MediaNL WM-WorkerWrapper
# ウォッチ: アプリのプロセスのみ
adb -s <watch> logcat --pid=(adb -s <watch> shell pidof net.sarotti.checkitout)
# ウォッチアプリを起動（=「いいね」送信）
adb -s <watch> shell am start -n net.sarotti.checkitout/net.sarotti.checkitout.wear.MainActivity
# ノード / Capability の確認（checkitout_phone / checkitout_wear が見えれば OK）
adb -s <phone> shell dumpsys activity service com.google.android.gms/.wearable.service.WearableService
```

| ウォッチの表示 | 主な原因 |
|---|---|
| スマホに接続できません | スマホ版未インストール / 署名不一致 / 未ペアリング |
| スマホから応答がありません | `WearLikeListenerService` が呼ばれていない |
| スマホで通知アクセスを許可してください | 通知アクセス未許可 |
| 再生中の曲が見つかりません | 再生停止中 / MediaSession 未検出 |

保存内容は Android Studio の **App Inspection → Database Inspector**（`liked_tracks`）で確認できます。

## 端末間同期

1. アプリ内で **「新規ファイルを作成」** または **「既存ファイルを選択」** をタップ
2. ピッカーで Google Drive / Dropbox / OneDrive などの保存先を選ぶ
3. `checkitout_sync.json` を作成または選択する
4. 同じファイルを指す別の端末が自動的にマージ

| 項目 | 詳細 |
|---|---|
| マージ戦略 | `syncId` による和集合 + `updatedAt` による最終更新優先（後から編集した内容が優先） |
| 保存モデル | SAF の単一ドキュメント選択（`CreateDocument` / `OpenDocument`） |
| バックグラウンド同期 | WorkManager、1時間ごと、ネットワーク必須 |
| オフライン | 指数バックオフでキューイング。接続回復時に自動リトライ |
| 手動 | 「いま同期」ボタンで即時プッシュ/プル |

## 既知の制限

- スマホに接続できないときのウォッチ側キューイングは未実装です（失敗表示から再送）。
- MediaSession の ID 形式はプレイヤー依存です。取れない場合は Odesli / iTunes 検索にフォールバックします。
- Odesli はキーなしで約 10 リクエスト/分の制限があります。解決に失敗した URL は最大 2 回まで再試行します。
- DRM の厳しいアプリは MediaSession からタイトル/アーティストを出さないことがあります。

## 任意: Spotify audio-features

各「いいね」に BPM / energy / valence / danceability / key / loudness を付けたい場合は、
<https://developer.spotify.com/dashboard> でアプリを作成し、プロジェクトルートの
`local.properties`（git 管理外）に以下を追加してください。

```
spotify.client.id=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
spotify.client.secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> 2024 年 11 月以降に作成された Spotify アプリでは audio-features API が利用できません。また client secret が APK に埋め込まれるため、個人利用に限定してください。代替データソースは検討中です。

未設定でも動作します。その場合は Spotify 関連列だけ null になります。曲 URL の取得には Spotify のキーは不要です。

## ロードマップ

- **v0.2 "Moment Capture"** ✅ — 時間帯、位置、場所ラベル、天気、音声経路、活動状態、歩数、audio-features、歌詞スニペットを非同期で付与
- **v0.3 "Wear"** ✅ — Wear OS アプリからのトリガー、キー不要の Spotify / Apple Music URL 解決、ヘッドセット / フォアグラウンドサービス依存の廃止
- **次** — ウォッチ側オフラインキュー + 再生履歴の永続化、ウォッチのセンサー（心拍・歩数）をコンテキストに追加、audio-features の代替
- **v0.4 "Reflection"** — 週次プレイリスト生成、30 日後の再聴リマインド
- **v0.5 "Artist Bond"** — アーティスト深掘り画面・新譜アラート・近隣ライブ通知

## ライセンス

TBD
