package com.petcare.app.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DateRefreshTest {

    @Test
    fun `invalidation rereads an injected clock zone without waiting for midnight`() = runTest {
        val clock = MutableClock(
            instant = Instant.parse("2026-09-15T12:00:00Z"),
            currentZone = ZoneId.of("UTC")
        )
        val dates = async {
            localDateChanges(clock).take(2).toList()
        }

        runCurrent()
        clock.currentZone = ZoneId.of("Pacific/Auckland")
        invalidateLocalDateChanges()
        runCurrent()

        assertEquals(
            listOf(
                ZonedDateTime.ofInstant(clock.instant(), ZoneId.of("UTC")).toLocalDate(),
                ZonedDateTime.ofInstant(clock.instant(), ZoneId.of("Pacific/Auckland")).toLocalDate()
            ),
            dates.await()
        )
    }

    private class MutableClock(
        private var instant: Instant,
        var currentZone: ZoneId
    ) : Clock() {
        override fun instant(): Instant = instant

        override fun getZone(): ZoneId = currentZone

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    }
}