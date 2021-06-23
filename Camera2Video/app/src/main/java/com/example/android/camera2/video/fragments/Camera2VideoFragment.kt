/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.camera2.video.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.android.camera2.video.*
import com.example.android.camera2.video.views.AutoFitTextureView
import com.example.mmsbridge.TranslateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Camera2VideoFragment : Fragment(), View.OnClickListener {
    private val args: Camera2VideoFragmentArgs by navArgs()

    private lateinit var overlay2k: View
    private lateinit var overlay8K: View
    private lateinit var size2K: TextView
    private lateinit var size8K: TextView
    private lateinit var image2K: ImageView
    private lateinit var image8K: ImageView
    private val animationBlinkTask2k = Runnable {
        // Flash white animation
        overlay2k.setBackgroundColor(Color.argb(150, 255, 255, 255))
        // Wait for ANIMATION_FAST_MILLIS
        overlay2k.postDelayed(
            { overlay2k.background = null } // Remove white flash animation
            , CameraActivity.ANIMATION_FAST_MILLIS)
    }

    private val animationBlinkTask8k = Runnable {
        // Flash white animation
        overlay8K.setBackgroundColor(Color.argb(150, 255, 255, 255))
        // Wait for ANIMATION_FAST_MILLIS
        overlay8K.postDelayed(
            { overlay8K.background = null } // Remove white flash animation
            , CameraActivity.ANIMATION_FAST_MILLIS)
    }



    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private fun chooseVideoSize(choices: Array<Size>): Size {
        return Size(args.width, args.height)
    }

    companion object {
        private const val TAG = "Camera2VideoFragment"
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()

        /**
         * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
         * width and height are at least as large as the respective requested values, and whose aspect
         * ratio matches with the specified value.
         *
         * @param choices     The list of sizes that the camera supports for the intended output class
         * @param width       The minimum desired width
         * @param height      The minimum desired height
         * @param aspectRatio The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(
            choices: Array<Size>,
            width: Int,
            height: Int,
            aspectRatio: Size?
        ): Size {
            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            val w = aspectRatio!!.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.height == option.width * h / w && option.width >= width && option.height >= height) {
                    bigEnough.add(option)
                }
            }

            // Pick the smallest of those, assuming we found any
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }

        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }
    }

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var mTextureView: AutoFitTextureView

    /**
     * Button to record video
     */
    private lateinit var mButtonVideo: ImageButton

    /**
     * A reference to the opened [CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * A reference to the current [CameraCaptureSession] for
     * preview.
     */
    private var mPreviewSession // 用来向相机发送获取发送图像的请求
            : CameraCaptureSession? = null

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int, height: Int
            ) {
                openCamera(width, height)
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int, height: Int
            ) {
                configureTransform(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }

    /**
     * The [Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * The [Size] of video recording.
     */
    private var mVideoSize: Size? = null

    /**
     * MediaRecorder
     */
    private var mMediaRecorder: MediaRecorder? = null

    /**
     * Whether the app is recording video now
     */
    private var mIsRecordingVideo = false
    private var recordingStartMillis: Long = 0

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            startPreview()
            mCameraOpenCloseLock.release()
            if (null != mTextureView) {
                configureTransform(mTextureView!!.width, mTextureView!!.height)
            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            val activity: Activity? = activity
            activity?.finish()
        }
    }
    private var mSensorOrientation: Int? = null
    private var mNextVideoAbsolutePath: String? = null

    // 用于捕捉请求的构造器
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        cameraId = args.cameraId
        return inflater.inflate(R.layout.fragment_camera2_video, container, false)
    }

    private lateinit var videoStatus: TextView
    private lateinit var aiResult: TextView
    private lateinit var overlay: View
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mTextureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
        mButtonVideo = view.findViewById(R.id.video)
        mButtonVideo.setOnClickListener(this)
        videoStatus = view.findViewById(R.id.video_status)
        val cameraInfo = view.findViewById<TextView>(R.id.camera_info)
        aiResult = view.findViewById(R.id.ai_result)
        overlay = view.findViewById(R.id.overlay)

        image2K = view.findViewById(R.id.image2K)
        image8K = view.findViewById(R.id.image8K)
        size2K = view.findViewById(R.id.size2K)
        size8K = view.findViewById(R.id.size8K)
        overlay2k = view.findViewById(R.id.overlay2k)
        overlay8K = view.findViewById(R.id.overlay8k)

        setHtml(
            cameraInfo,
            "Camera " + cameraId + "<br /><small>" + args!!.width + "*" + args!!.height + "</small>"
        )
        view.findViewById<View>(R.id.info).setOnClickListener(this)

        val settingsBtn = view.findViewById<ImageButton>(R.id.skip_settings)
        settingsBtn.setOnClickListener {
            Settings.SKIP_SETTINGS = !Settings.SKIP_SETTINGS
            syncSettingsBtnIcon(it as ImageButton)
        }
        syncSettingsBtnIcon(settingsBtn)

        finishListener = object : TranslateFinishListener {
            override fun onFinish(result: TranslateResult) {
                size2K.text = "${result.size2K}"
                size8K.text = "${result.size8K}"
                lifecycleScope.launch(Dispatchers.Default) {
                    val bmp2k =
                        getBitmapImageFromYUV(result.buffer2K, result.size2K.width, result.size2K.height)
                    val bmp8k =
                        getBitmapImageFromYUV(result.buffer8K, result.size8K.width, result.size8K.height)
                    overlay2k.post(animationBlinkTask2k)
                    overlay8K.post(animationBlinkTask8k)
                    withContext(Dispatchers.Main) {
                        image2K.setImageBitmap(bmp2k)
                        image8K.setImageBitmap(bmp8k)
                    }
                    val savePath = saveBitmap(bmp2k, bmp8k)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "result saved at: $savePath",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    private fun syncSettingsBtnIcon(btn: ImageButton) {
        btn.setImageDrawable(if (Settings.SKIP_SETTINGS) skiSettingsIcon else showSettingIcon)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (mTextureView!!.isAvailable) {
            openCamera(mTextureView!!.width, mTextureView!!.height)
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        //modelUtils.closeTagDetectionSession(tag);
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    @SuppressLint("NonConstantResourceId")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.video -> {
                if (mIsRecordingVideo) {
                    mButtonVideo!!.setImageDrawable(toCaptureBg)
                    stopRecordingVideo()
                } else {
                    mButtonVideo!!.setImageDrawable(capturingBg)
                    startRecordingVideo()
                }
            }
            R.id.info -> {
            }
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Tries to open a [CameraDevice]. The result is listened by `mStateCallback`.
     */
    var cameraId: String? = null
    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        val activity: Activity? = activity
        if (null == activity || activity.isFinishing) {
            return
        }
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            Log.d(TAG, "tryAcquire")
            // 检测 Camera 是否被锁住
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            // 获取CameraId
            val cameraIdCount = manager.cameraIdList
            Log.d(TAG, "cameraIdCount.length: " + cameraIdCount.size)

            // Choose the sizes for camera preview and video recording
            Log.d(TAG, "cameraId: $cameraId")
            // 获取Camera的特征
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (map == null) {
                throw RuntimeException("Cannot get available preview/video sizes")
            }
            mVideoSize = chooseVideoSize(
                map.getOutputSizes(
                    MediaRecorder::class.java
                )
            )
            mPreviewSize = chooseOptimalSize(
                map.getOutputSizes(
                    SurfaceTexture::class.java
                ),
                args.width, args.height, mVideoSize
            )
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView!!.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                mTextureView!!.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, mStateCallback, null)
        } catch (e: CameraAccessException) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show()
            activity.finish()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            //closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mMediaRecorder) {
                mMediaRecorder!!.release()
                mMediaRecorder = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable || null == mPreviewSize) {
            return
        }
        try {
            closePreviewSession()
            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surfaces: MutableList<Surface> = ArrayList()
            //texture.setOnFrameAvailableListener(listener1,mBackgroundHandler);
//            fpsRanges = Range.create(args.fps, args.fps)
//            mPreviewBuilder!!.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRanges)
            Log.d(TAG, " fps set 60")
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
            val imageSurface = imageReader!!.getSurface()
            surfaces.add(imageSurface)
            //            mPreviewBuilder.addTarget(imageSurface);
            mCameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        mPreviewSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        val activity: Activity? = activity
                        if (null != activity) {
                            Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private var lastAIFrameTimestamp: Long = 0
    private var lastFrameNumber: Long = 0
    private var currentFps = 0
    private fun needSendNextFrame(frame: Long): Boolean {
        val current = System.currentTimeMillis()
        val elapsedTime = current - lastAIFrameTimestamp
        if (elapsedTime >= Settings.AI_FRAME_CYCLE_TIME) {
            lastAIFrameTimestamp = current

            currentFps = (((frame - lastFrameNumber) * 1000f) / (elapsedTime)).toInt()
            lastFrameNumber = frame
            return true
        }
        return false
    }

    private val animationBlinkTask = Runnable {
        // Flash white animation
        overlay!!.setBackgroundColor(Color.argb(150, 255, 255, 255))
        // Wait for ANIMATION_FAST_MILLIS
        overlay!!.postDelayed(
            { overlay!!.background = null } // Remove white flash animation
            , CameraActivity.ANIMATION_FAST_MILLIS)
    }

    private fun setVideoStatus(status: String) {
        videoStatus!!.post { videoStatus!!.text = status }
    }

    private fun setAIResult(result: String) {
        aiResult!!.post { aiResult!!.text = result }
    }


    private fun doSendAIImage() {
        if (!Settings.isAllCameraReady()) {
            Settings.setCameraReady(args.cameraId)
        }
//        if (!Settings.isAllCameraReady()) {
//            return
//        }
        lifecycleScope.launch(Dispatchers.IO) {
            takePhoto()
        }
    }
    private val captureCallback: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                if (mIsRecordingVideo) {
                    setAIResult("")
                    setVideoStatus("Recording: " + msToReadable(System.currentTimeMillis() - recordingStartMillis))
                } else {
                    needSendNextFrame(frameNumber)
                    if (enableTranslate && !Settings.isCameraPushed(args.cameraId)) {
                        doSendAIImage()
                    }
                    setVideoStatus("Frame $frameNumber, FPS: $currentFps")
                }
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
            }
        }

    /**
     * Update the camera preview. [.startPreview] needs to be called in advance.
     */
    private fun updatePreview() {
        if (null == mCameraDevice) {
            return
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder)
            //            HandlerThread thread = new HandlerThread("CameraPreview");
//            thread.start();
            // 重复请求获取图像数据，常用于预览和连拍
            mPreviewSession!!.setRepeatingRequest(
                mPreviewBuilder!!.build(),
                captureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity: Activity? = activity
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f, 0f, mPreviewSize!!.height
                .toFloat(), mPreviewSize!!.width.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize!!.height,
                viewWidth.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val activity = activity ?: return
        //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath!!.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(getActivity())
        }
        mMediaRecorder!!.setOutputFile(mNextVideoAbsolutePath)
        mMediaRecorder!!.setVideoEncodingBitRate(5000000)
        mMediaRecorder!!.setVideoFrameRate(60)
        mMediaRecorder!!.setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        //mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        val rotation = activity.windowManager.defaultDisplay.rotation
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(
                DEFAULT_ORIENTATIONS[rotation]
            )
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(
                INVERSE_ORIENTATIONS[rotation]
            )
        }
        mMediaRecorder!!.prepare()
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private val imageReader: ImageReader by lazy {
        ImageReader.newInstance(
            args.width, args.height,
            Settings.AI_IMAGE_FORMAT, Settings.AI_IMAGE_SIZE
        )
    }
    @Suppress("ControlFlowWithEmptyBody")
    private fun waitForAIComplete() {
        while (takingPhoto.get()) {}
    }

    private val takingPhoto = AtomicBoolean(false)
    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    @SuppressLint("SetTextI18n")
    private fun takePhoto() {
        waitForAIComplete()
        takingPhoto.set(true)
        // Flush any images left in the image reader
//        while (imageReader.acquireLatestImage() != null) {}

        // Start a new image queue
        imageReader!!.setOnImageAvailableListener({ reader ->
            imageReader!!.setOnImageAvailableListener(null, null)
            val image = reader.acquireLatestImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            val imageSource = getImageAISource(image)
            image.close()
//            val result = aiAnalyzeImage(args.cameraId, imageSource)
            translateImage(args.cameraId, imageSource)
            takingPhoto.set(false)
//            aiResult.post {
//                aiResult.text = "AI: $result"
//            }
        }, imageReaderHandler)

        videoStatus.post(animationBlinkTask)
        val captureRequest = mPreviewSession!!.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        mPreviewSession!!.capture(captureRequest.build(), null, mBackgroundHandler)
    }


    private fun getVideoFilePath(context: Context?): String {
        //final File dir = context.getExternalFilesDir(null);
        val format = SimpleDateFormat("MMdd_hhmmss_SSS")
        val videoName = "API2_" + format.format(Date()) + ".mp4"
        return "/sdcard/DCIM/$videoName"
    }

    private fun startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable || null == mPreviewSize) {
            return
        }
        try {
            waitForAIComplete()
//            closePreviewSession()
            setUpMediaRecorder()
            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces: MutableList<Surface> = ArrayList()

            // Set up Surface for the camera preview
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mPreviewBuilder!!.addTarget(recorderSurface)



            /*imageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), 35, 2);
            imageReader.setOnImageAvailableListener(listener1, mBackgroundHandler);
            Surface imageSurface = imageReader.getSurface();
            surfaces.add(imageSurface);
            mPreviewBuilder.addTarget(imageSurface);*/

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        mPreviewSession = cameraCaptureSession
                        updatePreview()
                        activity!!.runOnUiThread { // UI
                            mIsRecordingVideo = true
                            recordingStartMillis = System.currentTimeMillis()
                            // Start recording
                            mMediaRecorder!!.start()
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        val activity: Activity? = activity
                        if (null != activity) {
                            Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession!!.close()
            mPreviewSession = null
        }
    }

    private fun stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false
        // Stop recording
        mMediaRecorder!!.stop()
        mMediaRecorder!!.reset()
        val activity: Activity? = activity
        if (null != activity) {
            Toast.makeText(
                activity, "Video saved: $mNextVideoAbsolutePath",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Video saved: $mNextVideoAbsolutePath")
        }
        mNextVideoAbsolutePath = null
        startPreview()
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height -
                        rhs.width.toLong() * rhs.height
            )
        }
    }
}