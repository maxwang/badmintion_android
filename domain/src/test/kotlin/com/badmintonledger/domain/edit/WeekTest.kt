package com.badmintonledger.domain.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekTest {
    @Test
    fun `week starts on Monday`() {
        // 2026-07-04 is Saturday, 2026-07-05 is Sunday -> same week (Monday 2026-06-29)
        assertEquals("2026-06-29", weekStart("2026-07-04"))
        assertEquals("2026-06-29", weekStart("2026-07-05"))
        // 2026-07-06 is Monday -> a new week
        assertEquals("2026-07-06", weekStart("2026-07-06"))
    }
}
