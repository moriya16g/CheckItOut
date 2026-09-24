package com.example.checkitout.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal blocking HTTP helper. Always call from a coroutine on Dispatchers.IO.
 * Returns null on any non-2xx or exception (callers treat absence as "no data").
 */
object Http {
    private const val DEFAULT_TIMEOUT_MS = 4_000

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CheckItOut/0.2 (https://github.com/checkitout)")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code !in 200..299) return null
            conn.inputStream.use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    fun postForm(
        url: String,
        formBody: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "CheckItOut/0.2")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val body = formBody.entries.joinToString("&") { (k, v) ->
                "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) return null
            conn.inputStream.use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }
}
