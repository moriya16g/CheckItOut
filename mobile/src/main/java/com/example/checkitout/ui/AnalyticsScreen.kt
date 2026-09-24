package com.example.checkitout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.example.checkitout.analytics.LikeAnalytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Self-contained analytics screen. Pure Compose Canvas — no chart library deps.
 */
@Composable
fun AnalyticsScreen(analytics: LikeAnalytics, modifier: Modifier = Modifier) {
    if (analytics.total == 0) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("いいねがまだありません。聴いて、押して、貯めましょう 🎵")
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OverviewCard(analytics) }
        if (analytics.highlights.isNotEmpty()) item { HighlightsCard(analytics.highlights) }
        if (analytics.hasContext) item { HourBarsCard(analytics) }
        if (analytics.hasContext) item { DowHourHeatmapCard(analytics) }
        if (analytics.hasMood) item { MoodQuadrantCard(analytics) }
        if (analytics.hasAudio) item { DonutCard("オーディオ経路", analytics.audioOutputCounts) }
        if (analytics.hasActivity) item { DonutCard("活動状態", analytics.activityCounts) }
        if (analytics.weatherCounts.isNotEmpty()) item { DonutCard("天気", analytics.weatherCounts) }
        if (analytics.timeBucketCounts.isNotEmpty()) item { BarListCard("時間帯", analytics.timeBucketCounts) }
        if (analytics.topArtists.isNotEmpty()) item { TopListCard("Top アーティスト", analytics.topArtists) }
        if (analytics.topPlaces.isNotEmpty()) item { TopListCard("Top 場所", analytics.topPlaces) }
        if (analytics.topApps.isNotEmpty()) item { TopListCard("Top アプリ", analytics.topApps) }
        if (analytics.avgBpm != null || analytics.avgEnergy != null) item { FeatureGaugesCard(analytics) }
    }
}

// ───────────────────── Overview ─────────────────────

@Composable
private fun OverviewCard(a: LikeAnalytics) {
    val df = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("サマリー", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatPill("合計", "${a.total}")
                StatPill("週ペース", "${a.perWeek.toInt()}")
                a.avgBpm?.let { StatPill("avg BPM", "${it.toInt()}") }
            }
            Spacer(Modifier.height(8.dp))
            if (a.firstAt != null && a.lastAt != null) {
                Text(
                    "${df.format(Date(a.firstAt))}  →  ${df.format(Date(a.lastAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────── Highlights ─────────────────────

@Composable
private fun HighlightsCard(items: List<LikeAnalytics.Highlight>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("ハイライト", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            items.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(h.emoji, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(h.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ───────────────────── Hour bars ─────────────────────

@Composable
private fun HourBarsCard(a: LikeAnalytics) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("時刻別の Like", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val maxV = (a.hourHist.maxOrNull() ?: 1).coerceAtLeast(1)
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val barW = size.width / 24f
                val accent = Color(0xFFB388FF)
                val accentDim = Color(0xFFE6DDFF)
                for (i in 0 until 24) {
                    val v = a.hourHist[i]
                    val h = if (v == 0) 0f else (v.toFloat() / maxV) * (size.height - 14f)
                    val left = i * barW + barW * 0.15f
                    val w = barW * 0.7f
                    drawRoundRect(
                        color = if (v > 0) accent else accentDim,
                        topLeft = Offset(left, size.height - h - 12f),
                        size = Size(w, h.coerceAtLeast(2f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18, 23).forEach {
                    Text("${it}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ───────────────────── DoW × Hour heatmap ─────────────────────

@Composable
private fun DowHourHeatmapCard(a: LikeAnalytics) {
    val days = listOf("月", "火", "水", "木", "金", "土", "日")
    val maxV = (a.dowHourGrid.maxOf { row -> row.maxOrNull() ?: 0 }).coerceAtLeast(1)
    val cold = Color(0xFF1E1B2E)
    val hot = Color(0xFFFF6F61)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("曜日 × 時刻ヒートマップ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            // Header row: hours
            Row(Modifier.fillMaxWidth().padding(start = 28.dp)) {
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val cellW = size.width / 24f
                    val cellH = size.height / 7f
                    for (d in 0 until 7) {
                        for (h in 0 until 24) {
                            val v = a.dowHourGrid[d][h]
                            val t = v.toFloat() / maxV
                            val color = lerpColor(cold, hot, t)
                            drawRoundRect(
                                color = if (v == 0) Color(0xFF2A2740).copy(alpha = 0.4f) else color,
                                topLeft = Offset(h * cellW + 1f, d * cellH + 1f),
                                size = Size(cellW - 2f, cellH - 2f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(start = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18, 23).forEach {
                    Text("${it}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Vertical day labels overlay
            Row {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.height(0.dp)) {}
            }
            // Render small day legend separately as a row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                days.forEachIndexed { i, d ->
                    Text("$d:${a.dowHist[i]}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = 1f,
    )
}

// ───────────────────── Mood quadrant ─────────────────────

@Composable
private fun MoodQuadrantCard(a: LikeAnalytics) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("ムード象限 (valence × energy)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val pad = 24f
                    val w = size.width - pad * 2
                    val h = size.height - pad * 2
                    // Background gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFFF6F61).copy(alpha = 0.18f), Color(0xFF6FB1FF).copy(alpha = 0.18f))
                        ),
                        topLeft = Offset(pad, pad), size = Size(w, h),
                    )
                    // Axes
                    drawLine(Color.Gray, Offset(pad, pad + h / 2), Offset(pad + w, pad + h / 2), 1.5f)
                    drawLine(Color.Gray, Offset(pad + w / 2, pad), Offset(pad + w / 2, pad + h), 1.5f)
                    // Points
                    a.moodPoints.forEach { p ->
                        val x = pad + p.valence * w
                        val y = pad + (1f - p.energy) * h // y-axis inverted
                        drawCircle(
                            color = Color(0xFFB388FF).copy(alpha = 0.65f),
                            radius = 5f,
                            center = Offset(x, y),
                        )
                    }
                    // Average marker
                    if (a.avgValence != null && a.avgEnergy != null) {
                        val x = pad + a.avgValence * w
                        val y = pad + (1f - a.avgEnergy) * h
                        drawCircle(Color(0xFFFFEB3B), radius = 10f, center = Offset(x, y))
                        drawCircle(
                            color = Color(0xFF000000),
                            radius = 10f,
                            center = Offset(x, y),
                            style = Stroke(2f),
                        )
                    }
                }
                // Quadrant labels
                Box(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("⚡ Angry/Aggressive", Modifier.align(Alignment.TopStart),
                        style = MaterialTheme.typography.labelSmall)
                    Text("☀️ Happy", Modifier.align(Alignment.TopEnd),
                        style = MaterialTheme.typography.labelSmall)
                    Text("🌧️ Sad", Modifier.align(Alignment.BottomStart),
                        style = MaterialTheme.typography.labelSmall)
                    Text("😌 Chill", Modifier.align(Alignment.BottomEnd),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            if (a.moodPoints.size < a.total) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "※ Spotify audio-features が取得できた ${a.moodPoints.size}/${a.total} 曲のみ表示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ───────────────────── Donut ─────────────────────

private val palette = listOf(
    Color(0xFFB388FF), Color(0xFF80DEEA), Color(0xFFFFAB91),
    Color(0xFFA5D6A7), Color(0xFFFFE082), Color(0xFFEF9A9A),
    Color(0xFF90CAF9), Color(0xFFCE93D8),
)

@Composable
private fun DonutCard(title: String, counts: Map<String, Int>) {
    val total = counts.values.sum().coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(140.dp)) {
                    var start = -90f
                    counts.entries.forEachIndexed { i, e ->
                        val sweep = e.value / total.toFloat() * 360f
                        drawArc(
                            color = palette[i % palette.size],
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 28f, cap = StrokeCap.Butt),
                        )
                        start += sweep
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    counts.entries.forEachIndexed { i, e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(palette[i % palette.size])
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${e.key.prettyBucket()}  ${e.value} (${e.value * 100 / total}%)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.prettyBucket(): String = when (this) {
    "morning" -> "朝"
    "afternoon" -> "昼"
    "evening" -> "夕"
    "night" -> "夜"
    "late_night" -> "深夜"
    "BLUETOOTH" -> "Bluetooth"
    "WIRED_HEADSET" -> "有線"
    "USB" -> "USB"
    "HDMI" -> "HDMI"
    "SPEAKER" -> "スピーカー"
    "STILL" -> "静止"
    "WALKING" -> "歩行"
    "RUNNING" -> "走行"
    "VEHICLE" -> "乗車"
    "clear" -> "晴"
    "mostly_clear" -> "ほぼ晴"
    "cloudy" -> "曇"
    "fog" -> "霧"
    "drizzle" -> "霧雨"
    "rain" -> "雨"
    "snow" -> "雪"
    "thunder" -> "雷"
    else -> this
}

// ───────────────────── Bar list / Top list ─────────────────────

@Composable
private fun BarListCard(title: String, counts: Map<String, Int>) {
    val total = counts.values.sum().coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            counts.entries.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(e.key.prettyBucket(), Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                    Box(
                        Modifier.weight(1f).height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(e.value / total.toFloat())
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette[i % palette.size])
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${e.value}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TopListCard(title: String, items: List<Pair<String, Int>>) {
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { i, (name, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${i + 1}.", Modifier.width(22.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Box(
                            Modifier.fillMaxWidth().height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(count / max.toFloat())
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(palette[i % palette.size])
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$count", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ───────────────────── Spotify feature gauges ─────────────────────

@Composable
private fun FeatureGaugesCard(a: LikeAnalytics) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Spotify オーディオ特徴量（平均）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Gauge("energy", a.avgEnergy)
            Gauge("valence (陽性度)", a.avgValence)
            Gauge("danceability", a.avgDanceability)
            a.avgBpm?.let {
                Spacer(Modifier.height(4.dp))
                Text("BPM 平均: ${it.toInt()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Gauge(label: String, value: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall)
        if (value == null) {
            Text("—", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(
                Modifier.weight(1f).height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFB388FF), Color(0xFFFF6F61))
                            )
                        )
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("${(value * 100).toInt()}", Modifier.width(36.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
