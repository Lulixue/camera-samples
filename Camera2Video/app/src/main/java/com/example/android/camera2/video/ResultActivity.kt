package com.example.android.camera2.video

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.mmsbridge.TranslateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var translateResult: TranslateResult? = null
class ResultActivity : AppCompatActivity() {
    private lateinit var image2K: ImageView
    private lateinit var image8K: ImageView
    private lateinit var size2K: TextView
    private lateinit var size8K: TextView
    private var encoding = !Settings.SHOW_TRANSLATE_RESULT_IMAGES
    private fun loadResultImages() {
        val result = translateResult!!
        size2K.text = "${result.size2K}"
        size8K.text = "${result.size8K}"
        lifecycleScope.launch(Dispatchers.Default) {
            encoding = true
            val bmp2k =
                getBitmapImageFromYUV(result.buffer2K, result.size2K.width, result.size2K.height)
            val bmp4k =
                getBitmapImageFromYUV(result.buffer8K, result.size8K.width, result.size8K.height)

            encoding = false
            withContext(Dispatchers.Main) {
                image2K.setImageBitmap(bmp2k)
                image8K.setImageBitmap(bmp4k)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val view = findViewById<View>(R.id.btnBack)
        view.setOnClickListener { onBackPressed() }

        image2K = findViewById(R.id.image2K)
        image8K = findViewById(R.id.image8K)
        size2K = findViewById(R.id.translate2K)
        size8K = findViewById(R.id.translate8K)
        loadResultImages()
    }
}