package com.example.android.camera2.video

import android.app.Application
import android.content.Context

class CameraApplication : Application() {
    companion object {
        lateinit var instance: CameraApplication private set
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}