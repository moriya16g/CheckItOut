package com.example.checkitout

import android.app.Application
import com.example.checkitout.data.AppContainer
import com.example.checkitout.sync.SyncWorker
import com.example.checkitout.widget.LikeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CheckItOutApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.enqueuePeriodicIfConfigured(this)
        appScope.launch {
            container.recentBuffer.state.collect { list ->
                LikeWidgetProvider.updateAll(this@CheckItOutApp, list.firstOrNull())
            }
        }
    }
}
