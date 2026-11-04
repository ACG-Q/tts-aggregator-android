package com.pulse.tts.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimelineTest {

    @Test
    fun split_keepsSentencePunctuation_andDropsBlankLines() {
        val text = "第一句。第二句！\n\n第三句没有标点"
        assertEquals(listOf("第一句。", "第二句！", "第三句没有标点"), ReadingTimeline.split(text))
    }

    @Test
    fun split_blankText_returnsEmptyList() {
        assertEquals(emptyList<String>(), ReadingTimeline.split("   "))
    }

    @Test
    fun indexAt_usesCharacterWeight() {
        val sentences = listOf("一二。", "三四五六七八九十。")
        assertEquals(0, ReadingTimeline.indexAt(sentences, 0f))
        assertEquals(0, ReadingTimeline.indexAt(sentences, 0.2f))
        assertEquals(1, ReadingTimeline.indexAt(sentences, 0.8f))
        assertEquals(1, ReadingTimeline.indexAt(sentences, 1f))
    }

    @Test
    fun indexAt_clampsOutOfRangeProgress() {
        val sentences = listOf("甲。", "乙。")
        assertEquals(0, ReadingTimeline.indexAt(sentences, -1f))
        assertEquals(1, ReadingTimeline.indexAt(sentences, 3f))
    }

    @Test
    fun format_padsMinutesAndSeconds() {
        assertEquals("00:00", ReadingTimeline.format(0))
        assertEquals("00:07", ReadingTimeline.format(7_400))
        assertEquals("12:05", ReadingTimeline.format(725_000))
    }

    @Test
    fun nextRate_cyclesThroughPresets() {
        assertEquals(0, ReadTuning.nextRate(-20))
        assertEquals(20, ReadTuning.nextRate(0))
        assertEquals(50, ReadTuning.nextRate(20))
        assertEquals(-20, ReadTuning.nextRate(50))
        assertEquals(0, ReadTuning.nextRate(999))
    }

    @Test
    fun nextPitch_cyclesAndFallsBackToStandard() {
        assertEquals(0, ReadTuning.nextPitch(-20))
        assertEquals(20, ReadTuning.nextPitch(0))
        assertEquals(-20, ReadTuning.nextPitch(20))
        assertEquals(0, ReadTuning.nextPitch(7))
    }

    @Test
    fun labels_mapToHumanReadableValues() {
        assertEquals("0.8×", ReadTuning.rateLabel(-20))
        assertEquals("1.2×", ReadTuning.rateLabel(20))
        assertEquals("1.5×", ReadTuning.rateLabel(50))
        assertEquals("1.0×", ReadTuning.rateLabel(0))
        assertEquals("低", ReadTuning.pitchLabel(-20))
        assertEquals("高", ReadTuning.pitchLabel(20))
        assertEquals("标准", ReadTuning.pitchLabel(0))
        assertEquals(1.5f, ReadTuning.playbackSpeed(50))
    }
}
