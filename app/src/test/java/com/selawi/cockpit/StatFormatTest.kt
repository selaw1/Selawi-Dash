package com.selawi.cockpit

import com.selawi.cockpit.features.vehicle.StatFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class StatFormatTest {
    @Test
    fun numbers() {
        assertEquals("0", StatFormat.number(0))
        assertEquals("902", StatFormat.number(902))
        assertEquals("9,999", StatFormat.number(9_999))
        assertEquals("10K", StatFormat.number(10_000))
        assertEquals("12.8K", StatFormat.number(12_846))
        assertEquals("13.1K", StatFormat.number(13_106))
        assertEquals("99.9K", StatFormat.number(99_940))
        assertEquals("100K", StatFormat.number(99_960))
        assertEquals("128K", StatFormat.number(128_400))
        assertEquals("1M", StatFormat.number(999_960))
        assertEquals("1.3M", StatFormat.number(1_250_000))
        assertEquals("12M", StatFormat.number(12_000_000))
    }

    @Test
    fun distances() {
        assertEquals("0.0", StatFormat.km(0.0))
        assertEquals("23.4", StatFormat.km(23.41))
        assertEquals("1,184", StatFormat.km(1_184.2))
        assertEquals("12.8K", StatFormat.km(12_846.0))
    }

    @Test
    fun durations() {
        assertEquals("45s", StatFormat.duration(45))
        assertEquals("41m", StatFormat.duration(2_460))
        assertEquals("1h 29m", StatFormat.duration(5_340))
        assertEquals("9h 52m", StatFormat.duration(35_520))
        assertEquals("13.9h", StatFormat.duration(49_920))
        assertEquals("128h", StatFormat.duration(460_800))
    }

    @Test
    fun everythingFitsSixCharacters() {
        val samples = listOf(9_999L, 12_846L, 99_940L, 128_400L, 999_999L, 1_250_000L, 987_654_321L)
        samples.forEach { assert(StatFormat.number(it).length <= 6) { StatFormat.number(it) } }
        listOf(59L, 3_599L, 35_999L, 359_999L, 3_599_999L).forEach { assert(StatFormat.duration(it).length <= 6) { StatFormat.duration(it) } }
    }
}
