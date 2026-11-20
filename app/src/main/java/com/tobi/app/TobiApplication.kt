package com.tobi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil3.SingletonImageLoader
import com.tobi.app.di.AppContainer

class TobiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SingletonImageLoader.setSafe { context ->
            container.network.newImageLoader(context)
        }
        val channel = NotificationChannel(
            "downloads",
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.download_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
