package com.example.checkitout

import android.app.Application
import com.example.checkitout.data.AppContainer
import com.example.checkitout.sync.SyncWorker

class CheckItOutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.enqueuePeriodicIfConfigured(this)
    }
}
