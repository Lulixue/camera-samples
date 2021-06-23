package com.example.android.camera2.video

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.Build
import android.text.Html
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.android.camera2.video.utils.BitmapUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val TAG = "CameraDemo"
fun setHtml(tv: TextView, txt: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        tv.text = Html.fromHtml(txt, Html.FROM_HTML_MODE_COMPACT)
    } else {
        tv.text = Html.fromHtml(txt)
    }
}
fun getBitmapImageFromYUV(data: ByteArray, width: Int, height: Int): Bitmap? {
    return BitmapUtil.getBitmapImageFromYUV(data, width, height)
}

data class AIImageSource(
    val width: Int, val height: Int, val pixelStride: Int, val rowStride: Int,
    val yRawSrcBytes: ByteArray, val vRawSrcBytes: ByteArray,
    val format: Int, val timestamp: Long
)

fun getImageAISource(image: Image): AIImageSource {
    val planes = image.planes
    val remaining0 = planes[0].buffer.remaining()
    val remaining2 = planes[2].buffer.remaining()
    val yRawSrcBytes = ByteArray(remaining0)
    val vRawSrcBytes = ByteArray(remaining2)
    planes[0].buffer[yRawSrcBytes]
    planes[2].buffer[vRawSrcBytes]

    val pixelStride = planes[2].pixelStride
    val rowOffset = planes[2].rowStride
    return AIImageSource(image.width, image.height, pixelStride, rowOffset, yRawSrcBytes, vRawSrcBytes,
        image.format, image.timestamp)
}

val capturingBg: Drawable = ContextCompat.getDrawable(CameraApplication.instance, R.drawable.record_off)!!
val toCaptureBg: Drawable = ContextCompat.getDrawable(CameraApplication.instance, R.drawable.record_on)!!
val skiSettingsIcon: Drawable = ContextCompat.getDrawable(CameraApplication.instance, R.drawable.disabled)!!
val showSettingIcon: Drawable = ContextCompat.getDrawable(CameraApplication.instance, R.drawable.settings)!!

fun aiAnalyzeImage(id: String, imgSource: AIImageSource): Int {
    val timestamp = imgSource.timestamp
    val fmt = imgSource.format
    val height = imgSource.height
    val width = imgSource.width
    val bytes = getBitmapArrayFromImage(imgSource)

    return AIHelper.instance.detectTag(bytes, fmt, width, height, timestamp)
}

var enableTranslate = false
fun translateImage(id: String, imgSource: AIImageSource) {
    if (!enableTranslate) {
        return
    }
    val height = imgSource.height
    val width = imgSource.width
    val bytes = getBitmapArrayFromImage(imgSource)
    println("$TAG Input cam$id: $width*$height, ${bytes.size}")
    AIHelper.imageManager.pushImage(id, bytes, width, height)
}

fun msToReadable(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60

    return String.format("%02d:%02d", minutes, seconds % 60)
}
fun getBitmapArrayFromImage(imgSource: AIImageSource): ByteArray {
    val time1 = System.currentTimeMillis()
    val w = imgSource.width
    val h = imgSource.height
    val i420Size = w * h * 3 / 2
    //获取pixelStride，可能跟width相等，可能不相等
    val pixelStride = imgSource.pixelStride
    val rowOffset = imgSource.rowStride
    val nv21 = ByteArray(i420Size)
    val yRawSrcBytes = imgSource.yRawSrcBytes
    val vRawSrcBytes = imgSource.vRawSrcBytes
    if (pixelStride == w) {
        //两者相等，说明每个YUV块紧密相连，可以直接拷贝
        System.arraycopy(yRawSrcBytes, 0, nv21, 0, rowOffset * h)
        System.arraycopy(vRawSrcBytes, 0, nv21, rowOffset * h, rowOffset * h / 2 - 1)
    } else {
        val ySrcBytes = ByteArray(w * h)
        val vSrcBytes = ByteArray(w * h / 2 - 1)
        for (row in 0 until h) {
            //源数组每隔 rowOffest 个bytes 拷贝 w 个bytes到目标数组
            System.arraycopy(yRawSrcBytes, rowOffset * row, ySrcBytes, w * row, w)

            //y执行两次，uv执行一次
            if (row % 2 == 0) {
                //最后一行需要减一
                if (row == h - 2) {
                    System.arraycopy(
                        vRawSrcBytes,
                        rowOffset * row / 2,
                        vSrcBytes,
                        w * row / 2,
                        w - 1
                    )
                } else {
                    System.arraycopy(vRawSrcBytes, rowOffset * row / 2, vSrcBytes, w * row / 2, w)
                }
            }
        }
        System.arraycopy(ySrcBytes, 0, nv21, 0, w * h)
        System.arraycopy(vSrcBytes, 0, nv21, w * h, w * h / 2 - 1)
    }
    return nv21
}

fun bitmapToFile(bmp: Bitmap, title: String, filename: String) {
    val path = "$filename/$title${bmp.width}x${bmp.height}.bmp"
    try {
        FileOutputStream(path).use { out ->
            bmp.compress(
                Bitmap.CompressFormat.PNG,
                100,
                out
            ) // bmp is your Bitmap instance
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

const val RESULT_DIR = "sdcard/DCIM/result"
fun initResultDir() {
    val file = File(RESULT_DIR)
    if (file.exists()) {
        file.deleteRecursively()
    }
    file.mkdir()
}

var counter = 0
fun saveBitmap(bmp2k: Bitmap?, bmp8k: Bitmap?): String {
    val destDir = File(RESULT_DIR, String.format("%03d", ++counter))
    destDir.mkdir()

    bmp2k?.also {
        bitmapToFile(it, "2k", destDir.absolutePath)
    }
    bmp8k?.also {
        bitmapToFile(it, "8k", destDir.absolutePath)
    }
    return destDir.absolutePath
}
