package com.petcare.app.reminder

/**
 * Counts how many charged SMS parts a text needs, the way the modem does:
 * GSM 03.38 septets when every character is in that alphabet (160 single,
 * 153 per concatenated part), otherwise UCS-2 code units (70 single, 67 per
 * part). One typographic character such as an en dash or a narrow space is
 * enough to push a whole message into UCS-2, so [normalise] maps the common
 * ones back to GSM equivalents before counting.
 */
object SmsSegments {
    private const val GSM_BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM_EXTENSION = "\u000C^{}\\[~]|€"
    private val basic = GSM_BASIC.toSet()
    private val extension = GSM_EXTENSION.toSet()

    private const val GSM_SINGLE = 160
    private const val GSM_PART = 153
    private const val UCS2_SINGLE = 70
    private const val UCS2_PART = 67

    fun isGsm7(text: String): Boolean = text.all { it in basic || it in extension }

    /** Septet count for a GSM-7 text; extension characters cost two. */
    fun septets(text: String): Int = text.length + text.count { it in extension }

    /**
     * Charged parts for [text]. When an extension character or a surrogate
     * pair could straddle a part boundary the modem ends that part one unit
     * early, so the per-part capacity is reduced by one to stay conservative.
     */
    fun parts(text: String): Int {
        if (text.isEmpty()) return 1
        return if (isGsm7(text)) {
            val septets = septets(text)
            if (septets <= GSM_SINGLE) 1
            else ceilDiv(septets, if (text.any { it in extension }) GSM_PART - 1 else GSM_PART)
        } else {
            val units = text.length
            if (units <= UCS2_SINGLE) 1
            else ceilDiv(units, if (text.any { it.isSurrogate() }) UCS2_PART - 1 else UCS2_PART)
        }
    }

    /** Replaces typographic punctuation and special spaces with GSM-7 equivalents. */
    fun normalise(text: String): String = buildString(text.length) {
        for (char in text) {
            when (char) {
                '\u00A0', '\u2007', '\u2009', '\u202F' -> append(' ')
                '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212' -> append('-')
                '\u2018', '\u2019', '\u201A', '\u2032' -> append('\'')
                '\u201C', '\u201D', '\u201E', '\u2033' -> append('"')
                '\u2026' -> append("...")
                else -> append(char)
            }
        }
    }

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
}
