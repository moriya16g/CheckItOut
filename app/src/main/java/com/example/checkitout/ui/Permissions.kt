package com.example.checkitout.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.example.checkitout.service.MediaNotificationListener
import com.example.checkitout.service.VolumeKeyAccessibilityService

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

    fun isAccessibilityEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(context, VolumeKeyAccessibilityService::class.java)
        val splitter: Iterator<String> = TextUtils.SimpleStringSplitter(':').apply { setString(flat) }
        return splitter.asSequence()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
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
}
