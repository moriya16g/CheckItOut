package net.sarotti.checkitout.shared

import org.json.JSONObject

/** Wearable Data Layer contract shared by the phone and watch apps. */
object WearProtocol {
    const val PATH_LIKE = "/checkitout/like"
    const val PATH_LIKE_RESULT = "/checkitout/like/result"

    /** Declared in the phone app's res/values/wear.xml. */
    const val CAPABILITY_PHONE = "checkitout_phone"
    /** Declared in the watch app's res/values/wear.xml. */
    const val CAPABILITY_WEAR = "checkitout_wear"
}

data class LikeRequest(
    val requestId: String,
    /** Epoch millis on the watch when the user tapped. */
    val tappedAt: Long,
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("requestId", requestId)
        .put("tappedAt", tappedAt)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): LikeRequest? = runCatching {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            LikeRequest(
                requestId = o.getString("requestId"),
                tappedAt = o.getLong("tappedAt"),
            )
        }.getOrNull()
    }
}

data class LikeResult(
    val requestId: String,
    val status: Status,
    val title: String? = null,
    val artist: String? = null,
) {
    enum class Status { OK, NO_TRACK, NO_PERMISSION, ERROR }

    fun toBytes(): ByteArray = JSONObject()
        .put("requestId", requestId)
        .put("status", status.name)
        .apply {
            title?.let { put("title", it) }
            artist?.let { put("artist", it) }
        }
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): LikeResult? = runCatching {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            LikeResult(
                requestId = o.getString("requestId"),
                status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.ERROR),
                title = o.optString("title").takeIf { it.isNotEmpty() },
                artist = o.optString("artist").takeIf { it.isNotEmpty() },
            )
        }.getOrNull()
    }
}
