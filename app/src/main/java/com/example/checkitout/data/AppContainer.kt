package com.example.checkitout.data

import android.content.Context

/**
 * Simple manual DI container. Held on the [com.example.checkitout.CheckItOutApp].
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val recentBuffer: RecentBuffer = RecentBuffer(capacity = 10)
    val sinks: List<PlaylistSink> = listOf(LocalDbSink(appContext))
    val db: AppDatabase by lazy { AppDatabase.get(appContext) }
}
