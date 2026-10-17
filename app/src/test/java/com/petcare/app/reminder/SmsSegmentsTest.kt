package com.petcare.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSegmentsTest {
    @Test
    fun `GSM text is charged per 160 then 153 septets`() {
        assertEquals(1, SmsSegments.parts("a".repeat(160)))
        assertEquals(2, SmsSegments.parts("a".repeat(161)))
        assertEquals(3, SmsSegments.parts("a".repeat(459)))
        assertEquals(4, SmsSegments.parts("a".repeat(460)))
    }

    @Test
    fun `extension characters cost two septets and shrink the safe part size`() {
        assertEquals(2, SmsSegments.septets("€"))
        assertEquals(1, SmsSegments.parts("€".repeat(80)))
        assertEquals(2, SmsSegments.parts("€".repeat(81)))
        assertEquals(4, SmsSegments.parts("a".repeat(455) + "€"))
    }

    @Test
    fun `one non-GSM character switches the whole message to UCS-2`() {
        val enDash = "a".repeat(69) + "–"
        assertFalse(SmsSegments.isGsm7(enDash))
        assertEquals(1, SmsSegments.parts(enDash))
        assertEquals(2, SmsSegments.parts(enDash + "a"))
        assertEquals(3, SmsSegments.parts("–" + "a".repeat(200)))
        assertEquals(4, SmsSegments.parts("–" + "a".repeat(201)))
        assertEquals(4, SmsSegments.parts("\uD83D\uDC36" + "a".repeat(197)))
    }

    @Test
    fun `normalise maps typographic punctuation and narrow spaces to GSM equivalents`() {
        val text = SmsSegments.normalise("9:05\u202FAM – Vet’s “diet”…\u00A0now")
        assertEquals("9:05 AM - Vet's \"diet\"... now", text)
        assertTrue(SmsSegments.isGsm7(text))
        assertEquals("नमस्ते", SmsSegments.normalise("नमस्ते"))
    }
}
