package com.ilmerya.server

import java.time.Instant
import kotlin.test.*

class SettlementClockTest {
    @Test fun idleTicksDoNotKeepDatabaseAwakeAndMondaySettles() {
        val start = Instant.parse("2026-09-27T20:00:00Z").toEpochMilli()
        val calls = mutableListOf<Long>()
        val clock = SettlementClock { calls += it }
        repeat(3600) { clock.tick(start + it * 1000L) }
        assertEquals(listOf(start), calls)
        clock.tick(start + 3_600_000)
        assertEquals(listOf(start, start + 3_600_000), calls)
    }

    @Test fun failureRetriesAfterOneMinuteAndRestartRecoversImmediately() {
        val start = Instant.parse("2026-09-28T08:00:00Z").toEpochMilli()
        var attempts = 0
        val clock = SettlementClock { if (++attempts == 1) error("offline") }
        assertFails { clock.tick(start) }
        clock.tick(start + 59_999)
        assertEquals(1, attempts)
        clock.tick(start + 60_000)
        assertEquals(2, attempts)
        SettlementClock { attempts++ }.tick(start + 70_000)
        assertEquals(3, attempts)
    }
}
