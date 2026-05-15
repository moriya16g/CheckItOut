package com.example.checkitout.util.context

import com.example.checkitout.util.Http
import org.json.JSONObject

/**
 * Reverse geocoding via OpenStreetMap Nominatim. Free, no key required.
 * Heavy usage requires a proper User-Agent (we send one in [Http]).
 *
 * Returns a short human-readable label like "Shibuya, Tokyo, Japan".
 */
object ReverseGeoCollector {

    suspend fun collect(lat: Double, lng: Double): String? {
        val url = "https://nominatim.openstreetmap.org/reverse" +
                "?format=jsonv2&zoom=14&lat=$lat&lon=$lng"
        val body = Http.get(url, headers = mapOf("Accept-Language" to "ja,en")) ?: return null
        return try {
            val obj = JSONObject(body)
            val addr = obj.optJSONObject("address")
            if (addr != null) {
                val parts = listOfNotNull(
                    addr.optString("suburb").takeIf { it.isNotBlank() }
                        ?: addr.optString("neighbourhood").takeIf { it.isNotBlank() }
                        ?: addr.optString("village").takeIf { it.isNotBlank() }
                        ?: addr.optString("town").takeIf { it.isNotBlank() }
                        ?: addr.optString("city_district").takeIf { it.isNotBlank() },
                    addr.optString("city").takeIf { it.isNotBlank() }
                        ?: addr.optString("county").takeIf { it.isNotBlank() },
                    addr.optString("country").takeIf { it.isNotBlank() },
                )
                parts.joinToString(", ").ifBlank { obj.optString("display_name").takeIf { it.isNotBlank() } }
            } else {
                obj.optString("display_name").takeIf { it.isNotBlank() }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
