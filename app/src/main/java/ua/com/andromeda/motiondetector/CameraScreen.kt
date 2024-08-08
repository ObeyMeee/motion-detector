package ua.com.andromeda.motiondetector

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraInfo
import androidx.camera.core.TorchState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.com.andromeda.motiondetector.composables.CameraCloseIcon
import ua.com.andromeda.motiondetector.composables.CameraFlipIcon
import ua.com.andromeda.motiondetector.composables.CameraRecordIcon
import ua.com.andromeda.motiondetector.composables.CameraStopIcon
import ua.com.andromeda.motiondetector.composables.CameraTorchIcon
import ua.com.andromeda.motiondetector.composables.Timer

private const val TAG = "CameraScreen"

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordingViewModel: RecordingViewModel = viewModel(factory = RecordingViewModel.factory)
    val state by recordingViewModel.state.collectAsState()
    var jumpsIndicies by remember {
        mutableStateOf(emptyList<Int>())
    }
    val listener = remember(recordingViewModel) {
        object : VideoCaptureManager.Listener {
            override fun onInitialised(cameraLensInfo: HashMap<Int, CameraInfo>) {
                recordingViewModel.onEvent(RecordingViewModel.Event.CameraInitialized(cameraLensInfo))
            }

            override fun recordingPaused() {
                recordingViewModel.onEvent(RecordingViewModel.Event.RecordingPaused)
            }

            override fun onProgress(progress: Int) {
                recordingViewModel.onEvent(RecordingViewModel.Event.OnProgress(progress))
            }

            override fun recordingCompleted(outputUri: Uri, jumps: List<Int>) {
                Log.d("CameraScreen", "Jumps: ${jumps.joinToString()}")
                jumpsIndicies = jumps
                recordingViewModel.onEvent(RecordingViewModel.Event.RecordingEnded(outputUri))
            }

            override fun onError(throwable: Throwable?) {
                recordingViewModel.onEvent(RecordingViewModel.Event.Error(throwable))
            }
        }
    }
    AnimatedVisibility(
        visible = jumpsIndicies.isNotEmpty()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Float.MAX_VALUE)
        ) {
            Text(
                text = "Jumps: ${jumpsIndicies.joinToString()}",
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(2.dp, Color.Red)
            )
        }
    }
    val captureManager = remember(recordingViewModel) {
        VideoCaptureManager.Builder(context)
            .registerLifecycleOwner(lifecycleOwner)
            .build()
            .apply { this.listener = listener }
    }

    val permissions =
        remember { listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) }
//    HandlePermissionsRequest(permissions = permissions, permissionsHandler = recordingViewModel.permissionsHandler)

    CompositionLocalProvider(LocalVideoCaptureManager provides captureManager) {
        VideoScreenContent(
//            allPermissionsGranted = state.multiplePermissionsState?.allPermissionsGranted ?: false,
            cameraLens = state.lens,
            torchState = state.torchState,
            hasFlashUnit = state.lensInfo[state.lens]?.hasFlashUnit() ?: false,
            hasDualCamera = state.lensInfo.size > 1,
            recordedLength = state.recordedLength,
            recordingStatus = state.recordingStatus,
            onEvent = recordingViewModel::onEvent
        )
    }

    LaunchedEffect(recordingViewModel) {
        recordingViewModel.effect.collect {
            when (it) {
                is RecordingViewModel.Effect.ShowMessage -> {
                    Log.e(TAG, context.getString(it.message))
                }

                is RecordingViewModel.Effect.RecordVideo -> captureManager.startRecording(it.filePath)
                RecordingViewModel.Effect.StopRecording -> captureManager.stopRecording()
            }
        }
    }

}

@Composable
private fun VideoScreenContent(
//    allPermissionsGranted: Boolean,
    cameraLens: Int?,
    @TorchState.State torchState: Int,
    hasFlashUnit: Boolean,
    hasDualCamera: Boolean,
    recordedLength: Int,
    recordingStatus: RecordingViewModel.RecordingStatus,
    onEvent: (RecordingViewModel.Event) -> Unit
) {
//    if (!allPermissionsGranted) {
//        RequestPermission(message = R.string.request_permissions) {
//            onEvent(RecordingViewModel.Event.PermissionRequired)
//        }
//    } else {
    Box(modifier = Modifier.fillMaxSize()) {
        cameraLens?.let {
            CameraPreview(lens = it, torchState = torchState)
            if (recordingStatus == RecordingViewModel.RecordingStatus.Idle) {
                CaptureHeader(
                    modifier = Modifier.align(Alignment.TopStart),
                    showFlashIcon = hasFlashUnit,
                    torchState = torchState,
                    onFlashTapped = { onEvent(RecordingViewModel.Event.FlashTapped) },
                )
            }
            AnimatedVisibility(
                visible = recordedLength > 0,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Timer(seconds = recordedLength)
            }
            RecordFooter(
                modifier = Modifier.align(Alignment.BottomStart),
                recordingStatus = recordingStatus,
                showFlipIcon = hasDualCamera,
                onRecordTapped = { onEvent(RecordingViewModel.Event.RecordTapped) },
                onStopTapped = { onEvent(RecordingViewModel.Event.StopTapped) },
                onFlipTapped = { onEvent(RecordingViewModel.Event.FlipTapped) }
            )
        }
    }
//    }
}

@Composable
internal fun CaptureHeader(
    modifier: Modifier = Modifier,
    showFlashIcon: Boolean,
    torchState: Int,
    onFlashTapped: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(8.dp)
            .then(modifier)
    ) {
        if (showFlashIcon) {
            CameraTorchIcon(torchState = torchState, onTapped = onFlashTapped)
        }
    }
}


@Composable
internal fun RecordFooter(
    modifier: Modifier = Modifier,
    recordingStatus: RecordingViewModel.RecordingStatus,
    showFlipIcon: Boolean,
    onRecordTapped: () -> Unit,
    onStopTapped: () -> Unit,
    onFlipTapped: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 40.dp)
    ) {
        when (recordingStatus) {
            RecordingViewModel.RecordingStatus.Idle -> {
                CameraRecordIcon(
                    modifier = Modifier.align(Alignment.Center),
                    onTapped = onRecordTapped
                )
            }

            RecordingViewModel.RecordingStatus.Paused -> {
                CameraStopIcon(modifier = Modifier.align(Alignment.Center), onTapped = onStopTapped)
            }

            RecordingViewModel.RecordingStatus.InProgress -> {
                CameraStopIcon(modifier = Modifier.align(Alignment.Center), onTapped = onStopTapped)
            }
        }

        if (showFlipIcon && recordingStatus == RecordingViewModel.RecordingStatus.Idle) {
            CameraFlipIcon(modifier = Modifier.align(Alignment.CenterEnd), onTapped = onFlipTapped)
        }
    }
}

@Composable
private fun CameraPreview(lens: Int, @TorchState.State torchState: Int) {
    val captureManager = LocalVideoCaptureManager.current
    BoxWithConstraints {
        AndroidView(
            factory = {
                captureManager.showPreview(
                    PreviewState(
                        cameraLens = lens,
                        torchState = torchState,
                        size = Size(this.minWidth.value.toInt(), this.maxHeight.value.toInt())
                    )
                )
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                captureManager.updatePreview(
                    PreviewState(cameraLens = lens, torchState = torchState),
                    it
                )
            }
        )
    }
}