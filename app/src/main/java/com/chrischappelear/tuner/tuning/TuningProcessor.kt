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
    /** That string's microtonal offset from [note]; [cents] is measured from note plus this. */
    val stringCents: Int = 0,
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
    /** Smooths the pitch line, in cents; unlike [filter], it carries on across note changes. */
    private val glideFilter = OneEuroFilter(minCutoff = 1.2, beta = 0.04)
    private var glideMidi: Double? = null

    private var target: Note? = null
    private var targetString: Int? = null
    /** The pitch tuned to: [target], plus the string's microtonal offset. */
    private var targetMidi = 0.0
    private var pendingKey: Int? = null
    private var pendingCount = 0
    private var silentFrames = 0
    private var octaveFrames = 0
    /** Whether the current run of octave readings began with a fresh attack. */
    private var octaveRunAttacked = false
    /** Recent window levels, to tell a new pluck from a ringing string. */
    private val recentRms = ArrayDeque<Double>()
    private var framesSinceOnset = NO_ONSET
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
        clearGlide()
        recent.clear()
        state = idleState()
    }

    /** Forget the current note and trace, e.g. when listening resumes after a pause. */
    fun reset() {
        clearTarget()
        clearGlide()
        recent.clear()
        recentRms.clear()
        framesSinceOnset = NO_ONSET
        history.clear()
        silentFrames = 0
        state = idleState()
    }

    fun process(estimate: PitchEstimate?, nowMs: Long): TunerState {
        trackOnset(estimate?.rms)
        val minClarity = if (state.status == TunerStatus.Active) SUSTAIN_CLARITY else ONSET_CLARITY
        val hasPitch = estimate != null && estimate.clarity >= minClarity && estimate.rms >= MIN_RMS

        state = if (!hasPitch) onSilence(nowMs) else {
            val frequency = resolveOctave(estimate!!.frequency)
            when {
                frequency == null -> state // hold the reading through an octave slip
                isStray(estimate.clarity, frequency) -> onSilence(nowMs)
                else -> onPitch(frequency, nowMs)
            }
        }

        val active = state.status == TunerStatus.Active
        history.add(
            TracePoint(
                timeMs = nowMs,
                cents = if (active) state.cents.toFloat() else null,
                note = state.note.takeIf { active },
                midi = glideMidi?.toFloat()?.takeIf { active }
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
        glideMidi = glideFilter.filter(midi * 100, nowMs) / 100

        val candidateString = if (tuning.isChromatic) null else tuning.nearestString(midi)
        val candidate = candidateString?.let { tuning.strings[it].note } ?: Note(midi.roundToInt())
        val candidateKey = candidateString ?: candidate.midi
        val current = target

        when {
            current == null || state.status != TunerStatus.Active -> {
                if (candidate != current || candidateString != targetString) setTarget(candidate, candidateString)
            }
            candidateKey != (targetString ?: current.midi) && abs(midi - targetMidi) * 100 > SWITCH_CENTS -> {
                if (candidateKey == pendingKey) pendingCount++ else {
                    pendingKey = candidateKey
                    pendingCount = 1
                }
                // Until the switch is confirmed, hold the last reading: an offset measured
                // against the old note (hundreds of cents) is meaningless and would flash up.
                if (pendingCount < SWITCH_FRAMES) return state
                setTarget(candidate, candidateString)
            }
            else -> {
                pendingKey = null
                pendingCount = 0
            }
        }

        val note = target!!
        val cents = filter.filter((midi - targetMidi) * 100, nowMs)
        updateLock(cents, nowMs)

        return TunerState(
            status = TunerStatus.Active,
            note = note,
            frequency = smoothedFrequency,
            cents = cents,
            locked = locked,
            lockCount = lockCount,
            stringIndex = targetString,
            stringCents = targetString?.let { tuning.strings[it].cents } ?: 0,
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
                clearGlide()
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
     * Decides what to do with a reading an octave from the running pitch: returns the
     * frequency to use, or null to hold the last reading.
     *
     * A brief transient (a click, a bump, string buzz) taints every analysis window that
     * overlaps it, up to window / hop = 4 frames, and can read as the octave below or
     * above with clarity to spare. So while tracking, a jump of almost exactly an octave
     * is held back unless it outlasts any single transient; a real octave change does.
     *
     * In a preset there is a second cause, which lasts far longer. A phone microphone
     * barely hears a low string's fundamental, and as the note rings its odd harmonics
     * can fade as well, until the sound really is periodic an octave up: a low E reads as
     * E3 for a second at a time, which the nearest-string rule then calls D3. A string
     * can't change octave without being plucked again, so in a preset an octave jump
     * that didn't begin with a fresh attack is folded back onto the string being tracked.
     * Chromatic doesn't fold, since a voice can leap an octave without one.
     */
    private fun resolveOctave(frequency: Double): Double? {
        if (state.status != TunerStatus.Active || recent.isEmpty()) {
            octaveFrames = 0
            return frequency
        }
        val median = recent.sorted()[recent.size / 2]
        val offset = 1200 * log2(frequency / median)
        if (abs(abs(offset) - 1200) > OCTAVE_TOLERANCE_CENTS) {
            octaveFrames = 0
            return frequency
        }
        if (octaveFrames == 0) octaveRunAttacked = framesSinceOnset <= ONSET_GRACE_FRAMES
        octaveFrames++
        if (!tuning.isChromatic && !octaveRunAttacked) return if (offset > 0) frequency / 2 else frequency * 2
        if (octaveFrames <= OCTAVE_CONFIRM_FRAMES) return null
        // It persisted: this is a real change of octave, so let the median start over.
        recent.clear()
        octaveFrames = 0
        return frequency
    }

    /**
     * A pluck shows as a jump in level over the last few windows; a ringing string only
     * decays. Frames with no reading at all still count as time passing.
     */
    private fun trackOnset(rms: Double?) {
        if (framesSinceOnset < NO_ONSET) framesSinceOnset++
        if (rms == null) return
        val floor = recentRms.minOrNull()
        if (floor != null && rms > ONSET_RISE * floor) framesSinceOnset = 0
        recentRms.addLast(rms)
        if (recentRms.size > ONSET_LOOKBACK_FRAMES) recentRms.removeFirst()
    }

    /**
     * While tracking, a murky frame that also disagrees with the running pitch is far
     * more likely a room reflection or a fumbled pluck than a real change; real retuning
     * keeps clarity high. Such frames are treated as brief dropouts.
     */
    private fun isStray(clarity: Double, frequency: Double): Boolean {
        if (state.status != TunerStatus.Active || recent.isEmpty()) return false
        if (clarity >= TRUSTED_CLARITY) return false
        val median = recent.sorted()[recent.size / 2]
        return abs(1200 * log2(frequency / median)) > STRAY_CENTS
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
        targetMidi = stringIndex?.let { tuning.strings[it].midi } ?: note.midi.toDouble()
        pendingKey = null
        pendingCount = 0
        inTuneSinceMs = null
        locked = false
        filter.reset()
    }

    private fun clearGlide() {
        glideMidi = null
        glideFilter.reset()
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
        // Both exceed the 4 frames a single transient can taint (window / hop in every CaptureProfile).
        const val DROPOUT_FRAMES = 6
        const val OCTAVE_CONFIRM_FRAMES = 6
        const val OCTAVE_TOLERANCE_CENTS = 40.0
        // A level 1.5× the quietest of the last 4 windows is a new attack; plucks measured
        // on a phone jump 5–15×, and a ringing string never rises. An octave run that starts
        // within 4 frames of one (a window's worth) belongs to that attack.
        const val ONSET_RISE = 1.5
        const val ONSET_LOOKBACK_FRAMES = 4
        const val ONSET_GRACE_FRAMES = 4
        private const val NO_ONSET = 1_000
        const val FADE_MS = 1_500L
    }
}
