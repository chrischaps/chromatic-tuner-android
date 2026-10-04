package com.chrischappelear.tuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.chrischappelear.tuner.tuning.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A reference note being played.
 *
 * @property stringIndex the string it belongs to, or null for the chromatic picker
 * @property id distinct for every pluck, so replaying the same note restarts its animation
 */
data class ReferenceTone(
    val note: Note,
    val cents: Int,
    val frequency: Double,
    val stringIndex: Int?,
    val id: Long
)

/**
 * Plays reference tones through one streaming [AudioTrack], opened for the first
 * pluck and closed once the last has died away. A new pluck crossfades over the old
 * one rather than cutting it off.
 *
 * The microphone hears the speaker, so [isGating] tells the tuner to ignore what it
 * hears while a tone sounds and for a moment after, until the last of it has left
 * both the speaker and the analysis window.
 */
class TonePlayer(scope: CoroutineScope) {
    private sealed interface Request {
        data class Pluck(val tone: ReferenceTone) : Request
        data object Stop : Request
    }

    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val _sounding = MutableStateFlow<ReferenceTone?>(null)

    /** The tone currently ringing, if any. */
    val sounding: StateFlow<ReferenceTone?> = _sounding.asStateFlow()

    @Volatile
    private var gateUntilMs = 0L
    private var nextId = 0L

    init {
        scope.launch(Dispatchers.IO) { run() }
    }

    fun play(note: Note, cents: Int, frequency: Double, stringIndex: Int?) {
        // Close the gate now, not when the audio thread gets to it, so no frame slips by.
        gateUntilMs = Long.MAX_VALUE
        requests.trySend(Request.Pluck(ReferenceTone(note, cents, frequency, stringIndex, nextId++)))
    }

    fun stop() {
        requests.trySend(Request.Stop)
    }

    fun isGating(nowMs: Long = SystemClock.elapsedRealtime()): Boolean = nowMs < gateUntilMs

    private suspend fun run() {
        while (true) {
            val first = requests.receive()
            if (first !is Request.Pluck) continue
            val track = openTrack()
            if (track == null) {
                gateUntilMs = 0L
                continue
            }
            try {
                ring(track, first.tone)
            } finally {
                track.release()
                _sounding.value = null
                gateUntilMs = SystemClock.elapsedRealtime() + GATE_TAIL_MS
            }
        }
    }

    /** Streams until every voice has finished, taking new plucks as they come. */
    private suspend fun ring(track: AudioTrack, firstTone: ReferenceTone) {
        val sampleRate = track.sampleRate
        val voices = mutableListOf(PluckVoice(firstTone.frequency, sampleRate))
        gateUntilMs = Long.MAX_VALUE
        _sounding.value = firstTone
        val buffer = FloatArray(CHUNK_FRAMES)
        track.play()

        while (voices.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            while (true) {
                when (val request = requests.tryReceive().getOrNull() ?: break) {
                    is Request.Pluck -> {
                        voices.forEach(PluckVoice::release)
                        voices += PluckVoice(request.tone.frequency, sampleRate)
                        gateUntilMs = Long.MAX_VALUE
                        _sounding.value = request.tone
                    }
                    Request.Stop -> voices.forEach(PluckVoice::release)
                }
            }

            buffer.fill(0f)
            voices.forEach { it.render(buffer) }
            voices.removeAll { it.finished }
            for (i in buffer.indices) buffer[i] = buffer[i].coerceIn(-1f, 1f)

            val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.w(TAG, "AudioTrack.write failed: $written")
                return
            }
        }
        track.stop()
    }

    private fun openTrack(): AudioTrack? {
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        if (minBuffer <= 0) return null
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL)
                        .setEncoding(ENCODING)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minBuffer * 2, CHUNK_FRAMES * 2 * Float.SIZE_BYTES))
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack at $sampleRate Hz failed", e)
            return null
        }
        if (track.state == AudioTrack.STATE_INITIALIZED) return track
        track.release()
        return null
    }

    private companion object {
        const val TAG = "TonePlayer"
        const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        const val CHUNK_FRAMES = 512

        /**
         * After the last sample is written: output latency (longer over Bluetooth) plus
         * the 85–170 ms analysis window still holding the tone.
         */
        const val GATE_TAIL_MS = 400L
    }
}
