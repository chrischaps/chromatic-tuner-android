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
 * Plays reference tones and the practice drone through one streaming [AudioTrack],
 * opened for the first sound and closed once the last has died away. A new pluck
 * crossfades over the old one rather than cutting it off.
 *
 * The microphone hears the speaker. A reference tone is brief, so [isGating] tells the
 * tuner to ignore what it hears while one sounds and for a moment after, until the
 * last of it has left both the speaker and the analysis window. The drone plays on
 * while you sing, so instead [droneSound] tells the tuner what to cancel.
 */
class TonePlayer(scope: CoroutineScope, private val audioManager: AudioManager?) {
    private sealed interface Request {
        data class Pluck(val tone: ReferenceTone) : Request
        data class Drone(val frequency: Double) : Request
        data object DroneOff : Request
        data object Stop : Request
    }

    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val _sounding = MutableStateFlow<ReferenceTone?>(null)

    /** The tone currently ringing, if any. */
    val sounding: StateFlow<ReferenceTone?> = _sounding.asStateFlow()

    private val _drone = MutableStateFlow<Note?>(null)

    /** The note the drone is on, while it plays. */
    val drone: StateFlow<Note?> = _drone.asStateFlow()

    /**
     * What the microphone may be hearing of the drone, from the moment it's asked for
     * until it has died away. Read from the recording thread.
     */
    @Volatile
    var droneSound: DroneSound? = null
        private set

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

    /** Starts the drone on [note], or moves it there if it's already playing. */
    fun startDrone(note: Note, frequency: Double) {
        _drone.value = note
        requests.trySend(Request.Drone(frequency))
    }

    fun stopDrone() {
        _drone.value = null
        requests.trySend(Request.DroneOff)
    }

    /** Silences everything, tones and drone. */
    fun stop() {
        _drone.value = null
        requests.trySend(Request.Stop)
    }

    fun isGating(nowMs: Long = SystemClock.elapsedRealtime()): Boolean = nowMs < gateUntilMs

    private suspend fun run() {
        while (true) {
            val first = requests.receive()
            if (first !is Request.Pluck && first !is Request.Drone) continue
            val track = openTrack()
            if (track == null) {
                gateUntilMs = 0L
                _drone.value = null
                continue
            }
            try {
                ring(track, first)
            } finally {
                track.release()
                _sounding.value = null
                droneSound = null
                gateUntilMs = SystemClock.elapsedRealtime() + GATE_TAIL_MS
            }
        }
    }

    /** Streams until every voice and the drone have finished, taking requests as they come. */
    private suspend fun ring(track: AudioTrack, first: Request) {
        val sampleRate = track.sampleRate
        val voices = mutableListOf<PluckVoice>()
        var drone: Tanpura? = null
        val fading = mutableListOf<Tanpura>()
        var volume = streamVolume()
        var volumeCheckedAt = SystemClock.elapsedRealtime()

        fun releaseDrone() {
            drone?.let {
                it.release()
                fading += it
            }
            drone = null
        }

        fun handle(request: Request) {
            when (request) {
                is Request.Pluck -> {
                    voices.forEach(PluckVoice::release)
                    voices += PluckVoice(request.tone.frequency, sampleRate)
                    gateUntilMs = Long.MAX_VALUE
                    _sounding.value = request.tone
                }
                is Request.Drone -> {
                    if (drone?.frequency == request.frequency) return
                    if (drone != null) {
                        // The old note is in the air a moment longer, and the comb won't take it.
                        gateUntilMs = maxOf(gateUntilMs, SystemClock.elapsedRealtime() + GATE_TAIL_MS)
                    }
                    releaseDrone()
                    drone = Tanpura(request.frequency, sampleRate)
                    droneSound = DroneSound(request.frequency, volume)
                }
                Request.DroneOff -> releaseDrone()
                Request.Stop -> {
                    voices.forEach(PluckVoice::release)
                    releaseDrone()
                }
            }
        }

        handle(first)
        val buffer = FloatArray(CHUNK_FRAMES)
        track.play()

        while (voices.isNotEmpty() || drone != null || fading.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            while (true) handle(requests.tryReceive().getOrNull() ?: break)

            buffer.fill(0f)
            voices.forEach { it.render(buffer) }
            drone?.render(buffer)
            fading.forEach { it.render(buffer) }
            for (i in buffer.indices) buffer[i] = buffer[i].coerceIn(-1f, 1f)

            val now = SystemClock.elapsedRealtime()
            if (voices.removeAll { it.finished } && voices.isEmpty()) {
                // The pluck is over, though the drone may play on.
                _sounding.value = null
                gateUntilMs = now + GATE_TAIL_MS
            }
            if (fading.removeAll { it.finished } && fading.isEmpty() && drone == null) {
                droneSound = null
                gateUntilMs = maxOf(gateUntilMs, now + GATE_TAIL_MS)
            }
            // Turning the volume changes how loud the drone is at the microphone.
            val playing = drone
            if (playing != null && now - volumeCheckedAt >= VOLUME_CHECK_MS) {
                volumeCheckedAt = now
                val latest = streamVolume()
                if (latest != volume) {
                    volume = latest
                    droneSound = DroneSound(playing.frequency, volume)
                }
            }

            val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.w(TAG, "AudioTrack.write failed: $written")
                _drone.value = null
                return
            }
        }
        track.stop()
    }

    private fun streamVolume() = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

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
        const val VOLUME_CHECK_MS = 250L

        /**
         * After the last sample is written: output latency (longer over Bluetooth) plus
         * the 85–170 ms analysis window still holding the tone.
         */
        const val GATE_TAIL_MS = 400L
    }
}
