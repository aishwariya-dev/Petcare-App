package com.petcare.app.reminder

import android.telephony.SmsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notification must explain a failed sent callback in plain words. */
class SmsSendFailureReasonTest {
    @Test
    fun `known result codes map to plain reasons`() {
        assertEquals("no mobile service", sendFailureReason(SmsManager.RESULT_ERROR_NO_SERVICE))
        assertTrue(sendFailureReason(SmsManager.RESULT_ERROR_RADIO_OFF).contains("airplane"))
        assertTrue(sendFailureReason(SmsManager.RESULT_ERROR_GENERIC_FAILURE).contains("default SMS SIM"))
    }

    @Test
    fun `unknown codes still produce a reason`() {
        assertEquals("the phone reported an error", sendFailureReason(-12345))
    }
}
