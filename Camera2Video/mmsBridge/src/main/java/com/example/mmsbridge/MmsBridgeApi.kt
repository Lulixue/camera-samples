package com.example.mmsbridge

import android.util.Size

class TranslateResult {
    lateinit var buffer2K: ByteArray
    lateinit var buffer8K: ByteArray
    lateinit var size2K: Size
    lateinit var size8K: Size
}

data class TranslateImage(val imageArray: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TranslateImage

        if (!imageArray.contentEquals(other.imageArray)) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = imageArray.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

class MmsBridgeApi {

    external fun open()
    external fun close()
    external fun stringFromJNI(): String
    external fun translateImages(img1: TranslateImage, img2: TranslateImage, img3: TranslateImage,
                                 result: TranslateResult): Int
}