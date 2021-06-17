package com.example.android.camera2.video

import android.graphics.*
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.Build
import android.text.Html
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.lifecycle.lifecycleScope
import com.example.android.camera2.video.utils.BitmapUtil
import com.example.android.camera2.video.utils.LogUtil
import com.example.mmsbridge.TranslateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun setHtml(tv: TextView, txt: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        tv.text = Html.fromHtml(txt, Html.FROM_HTML_MODE_COMPACT)
    } else {
        tv.text = Html.fromHtml(txt)
    }
}
fun getBitmapImageFromYUV(data: ByteArray, width: Int, height: Int): Bitmap? {
//    val yuvimage = YuvImage(data, ImageFormat.YUY2, width, height, null)
//    val baos = ByteArrayOutputStream()
//    yuvimage.compressToJpeg(Rect(0, 0, width, height), 40, baos)
//    val jdata: ByteArray = baos.toByteArray()
////    val bitmapFatoryOptions: BitmapFactory.Options = BitmapFactory.Options()
////    bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565
//    return BitmapFactory.decodeByteArray(jdata, 0, jdata.size, null)

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

fun fetchNV21(@NonNull bitmap: Bitmap): ByteArray {
    var w = bitmap.width
    var h = bitmap.height
    val size = w * h
    val pixels = IntArray(size)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val nv21 = ByteArray(size * 3 / 2)

    // Make w and h are all even.
    w = w and 1.inv()
    h = h and 1.inv()
    for (i in 0 until h) {
        for (j in 0 until w) {
            val yIndex = i * w + j
            val argb = pixels[yIndex]
            val a = argb shr 24 and 0xff // unused
            val r = argb shr 16 and 0xff
            val g = argb shr 8 and 0xff
            val b = argb and 0xff
            var y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
            y = clamp(y, 16, 255)
            nv21[yIndex] = y.toByte()
            if (i % 2 == 0 && j % 2 == 0) {
                var u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                var v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                u = clamp(u, 0, 255)
                v = clamp(v, 0, 255)
                nv21[size + i / 2 * w + j] = v.toByte()
                nv21[size + i / 2 * w + j + 1] = u.toByte()
            }
        }
    }
    return nv21
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
    if (id != "0") {
//        return
    }
    if (!enableTranslate) {
        return
    }
    val height = imgSource.height
    val width = imgSource.width
    val bytes = getBitmapArrayFromImage(imgSource)
    AIHelper.imageManager.pushImage(id, bytes, width, height)
//    AIHelper.imageManager.pushImage("1", bytes, width, height)
//    AIHelper.imageManager.pushImage("2", bytes, width, height)
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
