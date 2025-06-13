package mx.dev.cmg.android.wavedemo

// In a ViewModel or a dedicated audio processing class
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioViewModel : ViewModel() {

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude = _audioAmplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    @SuppressLint("MissingPermission") // Ensure permission is granted before calling
    fun startRecording(context: Context) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Handle permission not granted - ideally, this is checked before calling
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            // Handle initialization error
            return
        }

        audioRecord?.startRecording()

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize > 0) {
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = sqrt(sum / readSize)
                    // Normalize and scale the amplitude for visualization
                    // This scaling factor (e.g., 0.1f) will need tuning
                    _audioAmplitude.value = (rms / MAX_EXPECTED_RMS * MAX_WAVE_AMPLITUDE).toFloat()
                }
                delay(AUDIO_READ_INTERVAL_MS) // Adjust for desired responsiveness
            }
        }
    }

    fun stopRecording() {
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord?.stop()
        }
        audioRecord?.release()
        audioRecord = null
        recordingJob?.cancel()
        _audioAmplitude.value = 0f // Reset amplitude
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }

    companion object {
        private const val SAMPLE_RATE = 44100 // Hz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_READ_INTERVAL_MS = 50L // How often to read and update
        private const val MAX_EXPECTED_RMS = 5000.0 // Heuristic, adjust based on testing
        private const val MAX_WAVE_AMPLITUDE = 100f // Max amplitude for the visual wave
    }
}