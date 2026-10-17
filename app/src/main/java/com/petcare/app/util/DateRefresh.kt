package com.petcare.app.util

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

private val localDateInvalidation = MutableStateFlow(0L)

/** Re-checks active date flows after an activity resume or calendar change. */
fun invalidateLocalDateChanges() {
    localDateInvalidation.update { it + 1 }
}

/**
 * Emits the current local date immediately and again when the next local day
 * begins. The default zone is intentionally read on every iteration rather
 * than captured when the flow is created. The flow is cold so a ViewModel can
 * keep it upstream of a WhileSubscribed state flow; no date timer is kept
 * alive when its screen is not being observed.
 *
 * [clock] is injectable for JVM tests. Production callers leave it null so
 * both the current instant and the current device zone are re-read after an
 * invalidation.
 */
fun localDateChanges(clock: Clock? = null): Flow<LocalDate> = flow {
    var emittedDate: LocalDate? = null
    var observedInvalidation = localDateInvalidation.value

    while (currentCoroutineContext().isActive) {
        val zone = clock?.zone ?: ZoneId.systemDefault()
        val now = if (clock == null) ZonedDateTime.now(zone) else ZonedDateTime.now(clock)
        val date = now.toLocalDate()
        if (date != emittedDate) {
            emit(date)
            emittedDate = date
        }

        val nextZone = clock?.zone ?: ZoneId.systemDefault()
        val nextDate = date.plusDays(1).atStartOfDay(nextZone)
        val delayMillis = Duration.between(now, nextDate).toMillis().coerceAtLeast(1L)
        val invalidation = withTimeoutOrNull(delayMillis) {
            localDateInvalidation.first { it != observedInvalidation }
        }
        if (invalidation != null) observedInvalidation = invalidation
    }
}.distinctUntilChanged()