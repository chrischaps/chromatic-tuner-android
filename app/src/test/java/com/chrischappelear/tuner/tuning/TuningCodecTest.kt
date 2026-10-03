package com.chrischappelear.tuner.tuning

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningCodecTest {
    private fun notes(text: String) = text.split(' ').map { TuningString(Note.parse(it)) }

    @Test
    fun roundTrips() {
        val tunings = listOf(
            Tunings.custom("custom_1", "Open C", notes("C2 G2 C3 G3 C4 E4"), flats = false),
            Tunings.custom("custom_2", "Bass drop D · ♭", notes("D1 A1 D2 G2"), flats = true),
            Tunings.custom("custom_3", "", notes("A4"), flats = false)
        )
        assertEquals(tunings, TuningCodec.decode(TuningCodec.encode(tunings)))
    }

    @Test
    fun microtonalOffsetsRoundTrip() {
        val strings = listOf(TuningString(Note(48)), TuningString(Note(64), -14), TuningString(Note(70), 50))
        val tuning = Tunings.custom("custom_j", "Just", strings, flats = false)
        val text = TuningCodec.encode(listOf(tuning))
        assertEquals("custom_j\tJust\t0\t48 64:-14 70:50", text)
        assertEquals(listOf(tuning), TuningCodec.decode(text))
    }

    @Test
    fun outOfRangeOffsetsAreSkipped() {
        val text = "custom_a\tOk\t0\t40:-50\ncustom_b\tToo far\t0\t40:51\ncustom_c\tGarbled\t0\t40:1:2"
        assertEquals(listOf("custom_a"), TuningCodec.decode(text).map { it.id })
    }

    @Test
    fun controlCharactersInNamesAreFlattened() {
        val tuning = Tunings.custom("custom_1", "two\tline\nname", notes("E2"), flats = false)
        val decoded = TuningCodec.decode(TuningCodec.encode(listOf(tuning)))
        assertEquals("two line name", decoded.single().variant)
    }

    @Test
    fun malformedLinesAreSkipped() {
        val text = listOf(
            "custom_ok\tGood\t0\t40 45",
            "too\tfew\tfields",
            "custom_x\tBad note\t0\t40 forty",
            "custom_low\tToo low\t0\t10",
            "custom_many\tToo many\t0\t40 41 42 43 44 45 46 47 48",
            "\tNo id\t0\t40",
            ""
        ).joinToString("\n")
        assertEquals(listOf("custom_ok"), TuningCodec.decode(text).map { it.id })
    }

    @Test
    fun emptyTextHasNoTunings() {
        assertEquals(emptyList<Tuning>(), TuningCodec.decode(""))
        assertEquals("", TuningCodec.encode(emptyList()))
    }
}
