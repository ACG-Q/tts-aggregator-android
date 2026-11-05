package com.pulse.tts.viewmodel

import com.pulse.tts.data.AppSettings
import com.pulse.tts.data.AppSettingsOptions
import com.pulse.tts.data.CallMode
import com.pulse.tts.data.ThemeMode
import com.pulse.tts.data.UiStyle
import com.pulse.tts.playback.ReadTuning
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCyclesTest {

    @Test
    fun stepOption_moves_within_range() {
        assertEquals(5, stepOption(1, AppSettingsOptions.COOLDOWN_MINUTES, 1))
        assertEquals(60, stepOption(30, AppSettingsOptions.COOLDOWN_MINUTES, 1))
    }

    @Test
    fun stepOption_clamps_at_both_ends() {
        assertEquals(1, stepOption(1, AppSettingsOptions.COOLDOWN_MINUTES, -1))
        assertEquals(60, stepOption(60, AppSettingsOptions.COOLDOWN_MINUTES, 1))
    }

    @Test
    fun stepOption_unknown_value_starts_from_first() {
        assertEquals(5, stepOption(7, AppSettingsOptions.COOLDOWN_MINUTES, 1))
        assertEquals(1, stepOption(7, AppSettingsOptions.COOLDOWN_MINUTES, -1))
    }

    @Test
    fun stepOption_empty_options_keeps_value() {
        assertEquals(42, stepOption(42, emptyList<Int>(), 1))
    }

    @Test
    fun rate_and_pitch_steps_match_tuning_lists() {
        assertEquals(0, stepOption(-20, ReadTuning.RATES, 1))
        assertEquals(50, stepOption(50, ReadTuning.RATES, 1))
        assertEquals(-20, stepOption(0, ReadTuning.PITCHES, -1))
    }

    @Test
    fun mode_cycles_auto_group_manual() {
        assertEquals(CallMode.GROUP, CallMode.AUTO.nextMode())
        assertEquals(CallMode.MANUAL, CallMode.GROUP.nextMode())
        assertEquals(CallMode.AUTO, CallMode.MANUAL.nextMode())
    }

    @Test
    fun theme_cycles_system_light_dark() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.next())
    }

    @Test
    fun sleep_steps_from_off_to_first_slot_and_clamps() {
        assertEquals(15, stepOption(0, AppSettingsOptions.SLEEP_MINUTES, 1))
        assertEquals(0, stepOption(0, AppSettingsOptions.SLEEP_MINUTES, -1))
        assertEquals(60, stepOption(60, AppSettingsOptions.SLEEP_MINUTES, 1))
    }

    @Test
    fun sleep_cycle_wraps_back_to_off() {
        assertEquals(15, cycleOption(0, AppSettingsOptions.SLEEP_MINUTES))
        assertEquals(30, cycleOption(15, AppSettingsOptions.SLEEP_MINUTES))
        assertEquals(60, cycleOption(30, AppSettingsOptions.SLEEP_MINUTES))
        assertEquals(0, cycleOption(60, AppSettingsOptions.SLEEP_MINUTES))
    }

    @Test
    fun cycleOption_unknown_value_starts_from_second() {
        assertEquals(15, cycleOption(7, AppSettingsOptions.SLEEP_MINUTES))
        assertEquals(42, cycleOption(42, emptyList<Int>()))
    }

    @Test
    fun ui_style_defaults_to_standard() {
        assertEquals(UiStyle.STANDARD, AppSettings().uiStyle)
    }
}
