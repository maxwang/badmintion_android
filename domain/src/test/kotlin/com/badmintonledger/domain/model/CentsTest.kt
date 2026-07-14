package com.badmintonledger.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CentsTest {
    @Test
    fun `dollars to cents conversion`() {
        assertEquals(7680L, dollarsToCents(76.8))
        assertEquals(60000L, dollarsToCents(600.0))
        assertEquals(2560L, dollarsToCents(25.6))
    }

    @Test
    fun `cents to dollars formatting`() {
        assertEquals("76.80", centsToDollars(7680))
        assertEquals("0.00", centsToDollars(0))
        assertEquals("-9.60", centsToDollars(-960))
        assertEquals("2404.00", centsToDollars(240400))
    }
}
