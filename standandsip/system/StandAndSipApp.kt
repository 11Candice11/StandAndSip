package com.standandsip.system

import android.app.Application

class StandAndSipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No-op: the full-screen notifier creates its own NotificationChannel on demand.
    }
}