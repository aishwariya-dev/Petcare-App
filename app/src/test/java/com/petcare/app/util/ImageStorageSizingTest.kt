package com.petcare.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageStorageSizingTest {
    @Test
    fun `camera photos fit within a 16 MiB decoded bitmap budget`() {
        listOf(8000 to 6000, 4096 to 4096, 12000 to 9000, 3000 to 4000).forEach { (w, h) ->
            val sample = ImageStorage.sampleSize(w, h)
            val width = (w.toLong() + sample - 1) / sample
            val height = (h.toLong() + sample - 1) / sample
            assertTrue("Oversized bitmap for ${w}x$h", width * height * 4 <= 16L * 1024 * 1024)
            assertTrue(width <= 2048 && height <= 2048)
        }
    }

    @Test
    fun `small photos are not needlessly downsampled`() {
        assertEquals(1, ImageStorage.sampleSize(640, 480))
    }
}