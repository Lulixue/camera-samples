/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.android.camera2.video.utils.LoadSoFileUtils
import java.io.File


class CameraActivity : AppCompatActivity() {
    init {
        AIHelper.instance.copyModelToCache(CameraApplication.instance)
    }

    private lateinit var container: FrameLayout


    @SuppressLint("UnsafeDynamicallyLoadedCode", "SdCardPath")
    private fun initSdSo() {
        val dir: File = getDir("jniLibs", Activity.MODE_PRIVATE)
        val ext = "/sdcard/DCIM"

        try {
            LoadSoFileUtils.loadSoFile(this, ext)
            val currentFiles: Array<File> = dir.listFiles()
            for (currentFile in currentFiles) {
                System.load(currentFile.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        container = findViewById(R.id.fragment_container)
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } catch (_ : Exception) {}

        initSdSo()
        println(AIHelper.mmsBridge.stringFromJNI())
        AIHelper.instance.initHelper()
        initResultDir()
    }

    override fun onDestroy() {
        super.onDestroy()
        AIHelper.instance.destroyHelper()
    }
    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        container.postDelayed({
            container.systemUiVisibility = FLAGS_FULLSCREEN
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    companion object {
        /** Combination of all flags required to put activity into immersive mode */
        const val FLAGS_FULLSCREEN =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        /** Milliseconds used for UI animations */
        const val ANIMATION_FAST_MILLIS = 50L
        const val ANIMATION_SLOW_MILLIS = 100L
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L

    }
}
