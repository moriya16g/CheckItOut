package com.example.checkitout.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.action.LikeAction
import com.example.checkitout.data.LikedTrack
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.service.CaptureService
import com.example.checkitout.sync.SyncManager
import com.example.checkitout.sync.SyncWorker
import com.example.checkitout.util.Exporter
import com.example.checkitout.util.MusicLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored, UI re-renders on resume */ }

    val requestLocationPerm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result ignored, UI re-renders on resume */ }

    val requestActivityPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored, UI re-renders on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        CaptureService.ensureChannel(this)
        CaptureService.start(this)

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as CheckItOutApp
    val scope = rememberCoroutineScope()

    // Re-check permission flags on resume.
    var notifGranted by remember { mutableStateOf(Permissions.isNotificationListenerEnabled(context)) }
    var locationGranted by remember { mutableStateOf(Permissions.isLocationGranted(context)) }
    var activityGranted by remember { mutableStateOf(Permissions.isActivityRecognitionGranted(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = Permissions.isNotificationListenerEnabled(context)
                locationGranted = Permissions.isLocationGranted(context)
                activityGranted = Permissions.isActivityRecognitionGranted(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val recent by app.container.recentBuffer.state.collectAsState()
    val likedFlow: Flow<List<LikedTrack>> = remember { app.container.db.likedTrackDao().observeAll() }
    val liked by likedFlow.collectAsState(initial = emptyList())
    var recentSelectionMode by remember { mutableStateOf(false) }
    var likedSelectionMode by remember { mutableStateOf(false) }
    var selectedRecent by remember { mutableStateOf(setOf<TrackInfo>()) }
    var selectedLikedIds by remember { mutableStateOf(setOf<Long>()) }
    var tab by remember { mutableStateOf(0) } // 0=Home, 1=Analytics

    Scaffold(
        topBar = {
            androidx.compose.material3.TabRow(selectedTabIndex = tab) {
                androidx.compose.material3.Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("ホーム") },
                )
                androidx.compose.material3.Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("分析") },
                )
            }
        }
    ) { inner ->
        if (tab == 1) {
            val analytics = remember(liked) {
                com.example.checkitout.analytics.LikeAnalytics.compute(liked)
            }
            AnalyticsScreen(analytics, modifier = Modifier.padding(inner))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notifGranted) {
                item {
                    PermissionCard(
                        title = "⚠ まず「制限付き設定を許可」してください",
                        body = "サイドロード（Play Store 以外）でインストールした場合、" +
                                "先にアプリ情報画面の ⋮ →「制限付き設定を許可」が必要です。",
                        actionLabel = "アプリ情報を開く",
                        onAction = { Permissions.openAppDetailSettings(context) }
                    )
                }
                item {
                    PermissionCard(
                        title = "通知アクセスを許可してください",
                        body = "再生中のアプリ（Spotify / YouTube Music など）から曲情報を取得するために必要です。",
                        actionLabel = "通知アクセス設定を開く",
                        onAction = { Permissions.openNotificationListenerSettings(context) }
                    )
                }
            }

            // Optional context-capture permissions. App works without them; they only
            // enrich the analytical fields attached to each "like".
            if (!locationGranted) {
                item {
                    PermissionCard(
                        title = "（任意）位置情報を許可",
                        body = "「いいね」した瞬間の場所・天気を一緒に記録できるようになります。許可しなくても本体機能は使えます。",
                        actionLabel = "位置情報を許可する",
                        onAction = {
                            (context as? MainActivity)?.requestLocationPerm?.launch(
                                Permissions.locationPermissions
                            )
                        }
                    )
                }
            }
            if (!activityGranted) {
                item {
                    PermissionCard(
                        title = "（任意）身体活動を許可",
                        body = "歩数・移動状態（静止／歩行／ランニング／乗車）を記録します。Android 10 以降で必要。",
                        actionLabel = "身体活動を許可する",
                        onAction = {
                            (context as? MainActivity)?.requestActivityPerm?.launch(
                                Permissions.activityRecognitionPermission
                            )
                        }
                    )
                }
            }

            item {
                Column {
                    Button(
                        onClick = { LikeAction.trigger(context, 0) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("いま流れている曲を「いいね」") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { LikeAction.trigger(context, 1) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ひとつ前の曲を「いいね」") }
                }
            }

            item { SyncSection() }

            item {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("再生履歴バッファ（新しい順）", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (recentSelectionMode) {
                                if (selectedRecent.isNotEmpty()) {
                                    TextButton(onClick = {
                                        app.container.recentBuffer.removeAll(selectedRecent)
                                        selectedRecent = emptySet<TrackInfo>()
                                        recentSelectionMode = false
                                        toast(context, "再生履歴を削除しました")
                                    }) {
                                        Text("削除")
                                    }
                                }
                                TextButton(onClick = {
                                    selectedRecent = emptySet<TrackInfo>()
                                    recentSelectionMode = false
                                }) {
                                    Text("キャンセル")
                                }
                            } else if (recent.isNotEmpty()) {
                                TextButton(onClick = { recentSelectionMode = true }) {
                                    Text("選択")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (recent.isEmpty()) {
                        Text("まだ曲を検出していません。プレイヤーで再生を開始してください。")
                    } else {
                        recent.forEachIndexed { i, t ->
                            SelectableRecentRow(
                                index = i,
                                track = t,
                                selectionMode = recentSelectionMode,
                                selected = t in selectedRecent,
                                onSelectedChange = { checked ->
                                    selectedRecent = if (checked) selectedRecent + t else selectedRecent - t
                                }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("いいねした曲 (${liked.size})", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (likedSelectionMode) {
                                if (selectedLikedIds.isNotEmpty()) {
                                    TextButton(onClick = {
                                        val ids = selectedLikedIds.toList()
                                        scope.launch {
                                            app.container.db.likedTrackDao().deleteMany(ids)
                                            toast(context, "いいねを削除しました")
                                        }
                                        selectedLikedIds = emptySet<Long>()
                                        likedSelectionMode = false
                                    }) {
                                        Text("削除")
                                    }
                                }
                                TextButton(onClick = {
                                    selectedLikedIds = emptySet<Long>()
                                    likedSelectionMode = false
                                }) {
                                    Text("キャンセル")
                                }
                            } else if (liked.isNotEmpty()) {
                                TextButton(onClick = { likedSelectionMode = true }) {
                                    Text("選択")
                                }
                            }
                        }
                    }
                    ExportButtons(liked)
                }
            }

            items(liked, key = { it.id }) { row ->
                LikedRow(
                    row = row,
                    selectionMode = likedSelectionMode,
                    selected = row.id in selectedLikedIds,
                    onSelectedChange = { checked ->
                        selectedLikedIds = if (checked) {
                            selectedLikedIds + row.id
                        } else {
                            selectedLikedIds - row.id
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SelectableRecentRow(
    index: Int,
    track: com.example.checkitout.data.TrackInfo,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            }
            Column(Modifier.weight(1f)) {
                Text("${index}. ${track.displayName()}", style = MaterialTheme.typography.bodyLarge)
                Text(track.packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExportButtons(tracks: List<LikedTrack>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = Exporter.export(context, uri, tracks, Exporter.Format.CSV)
            toast(context, res.fold({ "CSV に ${it} 件を書き出しました" },
                { "エクスポート失敗: ${it.message}" }))
        }
    }
    val mdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = Exporter.export(context, uri, tracks, Exporter.Format.MARKDOWN)
            toast(context, res.fold({ "Markdown に ${it} 件を書き出しました" },
                { "エクスポート失敗: ${it.message}" }))
        }
    }

    val ts = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US) }
    Row {
        TextButton(
            onClick = { csvLauncher.launch("checkitout_${ts.format(Date())}.csv") },
            enabled = tracks.isNotEmpty()
        ) { Text("CSV") }
        TextButton(
            onClick = { mdLauncher.launch("checkitout_${ts.format(Date())}.md") },
            enabled = tracks.isNotEmpty()
        ) { Text("Markdown") }
    }
}

private fun toast(context: android.content.Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

@Composable
private fun SyncSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileUri by remember { mutableStateOf(SyncManager.getSavedFileUri(context)) }
    var syncing by remember { mutableStateOf(false) }
    val lastSync = remember(fileUri) { SyncManager.getLastSyncTime(context) }
    val df = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    val createPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        SyncManager.saveFileUri(context, uri)
        fileUri = uri
        // Initialize file with empty array if it's empty.
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write("[]".toByteArray())
            }
        }
        SyncWorker.enqueuePeriodicIfConfigured(context)
        toast(context, "同期ファイルを作成しました")
    }
    val openPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        SyncManager.saveFileUri(context, uri)
        fileUri = uri
        SyncWorker.enqueuePeriodicIfConfigured(context)
        toast(context, "既存の同期ファイルを設定しました")
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("端末間同期", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (fileUri != null) {
                Text(
                    "同期ファイル: ${fileUri?.lastPathSegment ?: "設定済み"}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (lastSync > 0) {
                    Text(
                        "最終同期: ${df.format(Date(lastSync))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !syncing,
                        onClick = {
                            syncing = true
                            scope.launch {
                                val result = runCatching { SyncManager.sync(context) }
                                syncing = false
                                result.fold(
                                    { toast(context, "同期完了: ${it}件インポート") },
                                    { toast(context, "同期失敗: ${it.message}") }
                                )
                            }
                        }
                    ) { Text(if (syncing) "同期中…" else "いま同期") }
                    TextButton(onClick = {
                        SyncManager.clearFileUri(context)
                        SyncWorker.cancelPeriodic(context)
                        fileUri = null
                        toast(context, "同期を解除しました")
                    }) { Text("解除") }
                }
            } else {
                Text(
                    "同期用 JSON ファイルを Google Drive / Dropbox / OneDrive 等の" +
                            "クラウド同期フォルダ内に作成すると、いいねリストが端末間で" +
                            "自動同期されます。\n" +
                            "（保存場所選択画面の左上 ☰ メニューからクラウドプロバイダを選べます）",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createPicker.launch(SyncManager.SYNC_FILE_NAME) }) {
                        Text("新規ファイルを作成")
                    }
                    OutlinedButton(onClick = { openPicker.launch(arrayOf("application/json", "*/*")) }) {
                        Text("既存ファイルを選択")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LikedRow(
    row: LikedTrack,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val df = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    var resolvingApple by remember(row.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            }
            Column(Modifier.weight(1f)) {
                val name = if (!row.artist.isNullOrBlank()) "${row.artist} - ${row.title}" else row.title
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${df.format(Date(row.likedAt))}  /  ${row.packageName}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { MusicLinks.open(context, MusicLinks.spotifySearchUrl(row)) }) {
                        Text("Spotify")
                    }
                    TextButton(
                        enabled = !resolvingApple,
                        onClick = {
                            resolvingApple = true
                            scope.launch {
                                val url = MusicLinks.resolveAppleMusic(row)
                                resolvingApple = false
                                if (url != null) MusicLinks.open(context, url)
                                else toast(context, "Apple Music で見つかりませんでした")
                            }
                        }
                    ) { Text(if (resolvingApple) "Apple Music…" else "Apple Music") }
                    TextButton(onClick = { MusicLinks.open(context, MusicLinks.lastFmUrl(row)) }) {
                        Text("Last.fm")
                    }
                }
            }
        }
    }
}
