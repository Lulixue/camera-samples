package com.example.android.camera2.video

import android.content.Context
import com.example.mmsbridge.MmsBridgeApi
import com.example.mmsbridge.TranslateImage
import com.example.mmsbridge.TranslateResult
import com.meishe.aidetect.AIEngine
import java.io.*

class ImageManager(private val bridgeApi: MmsBridgeApi) {
    private val images = HashMap<String, TranslateImage>()
    var callback: TranslateCallback? = null

    interface TranslateCallback {
        fun onResult(result: TranslateResult)
    }

    fun pushImage(cameraId: String, array: ByteArray, width: Int, height: Int) {
        synchronized(images) {
            images[cameraId] = (TranslateImage(array, width, height))
            if (images.size == 3) {
                val img1 = images[CAMERA_0]!!
                val img2 = images[CAMERA_1]!!
                val img3 = images[CAMERA_2]!!
                val result = TranslateResult()
                val ret = bridgeApi.translateImages(img1, img2, img3, result)
                if (ret == 0) {
                    callback?.onResult(result)
                }
                images.clear()
            }
        }
    }
    companion object {
        const val CAMERA_0 = "0"
        const val CAMERA_1 = "1"
        const val CAMERA_2 = "2"
    }
}

class AIHelper {
    companion object {
        val mmsBridge = MmsBridgeApi()
        val imageManager = ImageManager(mmsBridge)
        val instance = AIHelper()
        const val MODEL_DIR = "model"
        const val PLACE_MODEL = "pf.model"
        const val SCENE_MODEL = "sf.model"
        const val PHOTO_MODEL = "photo_category_20210115_191659.model"
        const val ACTIVITY_MODEL = "activity_v1_20210116_210237.model"
    }
    init {
        System.loadLibrary("native-lib")
    }
    fun initHelper() {
        mmsBridge.open()
    }
    fun destroyHelper() {
        mmsBridge.close()
        instance.closeAI()
    }


    private val aiEngine: AIEngine = AIEngine.getInstance()
    private var sessionId: Long = 0
    private lateinit var modelDir: File
    private lateinit var outputFile: File

    private fun copyAndWriteToFile(inputStream: InputStream, destFile: File) {
        try {
            val reader = InputStreamReader(inputStream)
            val writer = OutputStreamWriter(FileOutputStream(destFile))
            val bytes = CharArray(1024)
            var readBytes = reader.read(bytes)
            while (readBytes > 0) {
                writer.write(bytes, 0, readBytes)
                readBytes = reader.read(bytes)
            }
            reader.close()
            writer.close()
        } catch (e: IOException) {

        }
    }

    fun copyModelToCache(context: Context) {
        val assetManager = context.assets
        modelDir = File(context.filesDir, MODEL_DIR)
        outputFile = File(context.filesDir, "ai_output.txt")
        modelDir.mkdir()
        for (file in assetManager.list(MODEL_DIR)!!) {
            val destFile = File(modelDir, file)
            if (destFile.exists()) {
                continue
            }
            val inputStream = assetManager.open("$MODEL_DIR/$file")
            copyAndWriteToFile(inputStream, destFile)
        }
    }

    private fun assetFilePath(fileName: String): String {
//        return modelDir.absolutePath + "/" + fileName
        return "sdcard/DCIM/model/$fileName"
    }

    // public native int detectTag(long object, byte[] bytes, int width, int height, int bufferFormat, long timeStamp);
    fun detectTag(data: ByteArray, type: Int, width: Int, height: Int, timestamp: Long): Int {
        val result = aiEngine.detectTag(sessionId, data, width, height, type, timestamp)
        println("result: $result")
        return result
    }

    // public native int detectTag3Image(long object, byte[] bytes0, int width0, int height0, int bufferFormat0, long timeStamp0,
    //                                      byte[] bytes1, int width1, int height1, int bufferFormat1, long timeStamp1,
    //                                      byte[] bytes2, int width2, int height2, int bufferFormat2, long timeStamp2);
    fun detectTag3Image(dataArray: List<ByteArray>, type: Int,  width: Int, height: Int, timestamps: LongArray) {
        val result = aiEngine.detectTag3Image(sessionId, dataArray[0], width, height, type, timestamps[0],
                                                dataArray[1], width, height, type, timestamps[1],
                                                dataArray[2], width, height, type, timestamps[2])

        println("result: $result")
    }

    private fun testRead(path: String) {
        val file = File(path)
        if (!file.exists()) {
            file.createNewFile()
            file.writeText("hello")
            return
        }
        val reader = InputStreamReader(FileInputStream(file))
        val buffer = CharArray(1024)
        reader.read(buffer)

        reader.close()
    }
    // public native long createTagDetectionSession(String sceneModelPath, String placeModelPath, String photoModelPath, String activityModelPath, String outFilePath);

    fun initAI() {
        val place = assetFilePath(PLACE_MODEL)
        val scene = assetFilePath(SCENE_MODEL)
        val photo = assetFilePath(PHOTO_MODEL)
        val activity = assetFilePath(ACTIVITY_MODEL)
        sessionId = aiEngine.createTagDetectionSession(scene, place, photo, activity, outputFile.absolutePath)
        println("session id: $sessionId")
    }
    private fun closeAI() {
        aiEngine.closeTagDetectionSession(sessionId)
    }

}