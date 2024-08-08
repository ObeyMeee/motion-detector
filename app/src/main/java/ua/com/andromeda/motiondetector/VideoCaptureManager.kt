package ua.com.andromeda.motiondetector

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.net.Uri
import android.util.Log
import android.util.Range
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.compositionLocalOf
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.bytedeco.javacv.FFmpegFrameGrabber
import ua.com.andromeda.motiondetector.utils.BackflipDetector
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val TAG = VideoCaptureManager::class.simpleName

class VideoCaptureManager private constructor(private val builder: Builder) :
    LifecycleEventObserver, PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var videoCapture: VideoCapture<Recorder>

    private var activeRecording: Recording? = null

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    var listener: Listener? = null

    private val detectionResults = mutableListOf<PoseLandmarkerHelper.ResultBundle>()

    init {
        getLifecycle().addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                cameraProviderFuture = ProcessCameraProvider.getInstance(getContext())
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    queryCameraInfo(source, cameraProvider)
                }, ContextCompat.getMainExecutor(getContext()))

                // Initialize our background executor
                backgroundExecutor = Executors.newSingleThreadExecutor()

                backgroundExecutor.execute {
                    poseLandmarkerHelper = PoseLandmarkerHelper(
                        context = getContext(),
                        poseLandmarkerHelperListener = this
                    )
                }
            }

            Lifecycle.Event.ON_RESUME -> {
                backgroundExecutor.execute {
                    if (this::poseLandmarkerHelper.isInitialized) {
                        if (poseLandmarkerHelper.isClose()) {
                            poseLandmarkerHelper.setupPoseLandmarker()
                        }
                    }
                }
            }

            Lifecycle.Event.ON_PAUSE -> {
                if (this::poseLandmarkerHelper.isInitialized) {
                    // Close the PoseLandmarkerHelper and release resources
                    backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
                }
            }

            Lifecycle.Event.ON_DESTROY -> {
                // Shut down our background executor
                backgroundExecutor.shutdown()
                backgroundExecutor.awaitTermination(
                    Long.MAX_VALUE, TimeUnit.NANOSECONDS
                )
            }

            else -> Unit
        }
    }

    /**
     * Queries the capabilities of the FRONT and BACK camera lens
     * The result is stored in an array map.
     *
     * With this, we can determine if a camera lens is available or not,
     * and what capabilities the lens can support e.g flash support
     */
    private fun queryCameraInfo(
        lifecycleOwner: LifecycleOwner,
        cameraProvider: ProcessCameraProvider
    ) {
        val cameraLensInfo = HashMap<Int, CameraInfo>()
        arrayOf(CameraSelector.LENS_FACING_BACK, CameraSelector.LENS_FACING_FRONT).forEach { lens ->
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lens).build()
            if (cameraProvider.hasCamera(cameraSelector)) {
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector)
                if (lens == CameraSelector.LENS_FACING_BACK) {
                    cameraLensInfo[CameraSelector.LENS_FACING_BACK] = camera.cameraInfo
                } else if (lens == CameraSelector.LENS_FACING_FRONT) {
                    cameraLensInfo[CameraSelector.LENS_FACING_FRONT] = camera.cameraInfo
                }
            }
        }
        listener?.onInitialised(cameraLensInfo)
    }

    /**
     * Takes a [previewState] argument to determine the camera options
     *
     * Create a Preview.
     * Create Video Capture use case
     * Bind the selected camera and any use cases to the lifecycle.
     * Connect the Preview to the PreviewView.
     */
    fun showPreview(
        previewState: PreviewState,
        cameraPreview: PreviewView = getCameraPreview()
    ): View {
        getLifeCycleOwner().lifecycleScope.launchWhenResumed {
            val cameraProvider = cameraProviderFuture.await()
            cameraProvider.unbindAll()

            //Select a camera lens
            val cameraSelector: CameraSelector = CameraSelector.Builder()
                .requireLensFacing(previewState.cameraLens)
                .build()

            //Create Preview use case
            val preview: Preview = Preview.Builder()
                .setTargetFrameRate(Range(30, 30))
                .setTargetResolution(previewState.size)
                .build()
                .apply { setSurfaceProvider(cameraPreview.surfaceProvider) }

            //Create Video Capture use case
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                .setImageQueueDepth(50)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectPose(image, previewState)
                    }
                }
            cameraProvider.bindToLifecycle(
                getLifeCycleOwner(), cameraSelector, preview, videoCapture, imageAnalyzer,
            ).apply {
                cameraControl.enableTorch(previewState.torchState == TorchState.ON)
            }
        }
        return cameraPreview
    }

    @OptIn(ExperimentalPersistentRecording::class)
    private fun detectPose(imageProxy: ImageProxy, previewState: PreviewState) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = previewState.cameraLens == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    fun updatePreview(previewState: PreviewState, previewView: View) {
        showPreview(previewState, previewView as PreviewView)
    }

    private fun getCameraPreview() = PreviewView(getContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        keepScreenOn = true
    }

    private fun getLifecycle() = builder.lifecycleOwner?.lifecycle!!

    private fun getContext() = builder.context

    private fun getLifeCycleOwner() = builder.lifecycleOwner!!

    @SuppressLint("MissingPermission")
    fun startRecording(filePath: String) {
        val outputOptions = FileOutputOptions.Builder(File(filePath)).build()
        activeRecording = videoCapture.output
            .prepareRecording(getContext(), outputOptions)
            .apply { withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(getContext()), videoRecordingListener)
        poseLandmarkerHelper.isRecording = true
    }

    fun stopRecording() {
        activeRecording?.stop()
        poseLandmarkerHelper.isRecording = false
    }

    private val videoRecordingListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Finalize -> if (event.hasError()) {
                listener?.onError(event.cause)
            } else {
                Log.d(TAG, "${detectionResults.size} landmarks detected")
                val videoUri = event.outputResults.outputUri
                val retriever = MediaMetadataRetriever().apply {
                    setDataSource(getContext(),videoUri)
                }
                val frameRate = retriever.extractMetadata(METADATA_KEY_CAPTURE_FRAMERATE)?.toDouble() ?: 30.0
                Log.d(TAG, "Video duration: ${retriever.extractMetadata(METADATA_KEY_DURATION)} and frame rate: $frameRate")
                val jumps = BackflipDetector.detect(
                    detectionResults.map { it.landmarks }.filter { it.isNotEmpty() },
                    detectionResults.indices.toList(),
                    frameRate,
                )
                Log.d(TAG, "Jumps: $jumps")

                listener?.recordingCompleted(videoUri, jumps)
            }

            is VideoRecordEvent.Pause -> listener?.recordingPaused()
            is VideoRecordEvent.Status -> {
                listener?.onProgress(event.recordingStats.recordedDurationNanos.fromNanoToSeconds())
            }
        }
    }

    interface Listener {
        fun onInitialised(cameraLensInfo: HashMap<Int, CameraInfo>)
        fun onProgress(progress: Int)
        fun recordingPaused()
        fun recordingCompleted(outputUri: Uri, jumps: List<Int>)
        fun onError(throwable: Throwable?)
    }

    class Builder(val context: Context) {

        var lifecycleOwner: LifecycleOwner? = null
            private set

        fun registerLifecycleOwner(source: LifecycleOwner): Builder {
            this.lifecycleOwner = source
            return this
        }

        fun build(): VideoCaptureManager {
            requireNotNull(lifecycleOwner) { "Lifecycle owner is not set" }
            return VideoCaptureManager(this)
        }
    }

    private fun Long.fromNanoToSeconds() = (this / (1000 * 1000 * 1000)).toInt()

    override fun onError(error: String, errorCode: Int) {
        // TODO Auto-generated method stub
        Log.e(TAG, "Error occurred: $error")
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        detectionResults.add(resultBundle)
    }
}

val LocalVideoCaptureManager =
    compositionLocalOf<VideoCaptureManager> { error("No capture manager found!") }
