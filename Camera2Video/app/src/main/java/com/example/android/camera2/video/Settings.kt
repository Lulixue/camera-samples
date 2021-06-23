package com.example.android.camera2.video

import android.graphics.ImageFormat
import android.util.Size
import com.tencent.mmkv.MMKV

object Settings {
    var mmkv: MMKV
    init {
        MMKV.initialize(CameraApplication.instance)
        mmkv = MMKV.defaultMMKV()
    }
    const val KEY_ENABLE_AI_ANALYZE = "enable_ai_analyze"
    const val KEY_ENABLE_CAMERAS = "enable_cameras"
    const val KEY_LEGACY_CAMERAS = "legacy_cameras"
    const val KEY_CAMERA_FPS = "fps"
    const val KEY_RESOLUTION = "resolution"
    const val KEY_DEV_BOARD = "dev_board"
    const val KEY_SKIP_SETTINGS = "skip_settings_next_time"

    val ENABLE_AI_ANALYZE: Boolean
        get() = mmkv.getBoolean(KEY_ENABLE_AI_ANALYZE, true)
    const val AI_IMAGE_FORMAT = ImageFormat.YUV_420_888
    /** Maximum number of images that will be held in the reader's buffer */
    const val AI_IMAGE_SIZE: Int = 1
    const val AI_FRAME_CYCLE_TIME = 500L // ms
    private const val CAMERA_COUNT = 3

    private const val DEFAULT_RESOLUTION = "3840x2160"

    val RESOLUTION: String
        get() = mmkv.getString(KEY_RESOLUTION, DEFAULT_RESOLUTION)!!

    val DEFAULT_CAMERA_WIDTH: Int
        get() {
            val res = mmkv.getString(KEY_RESOLUTION, DEFAULT_RESOLUTION)!!
            return (res.split("x")[0]).toInt()
        }

    val DEFAULT_CAMERA_HEIGHT: Int
        get() {
            val res = mmkv.getString(KEY_RESOLUTION, DEFAULT_RESOLUTION)!!
            return (res.split("x")[1]).toInt()
        }

    val DEFAULT_CAMERA_FPS
        get() = mmkv.getInt(KEY_CAMERA_FPS, 60)
    val TRIPLE_CAMERA_DEV
        get() = mmkv.getBoolean(KEY_DEV_BOARD, true)

    val ENABLE_CAMERAS: Set<String>
        get() = mmkv.getStringSet(KEY_ENABLE_CAMERAS, HashSet<String>(listOf("0", "1", "2")))!!

    val CAMERA_USE_LEGACY: Set<String>
        get() = mmkv.getStringSet(KEY_LEGACY_CAMERAS, HashSet<String>(listOf("2")))!!

    const val SHOW_TRANSLATE_RESULT_IMAGES = true

    var SKIP_SETTINGS: Boolean = false
        get() = mmkv.getBoolean(KEY_SKIP_SETTINGS, false)
        set(value) {
            field = value
            mmkv.putBoolean(KEY_SKIP_SETTINGS, value)
        }

    var readyCameras = HashSet<String>()

    fun isAllCameraReady() = readyCameras.size == CAMERA_COUNT
    fun setCameraReady(id: String) {
        readyCameras.add(id)
    }
    fun isCameraPushed(id: String) = readyCameras.contains(id)
}