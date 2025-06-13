package mx.dev.cmg.android.wavedemo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.dev.cmg.android.wavedemo.ui.theme.WaveDemoTheme
import kotlin.math.PI
import kotlin.math.sin

@Preview
@Composable
fun WaveFormPreview() {
    WaveDemoTheme {
        AudioControlledWaveScreen()
    }
}

@Composable
fun AudioControlledWaveScreen(
    modifier: Modifier = Modifier,
    audioViewModel: AudioViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasAudioPermission by remember { mutableStateOf(false) } // You'd check actual permission

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
            if (isGranted) {
                audioViewModel.startRecording(context)
            } else {
                // Handle permission denial
            }
        }
    )

    val liveAmplitude by audioViewModel.audioAmplitude.collectAsState()

    // Smooth the amplitude changes for a nicer visual effect
    val smoothedAmplitude by animateFloatAsState(
        targetValue = liveAmplitude,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing), label = "amplitude"
    )

    LaunchedEffect(Unit) { // Request permission on launch
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Clean up when the composable leaves the composition
    // This is important if the ViewModel is not scoped to this screen.
    // If using hiltViewModel(), this might be handled by ViewModel's onCleared
    //    DisposableEffect(Unit) {
    //        onDispose {
    //            audioViewModel.stopRecording()
    //        }
    //    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(onClick = {
            if (hasAudioPermission) audioViewModel.startRecording(context)
            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }) {
            Text("Start Recording")
        }
        Button(onClick = { audioViewModel.stopRecording() }) {
            Text("Stop Recording")
        }

        WaveForm(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Increased height for better visualization
                .padding(top = 20.dp),
            waveColor = MaterialTheme.colorScheme.secondary,
            waveSpeed = 0.7f,
            amplitude = smoothedAmplitude, // Use the live amplitude
            frequency = 0.015f,            // Adjust for visual preference
            strokeWidth = 3.dp.value
        )
    }
}


@Composable
fun WaveForm(
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    waveSpeed: Float = 1f,         // Controls how fast the wave moves horizontally
    amplitude: Float = 50f,        // Max height of the wave from the center line
    frequency: Float = 0.02f,      // Controls how many wave cycles are visible
    strokeWidth: Float = 4.dp.value // Width of the wave line
) {
    val phaseShift = remember { Animatable(0f) }

    LaunchedEffect(waveSpeed) {
        phaseShift.animateTo(
            targetValue = (2 * PI).toFloat() * waveSpeed, // Animate one full cycle
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                ), // Adjust duration for speed
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val path = Path()
        path.moveTo(0f, centerY)

        for (x in 0..width.toInt()) {
            // The main sine wave equation: y = amplitude * sin(frequency * x + phase)
            // We add phaseShift.value to make the wave move
            val y = amplitude * sin(frequency * x + phaseShift.value)
            path.lineTo(x.toFloat(), centerY + y)
        }

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = strokeWidth)
        )
    }
}