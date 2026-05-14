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
import com.example.checkitout.service.CaptureService
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

    // Re-check permission flags on resume.
    var notifGranted by remember { mutableStateOf(Permissions.isNotificationListenerEnabled(context)) }
    var a11yGranted by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = Permissions.isNotificationListenerEnabled(context)
                a11yGranted = Permissions.isAccessibilityEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val recent by app.container.recentBuffer.state.collectAsState()
    val likedFlow: Flow<List<LikedTrack>> = remember { app.container.db.likedTrackDao().observeAll() }
    val liked by likedFlow.collectAsState(initial = emptyList())

    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (!notifGranted || !a11yGranted) {
                PermissionCard(
                    title = "⚠ まず「制限付き設定を許可」してください",
                    body = "サイドロード（Play Store 以外）でインストールした場合、" +
                            "先にアプリ情報画面の ⋮ →「制限付き設定を許可」が必要です。",
                    actionLabel = "アプリ情報を開く",
                    onAction = { Permissions.openAppDetailSettings(context) }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (!notifGranted) {
                PermissionCard(
                    title = "通知アクセスを許可してください",
                    body = "再生中のアプリ（Spotify / YouTube Music など）から曲情報を取得するために必要です。",
                    actionLabel = "通知アクセス設定を開く",
                    onAction = { Permissions.openNotificationListenerSettings(context) }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (!a11yGranted) {
                PermissionCard(
                    title = "ユーザー補助を有効にしてください",
                    body = "画面オフのまま音量ボタン長押しで「いいね」を保存するために使います。",
                    actionLabel = "ユーザー補助設定を開く",
                    onAction = { Permissions.openAccessibilitySettings(context) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { LikeAction.trigger(context, 0) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("いま流れている曲を「いいね」") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { LikeAction.trigger(context, 1) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ひとつ前の曲を「いいね」") }

            Spacer(Modifier.height(16.dp))
            Text("再生履歴バッファ（新しい順）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (recent.isEmpty()) {
                Text("まだ曲を検出していません。プレイヤーで再生を開始してください。")
            } else {
                recent.forEachIndexed { i, t ->
                    Text("${i}. ${t.displayName()}  (${t.packageName})")
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("いいねした曲 (${liked.size})", style = MaterialTheme.typography.titleMedium)
                ExportButtons(liked)
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(liked, key = { it.id }) { row ->
                    LikedRow(row)
                }
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
private fun LikedRow(row: LikedTrack) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val df = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    var resolvingApple by remember(row.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
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
