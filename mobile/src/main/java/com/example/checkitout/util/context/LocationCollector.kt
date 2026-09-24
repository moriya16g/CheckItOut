package com.example.checkitout.util.context

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Returns the most recent known device location without forcing a fresh fix.
 * Avoids a hard dependency on Google Play Services by using LocationManager directly.
 *
 * Returns null if no permission, no provider available, or no cached fix.
 */
object LocationCollector {

    @SuppressLint("MissingPermission")
    suspend fun collect(context: Context): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOfNotNull(
            if (fineGranted) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        // Pick the most recent cached fix across enabled providers
        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best
    }
}
