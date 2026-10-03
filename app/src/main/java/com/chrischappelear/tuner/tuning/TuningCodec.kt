package com.chrischappelear.tuner.tuning

/**
 * Stores custom tunings as text, one per line: `id \t name \t flats \t midi midi:cents …`.
 * Strings are MIDI numbers, so spelling never affects what is saved; a microtonal
 * string adds its offset in cents after a colon.
 */
object TuningCodec {
    fun encode(tunings: List<Tuning>): String = tunings.joinToString("\n") { tuning ->
        listOf(
            tuning.id,
            tuning.variant.replace(CONTROL, " "),
            if (tuning.flats) "1" else "0",
            tuning.strings.joinToString(" ") { if (it.cents == 0) "${it.note.midi}" else "${it.note.midi}:${it.cents}" }
        ).joinToString("\t")
    }

    /** Lines that don't describe a valid custom tuning are skipped. */
    fun decode(text: String): List<Tuning> = text.lineSequence().mapNotNull(::decodeLine).toList()

    private fun decodeLine(line: String): Tuning? {
        val fields = line.split('\t')
        if (fields.size != 4 || fields[0].isBlank()) return null
        val strings = fields[3].split(' ').map { decodeString(it) ?: return null }
        if (strings.size !in 1..Tunings.MAX_CUSTOM_STRINGS) return null
        return Tunings.custom(
            id = fields[0],
            name = fields[1],
            strings = strings,
            flats = fields[2] == "1"
        )
    }

    private fun decodeString(text: String): TuningString? {
        val parts = text.split(':')
        if (parts.size > 2) return null
        val midi = parts[0].toIntOrNull()?.takeIf { it in Tunings.CUSTOM_NOTE_RANGE } ?: return null
        val cents = if (parts.size == 1) 0 else parts[1].toIntOrNull() ?: return null
        if (cents !in -TuningString.MAX_CENTS..TuningString.MAX_CENTS) return null
        return TuningString(Note(midi), cents)
    }

    private val CONTROL = Regex("[\\t\\r\\n]")
}
