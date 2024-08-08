package ua.com.andromeda.motiondetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.net.toFile
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.bytedeco.javacv.FFmpegFrameGrabber
import ua.com.andromeda.motiondetector.utils.BackflipDetector
import java.io.FileInputStream
import kotlin.math.ceil


class PoseLandmarkerHelper(
    private val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Pose Landmarker will not change, a lazy val would be preferable.
    private var poseLandmarker: PoseLandmarker? = null
    var isRecording: Boolean = false

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    // Return running status of PoseLandmarkerHelper
    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    // Initialize the Pose landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(DEFAULT_POSE_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(DEFAULT_POSE_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(DEFAULT_POSE_PRESENCE_CONFIDENCE)
                .setNumPoses(Integer.MAX_VALUE)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            poseLandmarkerHelperListener?.onError("Pose Landmarker failed to initialize. See error logs for details")
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}")
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize. See error logs for " + "details",
                GPU_ERROR
            )
            Log.e(TAG, "Image classifier failed to load model with error: ${e.message}")
        }
    }

    // Convert the ImageProxy to MP Image and feed it to PoselandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (!isRecording) {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )

        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    // Return the landmark result to this PoseLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        poseLandmarkerHelperListener?.onResults(
            ResultBundle(result.landmarks().flatten(), inferenceTime)
        )
    }

    // Return errors thrown during detection to this PoseLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    fun detectVideoFile(videoUri: Uri): List<Int> {
        val originalFrameIndicies = mutableListOf<Int>()
        val landmarkSequences = mutableListOf<List<NormalizedLandmark>>()

        val retriever = MediaMetadataRetriever().apply {
            setDataSource(context, videoUri)
        }

        val durationMillis = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLong() ?: 0
        val inputStream = FileInputStream(videoUri.toFile())
        val grabber: FFmpegFrameGrabber = FFmpegFrameGrabber(inputStream)
        grabber.start()
        val frameRate = grabber.frameRate
        grabber.stop()
        val frameIntervalMillis = (1000 / frameRate).toLong()
        val totalFrames = calculateNumberOfFrames(frameRate, durationMillis)
        Log.d(TAG, "Starting detection")
        Log.d(TAG, "Frame rate is $frameRate")
        Log.d(TAG, "Frame interval millis is $frameIntervalMillis")
        for (i in (0..totalFrames)) {
            val timestampMillis = i * frameIntervalMillis
            val timestampMicros = timestampMillis * 1000
            val frame = retriever.getFrameAtTime(timestampMicros, OPTION_CLOSEST_SYNC)
            if (frame == null) {
                Log.d(TAG, "Couldn't find frame at $timestampMillis")
                continue
            }
            val argb8888Frame = if (frame.config == Bitmap.Config.ARGB_8888)
                frame
            else
                frame.copy(Bitmap.Config.ARGB_8888, false)

            // Convert the input Bitmap object to an MPImage object to run inference
            val mpImage = BitmapImageBuilder(argb8888Frame).build()
            val results = poseLandmarker?.detectForVideo(mpImage, timestampMillis)

            if (results != null && results.landmarks().isNotEmpty()) {
                landmarkSequences.add(results.landmarks().flatten())
                originalFrameIndicies.add(i)
            }
        }
        Log.d(TAG, "Ending detection")

//        val jumps = BackflipDetector.detect(landmarkSequences, originalFrameIndicies, frameRate)
        // number of frame / framerate

//        Log.d("PoseLandmarkerHelper", "jumps ==> $jumps")
//        return jumps
        return emptyList()
    }

    private fun calculateNumberOfFrames(frameRate: Double, durationMillis: Long): Int {
        // Convert duration from milliseconds to seconds
        val durationSeconds = durationMillis / 1000.0
        // Calculate the number of frames
        return ceil(frameRate * durationSeconds).toInt()
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F

        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val landmarks: List<NormalizedLandmark>,
        val inferenceTime: Long,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}