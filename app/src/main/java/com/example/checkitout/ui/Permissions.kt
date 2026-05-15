package com.example.checkitout.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.checkitout.service.MediaNotificationListener

object Permissions {

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, MediaNotificationListener::class.java)
        val splitter: Iterator<String> = TextUtils.SimpleStringSplitter(':').apply { setString(flat) }
        return splitter.asSequence()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Opens the "App info" screen for this app.
     * On Android 13+ sideloaded apps, the user can tap ⋮ → "Allow restricted settings"
     * here before enabling Notification Listener or Accessibility Service.
     */
    fun openAppDetailSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ──────────── Optional context-capture permissions ────────────
    // The app works without any of these; they only enrich LikeContext.

    fun isLocationGranted(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun isActivityRecognitionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val locationPermissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val activityRecognitionPermission: String = Manifest.permission.ACTIVITY_RECOGNITION
}
