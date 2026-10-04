package com.chrischappelear.tuner.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Microphone capture as a cold flow of pitch readings.
 *
 * The microphone is opened when collection starts and released when it stops, all
 * on the same worker thread, so the recording's lifetime is exactly the collector's.
 * Analysis uses a sliding window advanced by a hop, both set by the [CaptureProfile].
 * While the app's drone plays, [drone] says what it is, so it can be heard past.
 * A null reading means no clear pitch.
 */
class AudioRecorder(private val context: Context) {
    fun frames(profile: CaptureProfile, drone: () -> DroneSound? = { null }): Flow<PitchEstimate?> = flow {
        val windowSize = profile.windowSize
        val hopSize = profile.hopSize
        val config = openRecord(windowSize) ?: return@flow
        val (record, sampleRate) = config
        val detector = DroneCanceller(profile)
        val window = FloatArray(windowSize)
        val hop = FloatArray(hopSize)
        var filled = 0

        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < hopSize) {
                    val count = record.read(hop, read, hopSize - read, AudioRecord.READ_BLOCKING)
                    if (count < 0) {
                        Log.w(TAG, "AudioRecord.read failed: $count")
                        return@flow
                    }
                    read += count
                }

                System.arraycopy(window, hopSize, window, 0, windowSize - hopSize)
                System.arraycopy(hop, 0, window, windowSize - hopSize, hopSize)
                filled = minOf(filled + hopSize, windowSize)

                if (filled == windowSize) emit(detector.detect(window, sampleRate, drone()))
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission") // checked explicitly below
    private fun openRecord(windowSize: Int): Pair<AudioRecord, Int>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        for (sampleRate in SAMPLE_RATES) {
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
            if (minBuffer <= 0) continue
            val bufferBytes = maxOf(minBuffer * 2, windowSize * Float.SIZE_BYTES)

            val record = try {
                AudioRecord(audioSource(), sampleRate, CHANNEL, ENCODING, bufferBytes)
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord at $sampleRate Hz failed", e)
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to sampleRate
            record.release()
        }
        Log.e(TAG, "No usable microphone configuration")
        return null
    }

    /**
     * UNPROCESSED skips the gain control and noise suppression that the default MIC
     * source applies on many devices, which would otherwise smear a decaying note.
     * VOICE_RECOGNITION is required to be similarly raw, so it is the fallback.
     */
    private fun audioSource(): Int {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (unprocessed == "true") MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private companion object {
        const val TAG = "AudioRecorder"
        val SAMPLE_RATES = intArrayOf(48_000, 44_100)
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    }
}
