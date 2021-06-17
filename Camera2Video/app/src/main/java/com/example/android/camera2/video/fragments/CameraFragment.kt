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

package com.example.android.camera2.video.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.*
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.fragment_camera2_video.view.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.Runnable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraFragment : Fragment() {

    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }
    private var _outputFile: File? = null
    /** File where the recording will be saved */
    private val outputFile: File
        get() = _outputFile!!

    /**
     * Setup a persistent [Surface] for the recorder so we can use it as an output target for the
     * camera session without preparing the recorder
     */
    private val recorderSurface: Surface by lazy {

        // Get a persistent Surface from MediaCodec, don't forget to release when done
        val surface = MediaCodec.createPersistentInputSurface()

        // Prepare and release a dummy MediaRecorder with our new surface
        // Required to allocate an appropriately sized buffer before passing the Surface as the
        //  output target to the capture session
        createRecorder(surface).apply {
            prepare()
            release()
        }
        surface
    }
    private var _recorder: MediaRecorder? = null
    /** Saves the video recording */
    private val recorder: MediaRecorder
        get() = _recorder!!

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            overlay.foreground = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            overlay.postDelayed({
                // Remove white flash animation
                overlay.foreground = null
                // Restart animation recursively
                overlay.postDelayed(animationTask, CameraActivity.ANIMATION_FAST_MILLIS)
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }
    private val animationBlinkTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            overlay.postDelayed({
                // Remove white flash animation
                overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** Where the camera preview is displayed */
    private lateinit var viewFinder: AutoFitSurfaceView

    /** Overlay on top of the camera preview */
    private lateinit var overlay: View

    private var _session: CameraCaptureSession? = null
        set(value) {
            field = value
            initSession()
        }
    /** Captures frames from a [CameraDevice] for our video recording */
    private val session: CameraCaptureSession
        get() = _session!!

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice


    private lateinit var videoStatus: TextView
    private lateinit var aiResult: TextView

    /** Requests used for preview only in the [CameraCaptureSession] */
    private var _previewRequest: CaptureRequest? = null
    private val previewRequest: CaptureRequest
        get() = _previewRequest!!
    private fun initSession() {
        // Capture request holds references to target surfaces
        _previewRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            // Add the preview surface target
            addTarget(viewFinder.holder.surface)
        }.build()

        // Capture request holds references to target surfaces
        _recordRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(viewFinder.holder.surface)
            addTarget(recorderSurface)
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(args.fps, args.fps))
        }.build()

        _recorder?.release()
        _recorder = createRecorder(recorderSurface)
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private var _recordRequest: CaptureRequest? = null
    private val recordRequest: CaptureRequest
        get() = _recordRequest!!

    private var recordingStartMillis: Long = 0L

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_camera, container, false)

    private fun setVideoStatus(status: String) {
        videoStatus.post {
            videoStatus.text = status
        }
    }

    private lateinit var mPreviewSize: Size
    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        overlay = view.findViewById(R.id.overlay)
        viewFinder = view.findViewById(R.id.view_finder)
        videoStatus = view.findViewById(R.id.video_status)
        aiResult = view.findViewById(R.id.ai_result)
        capture_button.background = null
        capture_button.setImageDrawable(toCaptureBg)
        capture_button.scaleType = ImageView.ScaleType.FIT_CENTER

        val cameraInfo = view.findViewById<TextView>(R.id.camera_info)
        setHtml(cameraInfo, "Camera ${args.cameraId} <br /><small>${args.width}*${args.height}</small>")

        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                        viewFinder.display, characteristics, SurfaceHolder::class.java)
                mPreviewSize = previewSize
                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                viewFinder.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                viewFinder.post { initializeCamera() }
            }
        })

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                orientation -> Log.d(TAG, "Orientation changed: $orientation")
            })
        }
    }

    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    private fun createRecorder(surface: Surface) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        if (args.fps > 0) setVideoFrameRate(args.fps)
        setVideoSize(args.width, args.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setInputSurface(surface)
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
        imageReader.setOnImageAvailableListener({ reader ->
            imageReader.setOnImageAvailableListener(null, null)
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

        viewFinder.post(animationBlinkTask)
        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        session.capture(captureRequest.build(), null, cameraHandler)
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
    private val captureListener = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            if (capturing) {
                val ms = System.currentTimeMillis()
                setVideoStatus("Recording: ${msToReadable(ms - recordingStartMillis)}")
                aiResult.post {
                    aiResult.text = ""
                }
            } else {
                println("${args.cameraId} frame: $frameNumber")
                needSendNextFrame(frameNumber)
                if (enableTranslate && !Settings.isCameraPushed(args.cameraId)) {
                    doSendAIImage()
                }
                setVideoStatus("Frame $frameNumber, FPS: $currentFps")
            }
        }
    }
    @Suppress("ControlFlowWithEmptyBody")
    private fun waitForAIComplete() {
        while (takingPhoto.get()) {}
    }
    private var capturing = false
        set(value) {
            field = value
            waitForAIComplete()
            if (value) {
                capture_button.setImageDrawable(capturingBg)
                startCapture()
            } else {
                capture_button.setImageDrawable(toCaptureBg)
                stopCapture()
            }
        }
    private fun toggleCapture() {
        capturing = !capturing
        capture_button.background = null
    }

    private fun doSendAIImage() {
        if (!Settings.isAllCameraReady()) {
            Settings.setCameraReady(args.cameraId)
        }
        if (!Settings.isAllCameraReady()) {
//            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            takePhoto()
        }
    }

    private fun startCapture() {
        lifecycleScope.launch(Dispatchers.IO) {

            // Prevents screen rotation during the video recording
            requireActivity().requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LOCKED

            recordingStartMillis = System.currentTimeMillis()
            // Start recording repeating requests, which will stop the ongoing preview
            //  repeating requests without having to explicitly call `session.stopRepeating`
            session.setRepeatingRequest(recordRequest, captureListener, cameraHandler)

            // Finalizes recorder setup and starts recording
            recorder.apply {
                // Sets output orientation based on current sensor value at start time
                relativeOrientation.value?.let { setOrientationHint(it) }
                try {
                    prepare()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                start()
            }
            Log.d(TAG, "Recording started")

            // Starts recording animation
//            overlay.post(animationTask)
        }
    }
    private fun stopCapture() {
        lifecycleScope.launch(Dispatchers.IO) {

            // Unlocks screen rotation after recording finished
            requireActivity().requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
            val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
            if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
            }

            Log.d(TAG, "Recording stopped. Output file: $outputFile")
            recorder.stop()
            try {
                recorder.reset()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            startPreview()
            // Removes recording animation
            overlay.removeCallbacks(animationTask)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireActivity(), "MP4 saved at: $outputFile", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private val imageReader: ImageReader by lazy {
//        val size = characteristics.get(
//            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
//            .getOutputSizes(Settings.AI_IMAGE_FORMAT).maxBy { it.height * it.width }!!
        val imageReader = ImageReader.newInstance(args.width, args.height, Settings.AI_IMAGE_FORMAT, Settings.AI_IMAGE_SIZE)
        imageReader
    }
    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating request
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {

        // Open the selected camera
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        startPreview()
        // React to user touching the capture button
        capture_button.setOnClickListener {
            toggleCapture()
        }
    }
    private suspend fun startPreview() {
        _session?.close()
        _outputFile = createFile(requireContext(), "mp4")
        // Creates list of Surfaces where the camera will output frames
        val targets = if (Settings.ENABLE_AI_ANALYZE) {
            listOf(viewFinder.holder.surface, recorderSurface, imageReader.surface)
        } else {
            listOf(viewFinder.holder.surface, recorderSurface)
        }
        // Start a capture session using our open camera and list of Surfaces where frames will go
        _session = createCaptureSession(camera, targets, cameraHandler)

        // Sends the capture request as frequently as possible until the session is torn down or
        //  session.stopRepeating() is called
        session.setRepeatingRequest(previewRequest, captureListener, cameraHandler)

    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
            manager: CameraManager,
            cameraId: String,
            handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                setVideoStatus("Camera opened!")
                cont.resume(device)
            }

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                setVideoStatus("Camera disconnected!")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when(error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Creates a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine)
     */
    private suspend fun createCaptureSession(
            device: CameraDevice,
            targets: List<Surface>,
            handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Creates a capture session using the predefined targets, and defines a session state
        // callback which resumes the coroutine once the session is configured
        device.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageReaderThread.quitSafely()
        cameraThread.quitSafely()
        if (_recorder != null) {
            _recorder?.release()
            recorderSurface.release()
        }
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }
    }
}