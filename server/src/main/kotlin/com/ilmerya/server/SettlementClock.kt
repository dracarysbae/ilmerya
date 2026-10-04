package com.ilmerya.server

/** The one-second local clock never queries the database between week boundaries. */
internal class SettlementClock(private val settle: (Long) -> Unit) {
    private var nextCheck = Long.MIN_VALUE
    fun tick(now: Long) {
        if (now < nextCheck) return
        // Back off on an outage; after a successful startup, sleep until next Monday.
        nextCheck = now + 60_000
        settle(now)
        nextCheck = weekAt(now).end
    }
}
