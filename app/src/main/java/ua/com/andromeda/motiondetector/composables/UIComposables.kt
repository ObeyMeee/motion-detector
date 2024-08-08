package ua.com.andromeda.motiondetector.composables

import android.text.format.DateUtils
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.com.andromeda.motiondetector.R

@Composable
fun CameraCaptureIcon(modifier: Modifier, onTapped: () -> Unit) {
    TextButton(
        modifier = Modifier.then(modifier),
        onClick = { onTapped() },
        content = {
            Image(
                modifier = Modifier.size(dimensionResource(R.dimen.record_icon_size)),
                painter = painterResource(R.drawable.ic_capture),
                contentDescription = null
            )
        }
    )
}

@Composable
fun CameraPauseIcon(modifier: Modifier = Modifier, onTapped: () -> Unit) {
    TextButton(
        modifier = Modifier.then(modifier),
        onClick = { onTapped() },
        content = {
            Image(
                modifier = Modifier.size(dimensionResource(R.dimen.record_icon_size)),
                painter = painterResource(id = R.drawable.ic_pause),
                contentDescription = null
            )
        }
    )
}


@Composable
fun CameraPlayIcon(modifier: Modifier = Modifier, onTapped: () -> Unit) {
    TextButton(
        modifier = Modifier
            .then(modifier),
        onClick = { onTapped() },
        content = {
            Image(
                modifier = Modifier.size(dimensionResource(R.dimen.record_icon_size)),
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = ""
            )
        }
    )
}

@Composable
fun CameraRecordIcon(modifier: Modifier = Modifier, onTapped: () -> Unit) {
    PrimaryIcon(
        modifier = modifier,
        onClick = onTapped,
        contentModifier = Modifier
            .padding(4.dp)
            .background(
                color = Color.White,
                shape = CircleShape,
            ),
    )
}

@Composable
fun PrimaryIcon(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isTapped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isTapped) 1.4f else 1.3f,
        finishedListener = { _ ->
            isTapped = !isTapped
        }, label = ""
    )
    IconButton(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 2.dp,
                color = Color.White,
                shape = CircleShape,
            )
            .clip(CircleShape),
        onClick = {
            isTapped = !isTapped
            onClick()
        },
        content = {
            Box(contentModifier.size(dimensionResource(R.dimen.record_icon_size)))
        }
    )
}

@Composable
fun CameraStopIcon(modifier: Modifier = Modifier, onTapped: () -> Unit) {
    PrimaryIcon(
        modifier = modifier,
        onClick = onTapped,
        contentModifier = Modifier
            .padding(8.dp)
            .background(
                color = Color.Red,
                shape = MaterialTheme.shapes.small,
            ),
    )
}

@Composable
fun CameraFlipIcon(modifier: Modifier = Modifier, onTapped: () -> Unit) {
    var flipped by remember { mutableStateOf(false) }
    val rotationDegrees by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),

        )
    TextButton(
        modifier = Modifier
            .then(modifier),
        onClick = {
            flipped = !flipped
            onTapped()
        },
        content = {
            Image(
                painter = painterResource(id = R.drawable.ic_rotate),
                contentDescription = "",
                modifier = Modifier
                    .size(dimensionResource(R.dimen.flip_lens_icon_size))
                    .rotate(rotationDegrees)
                    .graphicsLayer {
                        rotationZ = rotationDegrees
                    }
            )
        }
    )
}

@Composable
fun CameraCloseIcon(modifier: Modifier = Modifier, onTapped: () -> Unit) {
    TextButton(
        modifier = Modifier
            .then(modifier),
        onClick = { onTapped() },
        content = {
            Image(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = ""
            )
        }
    )
}

@Composable
fun CameraTorchIcon(
    modifier: Modifier = Modifier,
    @TorchState.State torchState: Int,
    onTapped: () -> Unit
) {
    TextButton(
        modifier = Modifier
            .then(modifier),
        onClick = { onTapped() },
        content = {
            val drawable = if (torchState == TorchState.ON) {
                R.drawable.ic_flash_off
            } else {
                R.drawable.ic_flash_on
            }
            Image(
                painter = painterResource(id = drawable),
                contentDescription = "",
                modifier = Modifier.size(dimensionResource(R.dimen.flip_lens_icon_size))
            )
        }
    )
}

@Composable
fun CameraFlashIcon(
    modifier: Modifier = Modifier,
    @ImageCapture.FlashMode flashMode: Int,
    onTapped: () -> Unit
) {
    TextButton(
        modifier = Modifier
            .then(modifier),
        onClick = { onTapped() },
        content = {
            val drawable = when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_on
                ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                else -> R.drawable.ic_flash_off
            }
            Image(
                painter = painterResource(id = drawable),
                contentDescription = "",
                modifier = Modifier.size(dimensionResource(R.dimen.flip_lens_icon_size))
            )
        }
    )
}

@Composable
internal fun RequestPermission(message: Int, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onClick) {
            Text(text = stringResource(id = message))
        }
    }
}

@Composable
fun Timer(modifier: Modifier = Modifier, seconds: Int) {
    if (seconds > 0) {
        Box(
            modifier = Modifier
                .padding(vertical = 24.dp)
                .then(modifier)
        ) {
            Text(
                text = DateUtils.formatElapsedTime(seconds.toLong()),
                color = Color.White,
                modifier = Modifier
                    .background(color = Color.Red, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp)
                    .then(modifier)
            )
        }

    }
}