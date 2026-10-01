package com.chrischappelear.tuner.tuning

import com.chrischappelear.tuner.audio.PitchEstimate
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

enum class TunerStatus {
    /** Nothing sounding. */
    Idle,

    /** A note is being tracked. */
    Active,

    /** The note just stopped; its reading lingers, dimmed, before going idle. */
    Fading
}

data class TunerState(
    val status: TunerStatus = TunerStatus.Idle,
    val note: Note? = null,
    val frequency: Double = 0.0,
    /** Smoothed offset from [note] in cents; positive is sharp. */
    val cents: Double = 0.0,
    /** True once the note has held in tune long enough to call it tuned. */
    val locked: Boolean = false,
    /** Increments every time a new lock is reached, for one-shot feedback. */
    val lockCount: Int = 0,
    /** Index into [Tuning.strings] of the string being tuned, in a preset. */
    val stringIndex: Int? = null,
    /** Strings that have reached a lock since the tuning was chosen. */
    val tunedStrings: Set<Int> = emptySet(),
    val tuning: Tuning = Tunings.Chromatic,
    val a4: Double = NoteMath.DEFAULT_A4
)

/**
 * Turns raw per-frame pitch readings into a steady tuner reading.
 *
 * Synchronous and single-threaded; call [process] once per audio frame.
 */
class TuningProcessor(
    tuning: Tuning = Tunings.Chromatic,
    a4: Double = NoteMath.DEFAULT_A4
) {
    var tuning: Tuning = tuning
        private set
    var a4: Double = a4
        private set

    val history = PitchHistory()

    private val recent = ArrayDeque<Double>()
    private val filter = OneEuroFilter(minCutoff = 1.2, beta = 0.04)

    private var target: Note? = null
    private var targetString: Int? = null
    private var pendingKey: Int? = null
    private var pendingCount = 0
    private var silentFrames = 0
    private var lastPitchMs = 0L
    private var inTuneSinceMs: Long? = null
    private var locked = false
    private var lockCount = 0
    private var tunedStrings = emptySet<Int>()

    private var state = TunerState(tuning = tuning, a4 = a4)

    fun configure(tuning: Tuning, a4: Double) {
        if (tuning == this.tuning && a4 == this.a4) return
        if (tuning != this.tuning) tunedStrings = emptySet()
        this.tuning = tuning
        this.a4 = a4
        clearTarget()
        recent.clear()
        state = idleState()
    }

    /** Forget the current note and trace, e.g. when listening resumes after a pause. */
    fun reset() {
        clearTarget()
        recent.clear()
        history.clear()
        silentFrames = 0
        state = idleState()
    }

    fun process(estimate: PitchEstimate?, nowMs: Long): TunerState {
        val minClarity = if (state.status == TunerStatus.Active) SUSTAIN_CLARITY else ONSET_CLARITY
        val hasPitch = estimate != null && estimate.clarity >= minClarity && estimate.rms >= MIN_RMS

        state = if (hasPitch && !isStray(estimate!!)) onPitch(estimate.frequency, nowMs) else onSilence(nowMs)

        history.add(
            TracePoint(
                timeMs = nowMs,
                cents = if (state.status == TunerStatus.Active) state.cents.toFloat() else null,
                note = state.note.takeIf { state.status == TunerStatus.Active }
            )
        )
        return state
    }

    private fun onPitch(frequency: Double, nowMs: Long): TunerState {
        silentFrames = 0
        lastPitchMs = nowMs

        recent.addLast(frequency)
        if (recent.size > MEDIAN_FRAMES) recent.removeFirst()
        // Before announcing a new note, wait for a few frames that agree with each other,
        // so pick transients and taps don't flash up as notes.
        if (state.status != TunerStatus.Active && !isSteadyOnset()) return state

        val smoothedFrequency = recent.sorted()[recent.size / 2]
        val midi = NoteMath.frequencyToMidi(smoothedFrequency, a4)

        val candidateString = if (tuning.isChromatic) null else tuning.nearestString(midi)
        val candidate = candidateString?.let { tuning.strings[it] } ?: Note(midi.roundToInt())
        val candidateKey = candidateString ?: candidate.midi
        val current = target

        when {
            current == null || state.status != TunerStatus.Active -> {
                if (candidate != current || candidateString != targetString) setTarget(candidate, candidateString)
            }
            candidateKey != (targetString ?: current.midi) && abs(midi - current.midi) * 100 > SWITCH_CENTS -> {
                if (candidateKey == pendingKey) pendingCount++ else {
                    pendingKey = candidateKey
                    pendingCount = 1
                }
                if (pendingCount >= SWITCH_FRAMES) setTarget(candidate, candidateString)
            }
            else -> {
                pendingKey = null
                pendingCount = 0
            }
        }

        val note = target!!
        val cents = filter.filter((midi - note.midi) * 100, nowMs)
        updateLock(cents, nowMs)

        return TunerState(
            status = TunerStatus.Active,
            note = note,
            frequency = smoothedFrequency,
            cents = cents,
            locked = locked,
            lockCount = lockCount,
            stringIndex = targetString,
            tunedStrings = tunedStrings,
            tuning = tuning,
            a4 = a4
        )
    }

    private fun onSilence(nowMs: Long): TunerState {
        silentFrames++
        return when (state.status) {
            TunerStatus.Idle -> state
            TunerStatus.Active -> {
                // Ride out brief dropouts without flickering.
                if (silentFrames < DROPOUT_FRAMES) return state
                recent.clear()
                pendingKey = null
                pendingCount = 0
                inTuneSinceMs = null
                locked = false
                state.copy(status = TunerStatus.Fading, locked = false)
            }
            TunerStatus.Fading -> {
                if (nowMs - lastPitchMs < FADE_MS) return state
                clearTarget()
                idleState()
            }
        }
    }

    /**
     * While tracking, a murky frame that also disagrees with the running pitch is far
     * more likely a room reflection or a fumbled pluck than a real change; real retuning
     * keeps clarity high. Such frames are treated as brief dropouts.
     */
    private fun isStray(estimate: PitchEstimate): Boolean {
        if (state.status != TunerStatus.Active || recent.isEmpty()) return false
        if (estimate.clarity >= TRUSTED_CLARITY) return false
        val median = recent.sorted()[recent.size / 2]
        return abs(1200 * log2(estimate.frequency / median)) > STRAY_CENTS
    }

    private fun isSteadyOnset(): Boolean {
        if (recent.size < ONSET_FRAMES) return false
        val lastFew = recent.takeLast(ONSET_FRAMES)
        val spreadCents = 1200 * log2(lastFew.max() / lastFew.min())
        return spreadCents <= ONSET_SPREAD_CENTS
    }

    private fun updateLock(cents: Double, nowMs: Long) {
        val distance = abs(cents)
        when {
            distance <= LOCK_CENTS -> {
                val since = inTuneSinceMs ?: nowMs.also { inTuneSinceMs = it }
                if (!locked && nowMs - since >= LOCK_HOLD_MS) {
                    locked = true
                    lockCount++
                    targetString?.let { tunedStrings = tunedStrings + it }
                }
            }
            distance > UNLOCK_CENTS -> {
                inTuneSinceMs = null
                locked = false
            }
            !locked -> inTuneSinceMs = null
        }
    }

    private fun setTarget(note: Note, stringIndex: Int?) {
        target = note
        targetString = stringIndex
        pendingKey = null
        pendingCount = 0
        inTuneSinceMs = null
        locked = false
        filter.reset()
    }

    private fun clearTarget() {
        target = null
        targetString = null
        pendingKey = null
        pendingCount = 0
        inTuneSinceMs = null
        locked = false
        filter.reset()
    }

    private fun idleState() = TunerState(
        lockCount = lockCount,
        tunedStrings = tunedStrings,
        tuning = tuning,
        a4 = a4
    )

    companion object {
        const val ONSET_CLARITY = 0.80
        const val SUSTAIN_CLARITY = 0.60
        const val TRUSTED_CLARITY = 0.82
        const val STRAY_CENTS = 15.0
        const val MIN_RMS = 0.0003 // about -70 dBFS; the UNPROCESSED source runs quiet
        const val MEDIAN_FRAMES = 5
        const val ONSET_FRAMES = 3
        const val ONSET_SPREAD_CENTS = 35.0
        const val SWITCH_CENTS = 65
        const val SWITCH_FRAMES = 3
        const val LOCK_CENTS = 4.0
        const val UNLOCK_CENTS = 8.0
        const val LOCK_HOLD_MS = 400L
        const val DROPOUT_FRAMES = 4
        const val FADE_MS = 1_500L
    }
}
