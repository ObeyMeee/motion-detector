package ua.com.andromeda.motiondetector

import android.app.Activity
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.com.andromeda.motiondetector.ui.theme.MotionDetectorTheme
import kotlin.time.measureTimedValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cameraPermissionRequest =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) {
                    // Camera permission denied
                    Log.e("MainActivity", "Camera permission denied")
                }
            }
        val audioPermissionRequest =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    setCameraPreview()
                } else {
                    Log.e("MainActivity", "Audio permission denied")
                }
            }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            setCameraPreview()
        } else {
            audioPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @SuppressLint("NewApi")
    private fun setCameraPreview() {
        setContent {
            MotionDetectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}
//
//@Composable
//private fun MainScreen(modifier: Modifier = Modifier) {
//    var jumpFrameNumber by remember {
//        mutableStateOf<Int?>(null)
//    }
//
//    val videoPickerLauncher = rememberVideoPickerLauncher {
//        jumpFrameNumber = it
//    }
//    Box(modifier.fillMaxSize()) {
//        Column(modifier = Modifier.align(Alignment.Center)) {
//            AnimatedVisibility(
//                visible = jumpFrameNumber != null,
//                enter = fadeIn() + slideInHorizontally()
//            ) {
//                Text("Detected frame number: $jumpFrameNumber")
//                Spacer(Modifier.height(12.dp))
//            }
//            Button(
//                onClick = {
//                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
//                        type = "video/*"
//                    }
//                    videoPickerLauncher.launch(intent)
//                }
//            ) {
//                Text("Import")
//            }
//        }
//    }
//}
//
//
//@Composable
//private fun rememberVideoPickerLauncher(onSuccess: (Int) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//    return rememberLauncherForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            val intent = result.data
//            intent?.data?.let { uri ->
//                val copiedVideoUri = copyVideoToInternalStorage(context, uri)
//                scope.launch(Dispatchers.IO) {
//                    val (resultTimeMillis, duration) = measureTimedValue {
//                        PoseLandmarkerHelper(context = context).detectVideoFile(copiedVideoUri)
//                    }
//                    Log.d("onSuccess", "${duration.inWholeSeconds}")
//                    onSuccess(resultTimeMillis.getOrNull(0) ?: 0)
//                }
//            }
//        }
//    }
//}