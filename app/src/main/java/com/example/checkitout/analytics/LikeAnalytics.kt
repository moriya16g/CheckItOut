package com.example.checkitout.analytics

import com.example.checkitout.data.LikedTrack
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure, side-effect-free analytics computed from the local "liked" rows.
 *
 * Designed to run in O(N) over the in-memory list. For typical "tens to a few
 * thousand" likes this is microseconds — no need for cached aggregations.
 */
data class LikeAnalytics(
    val total: Int,
    val firstAt: Long?,
    val lastAt: Long?,
    val perWeek: Float,
    /** 24-element histogram of likes by local hour-of-day. */
    val hourHist: IntArray,
    /** 7-element histogram, index 0=Monday .. 6=Sunday (ISO). */
    val dowHist: IntArray,
    /** 7×24 grid (rows=DoW, cols=hour). */
    val dowHourGrid: Array<IntArray>,
    val timeBucketCounts: LinkedHashMap<String, Int>,
    val audioOutputCounts: LinkedHashMap<String, Int>,
    val activityCounts: LinkedHashMap<String, Int>,
    val weatherCounts: LinkedHashMap<String, Int>,
    val topArtists: List<Pair<String, Int>>,
    val topApps: List<Pair<String, Int>>,
    val topPlaces: List<Pair<String, Int>>,
    val avgBpm: Float?,
    val avgEnergy: Float?,
    val avgValence: Float?,
    val avgDanceability: Float?,
    val avgPositionPct: Float?,
    /** Scatter points for valence (x) × energy (y), one per like that has both. */
    val moodPoints: List<MoodPoint>,
    val highlights: List<Highlight>,
) {
    data class MoodPoint(val valence: Float, val energy: Float)
    data class Highlight(val emoji: String, val title: String, val detail: String)

    val hasContext: Boolean get() = hourHist.any { it > 0 }
    val hasMood: Boolean get() = moodPoints.isNotEmpty()
    val hasAudio: Boolean get() = audioOutputCounts.isNotEmpty()
    val hasActivity: Boolean get() = activityCounts.isNotEmpty()

    companion object {
        private const val TOP_N = 5

        fun compute(rows: List<LikedTrack>, zone: ZoneId = ZoneId.systemDefault()): LikeAnalytics {
            if (rows.isEmpty()) return empty()

            val hour = IntArray(24)
            val dow = IntArray(7)
            val grid = Array(7) { IntArray(24) }
            val bucket = LinkedHashMap<String, Int>()
            val audio = LinkedHashMap<String, Int>()
            val activity = LinkedHashMap<String, Int>()
            val weather = LinkedHashMap<String, Int>()
            val artistCounts = HashMap<String, Int>()
            val appCounts = HashMap<String, Int>()
            val placeCounts = HashMap<String, Int>()

            var bpmSum = 0f; var bpmN = 0
            var energySum = 0f; var energyN = 0
            var valenceSum = 0f; var valenceN = 0
            var danceSum = 0f; var danceN = 0
            var posSum = 0f; var posN = 0
            val mood = ArrayList<MoodPoint>(rows.size)

            var firstAt = Long.MAX_VALUE
            var lastAt = Long.MIN_VALUE

            for (r in rows) {
                if (r.likedAt < firstAt) firstAt = r.likedAt
                if (r.likedAt > lastAt) lastAt = r.likedAt

                // Prefer stored fields; fall back to recomputing from likedAt for legacy rows.
                val h = r.hourOfDay ?: ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.likedAt), zone).hour
                val d = (r.dayOfWeek ?: ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.likedAt), zone).dayOfWeek.value)
                    .coerceIn(1, 7)
                hour[h] = hour[h] + 1
                dow[d - 1] = dow[d - 1] + 1
                grid[d - 1][h] = grid[d - 1][h] + 1

                r.timeBucket?.let { bucket.merge(it, 1, Int::plus) }
                r.audioOutput?.let { audio.merge(it, 1, Int::plus) }
                r.activity?.let { activity.merge(it, 1, Int::plus) }
                r.weather?.let { weather.merge(it, 1, Int::plus) }
                r.artist?.takeIf { it.isNotBlank() }?.let { artistCounts.merge(it, 1, Int::plus) }
                r.packageName.let { appCounts.merge(it, 1, Int::plus) }
                r.placeLabel?.takeIf { it.isNotBlank() }?.let { placeCounts.merge(it, 1, Int::plus) }

                r.bpm?.let { bpmSum += it; bpmN++ }
                r.energy?.let { energySum += it; energyN++ }
                r.valence?.let { valenceSum += it; valenceN++ }
                r.danceability?.let { danceSum += it; danceN++ }
                r.positionPct?.let { posSum += it; posN++ }
                val v = r.valence; val e = r.energy
                if (v != null && e != null) mood.add(MoodPoint(v, e))
            }

            val avgBpm = if (bpmN > 0) bpmSum / bpmN else null
            val avgEnergy = if (energyN > 0) energySum / energyN else null
            val avgValence = if (valenceN > 0) valenceSum / valenceN else null
            val avgDance = if (danceN > 0) danceSum / danceN else null
            val avgPos = if (posN > 0) posSum / posN else null

            // Per-week rate over the observed span (min 1 day to avoid blow-ups for first day).
            val spanDays = max(1L, (lastAt - firstAt) / 86_400_000L)
            val perWeek = (rows.size.toFloat() * 7f / spanDays.toFloat())

            val ana = LikeAnalytics(
                total = rows.size,
                firstAt = firstAt,
                lastAt = lastAt,
                perWeek = perWeek,
                hourHist = hour,
                dowHist = dow,
                dowHourGrid = grid,
                timeBucketCounts = bucket.sortedDesc(),
                audioOutputCounts = audio.sortedDesc(),
                activityCounts = activity.sortedDesc(),
                weatherCounts = weather.sortedDesc(),
                topArtists = artistCounts.toTopList(TOP_N),
                topApps = appCounts.toTopList(TOP_N),
                topPlaces = placeCounts.toTopList(TOP_N),
                avgBpm = avgBpm,
                avgEnergy = avgEnergy,
                avgValence = avgValence,
                avgDanceability = avgDance,
                avgPositionPct = avgPos,
                moodPoints = mood,
                highlights = emptyList(), // filled below
            )
            return ana.copy(highlights = computeHighlights(ana, rows))
        }

        private fun empty(): LikeAnalytics = LikeAnalytics(
            total = 0, firstAt = null, lastAt = null, perWeek = 0f,
            hourHist = IntArray(24), dowHist = IntArray(7), dowHourGrid = Array(7) { IntArray(24) },
            timeBucketCounts = LinkedHashMap(), audioOutputCounts = LinkedHashMap(),
            activityCounts = LinkedHashMap(), weatherCounts = LinkedHashMap(),
            topArtists = emptyList(), topApps = emptyList(), topPlaces = emptyList(),
            avgBpm = null, avgEnergy = null, avgValence = null, avgDanceability = null, avgPositionPct = null,
            moodPoints = emptyList(), highlights = emptyList(),
        )

        private fun LinkedHashMap<String, Int>.sortedDesc(): LinkedHashMap<String, Int> {
            val sorted = entries.sortedByDescending { it.value }
            val out = LinkedHashMap<String, Int>(size)
            for (e in sorted) out[e.key] = e.value
            return out
        }

        private fun HashMap<String, Int>.toTopList(n: Int): List<Pair<String, Int>> =
            entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

        // ───── Highlights: surprising / fun insights ─────

        private fun computeHighlights(a: LikeAnalytics, rows: List<LikedTrack>): List<Highlight> {
            val out = ArrayList<Highlight>()

            // 1. Peak time
            val peakHour = a.hourHist.withIndex().maxByOrNull { it.value }
            if (peakHour != null && peakHour.value > 0) {
                val dowIdx = a.dowHist.withIndex().maxByOrNull { it.value }!!.index
                val dowName = DayOfWeek.of(dowIdx + 1).jp()
                out.add(
                    Highlight(
                        emoji = "🕒",
                        title = "ピークは ${dowName} の ${peakHour.index}時台",
                        detail = "全体の ${pct(peakHour.value, a.total)}% がこの時間帯に集中"
                    )
                )
            }

            // 2. Audio routing dominant
            val audioTop = a.audioOutputCounts.entries.firstOrNull()
            if (audioTop != null && audioTop.value * 2 >= rows.count { it.audioOutput != null }) {
                out.add(
                    Highlight(
                        emoji = audioTop.key.audioEmoji(),
                        title = "${audioTop.key.audioJp()} で聴くことが多い",
                        detail = "${pct(audioTop.value, rows.count { it.audioOutput != null })}% がこの経路"
                    )
                )
            }

            // 3. Mood quadrant
            if (a.avgValence != null && a.avgEnergy != null) {
                val (emoji, label) = moodLabel(a.avgValence, a.avgEnergy)
                out.add(
                    Highlight(
                        emoji = emoji,
                        title = "あなたの平均ムード: $label",
                        detail = "valence=${(a.avgValence * 100).roundToInt()}, energy=${(a.avgEnergy * 100).roundToInt()}"
                    )
                )
            }

            // 4. Weekend vs weekday energy
            val weekendE = rows.filter {
                val dow = it.dayOfWeek ?: return@filter false
                dow == 6 || dow == 7
            }.mapNotNull { it.energy }.avgOrNull()
            val weekdayE = rows.filter {
                val dow = it.dayOfWeek ?: return@filter false
                dow in 1..5
            }.mapNotNull { it.energy }.avgOrNull()
            if (weekendE != null && weekdayE != null && kotlin.math.abs(weekendE - weekdayE) > 0.08f) {
                val diff = ((weekendE - weekdayE) * 100).roundToInt()
                if (diff > 0) {
                    out.add(Highlight("🎉", "週末のほうがアグレッシブ", "energy が平日比 +${diff}%"))
                } else {
                    out.add(Highlight("☕", "週末は落ち着いた選曲", "energy が平日比 ${diff}%"))
                }
            }

            // 5. BPM character
            a.avgBpm?.let { bpm ->
                val tag = when {
                    bpm < 90 -> "🌙 スロー" to "ゆったりした曲が好み (avg ${bpm.roundToInt()} BPM)"
                    bpm < 120 -> "🚶 ミドル" to "ミディアムテンポが中心 (avg ${bpm.roundToInt()} BPM)"
                    bpm < 140 -> "🏃 アップ" to "アップテンポが好み (avg ${bpm.roundToInt()} BPM)"
                    else -> "⚡ 高速" to "高速ビートに魂が反応 (avg ${bpm.roundToInt()} BPM)"
                }
                out.add(Highlight(tag.first.take(2), tag.first.drop(2).trim(), tag.second))
            }

            // 6. Discovery streak
            if (a.perWeek > 0f) {
                val rate = a.perWeek.roundToInt().coerceAtLeast(1)
                out.add(Highlight("📈", "週あたり ${rate} 曲ペース", "計 ${a.total} 曲の発見ログ"))
            }

            // 7. Top artist obsession
            a.topArtists.firstOrNull()?.let { (artist, n) ->
                if (n >= 3 && n * 5 >= a.total) {
                    out.add(Highlight("🎤", "推し: $artist", "全体の ${pct(n, a.total)}% を占めるヘビロテ"))
                }
            }

            // 8. Place hotspot
            a.topPlaces.firstOrNull()?.let { (place, n) ->
                if (n >= 3) out.add(Highlight("📍", "Like がよく出る場所: $place", "${n} 曲がここで保存"))
            }

            // 9. Position-in-track tendency
            a.avgPositionPct?.let { p ->
                when {
                    p < 0.25f -> out.add(Highlight("⏱️", "イントロ即決派", "平均で曲頭${(p * 100).roundToInt()}%地点で Like"))
                    p > 0.6f -> out.add(Highlight("🎶", "サビまで聴く派", "平均で曲の${(p * 100).roundToInt()}%地点で Like"))
                    else -> {}
                }
            }

            return out
        }

        private fun List<Float>.avgOrNull(): Float? =
            if (isEmpty()) null else sum() / size

        private fun pct(part: Int, total: Int): Int =
            if (total <= 0) 0 else ((part.toFloat() / total) * 100f).roundToInt()

        private fun moodLabel(valence: Float, energy: Float): Pair<String, String> = when {
            valence >= 0.5f && energy >= 0.5f -> "☀️" to "ハッピー&エネルギッシュ"
            valence >= 0.5f && energy < 0.5f -> "😌" to "穏やかでポジティブ"
            valence < 0.5f && energy >= 0.5f -> "⚡" to "激しめ・攻めの気分"
            else -> "🌧️" to "メロウ・内省モード"
        }

        private fun DayOfWeek.jp(): String = when (this) {
            DayOfWeek.MONDAY -> "月曜"
            DayOfWeek.TUESDAY -> "火曜"
            DayOfWeek.WEDNESDAY -> "水曜"
            DayOfWeek.THURSDAY -> "木曜"
            DayOfWeek.FRIDAY -> "金曜"
            DayOfWeek.SATURDAY -> "土曜"
            DayOfWeek.SUNDAY -> "日曜"
        }

        private fun String.audioJp(): String = when (this) {
            "BLUETOOTH" -> "Bluetooth"
            "WIRED_HEADSET" -> "有線イヤホン"
            "USB" -> "USBオーディオ"
            "HDMI" -> "HDMI"
            "SPEAKER" -> "本体スピーカー"
            else -> this
        }

        private fun String.audioEmoji(): String = when (this) {
            "BLUETOOTH" -> "🎧"
            "WIRED_HEADSET" -> "🎧"
            "USB" -> "🔌"
            "HDMI" -> "📺"
            "SPEAKER" -> "📱"
            else -> "🔊"
        }
    }
}
