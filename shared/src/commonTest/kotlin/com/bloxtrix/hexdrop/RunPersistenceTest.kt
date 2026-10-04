package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.competition.*
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import com.bloxtrix.hexdrop.persistence.RunStore
import com.bloxtrix.hexdrop.persistence.SavedRun
import com.bloxtrix.hexdrop.viewmodel.HexDropViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

private class MemoryStore(var value: String? = null) : RunStore {
    override fun load() = value
    override fun save(encoded: String?) { value = encoded }
}

private class FakeLeague(private val seed: Long, private val now: Long) : LeagueClient {
    override val state = MutableStateFlow(WeeklyState(loginRequired = false))
    override val configured = true
    val submitted = mutableListOf<Pair<RankedTicket, CompletedRun>>()
    override suspend fun refresh() {}
    override suspend fun startRun() = RankedTicket("00000000-0000-0000-0000-000000000001", seed, "2026-09-28", now + 3_600_000, "p1", now)
    override suspend fun submit(ticket: RankedTicket, run: CompletedRun) { submitted += ticket to run }
    override suspend fun deleteAccount() = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class RunPersistenceTest {
    private val now = 1_790_000_000_000L

    private fun TestScope.playUntilOver(vm: HexDropViewModel) {
        var guard = 0
        while (vm.state.value.phase == GamePhase.Playing && guard++ < 3000) {
            val s = vm.state.value
            val open = (0 until COLS).firstOrNull { !s.grid.isColumnFull(it) }
            if (open != null) vm.drop(open) else {
                vm.selectColumn((0 until COLS).first { cycleColumn(s.grid, it) != null }); vm.cycle()
            }
            advanceTimeBy(500)
        }
    }

    @Test fun freeRunSurvivesProcessDeathWithTheSameBag() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = MemoryStore()
        val first = HexDropViewModel(runStore = store, wallClock = { now }, timeSource = testScheduler.timeSource)
        try {
            first.restart(GameMode.Calm)
            repeat(3) { first.drop(it); advanceTimeBy(300) }
            val saved = store.value
            assertNotNull(saved)
            val second = HexDropViewModel(runStore = MemoryStore(saved), wallClock = { now }, timeSource = testScheduler.timeSource)
            try {
                val a = first.state.value; val b = second.state.value
                assertEquals(GamePhase.Paused, b.phase)
                assertEquals(a.grid, b.grid); assertEquals(a.score, b.score)
                assertEquals(a.current, b.current); assertEquals(a.nextQueue, b.nextQueue)
                assertEquals(a.energy, b.energy); assertEquals(a.dropCount, b.dropCount)
                second.resume()
                repeat(3) { col -> first.drop(col + 1); second.drop(col + 1); advanceTimeBy(300) }
                assertEquals(first.state.value.grid, second.state.value.grid)
                assertEquals(first.state.value.nextQueue, second.state.value.nextQueue)
                // Re-entering the screen must not replay the last move's sound.
                assertFalse(HexDropViewModel(runStore = MemoryStore(saved), wallClock = { now }, timeSource = testScheduler.timeSource).let { vm ->
                    vm.claimTurnSound(vm.state.value.runId, vm.state.value.turnKey).also { vm.dispose() } })
            } finally { second.dispose() }
        } finally { first.dispose(); Dispatchers.resetMain() }
    }

    @Test fun finishedFreeRunAndCorruptDataAreNotRestored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = MemoryStore("v=1\ngrid=broken")
        val vm = HexDropViewModel(runStore = store, wallClock = { now }, timeSource = testScheduler.timeSource)
        try {
            assertNull(store.value)
            assertEquals(0, vm.state.value.dropCount)
            vm.restart()
            playUntilOver(vm)
            assertEquals(GamePhase.GameOver, vm.state.value.phase)
            assertNull(store.value)
        } finally { vm.dispose(); Dispatchers.resetMain() }
    }

    @Test fun rankedRunResumesFromRecordedMovesAndVerifiesOnServer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = MemoryStore()
        val league = FakeLeague(seed = 42, now = now)
        val first = HexDropViewModel(runStore = store, wallClock = { now }, timeSource = testScheduler.timeSource).also { it.league = league }
        try {
            first.startLeague {}
            advanceTimeBy(10)
            assertTrue(first.ranked.value)
            repeat(6) { first.drop(it % COLS); advanceTimeBy(400) }
            val saved = store.value!!
            // A tampered board is ignored; ranked games are rebuilt from the seed and moves.
            val tampered = saved.replace(Regex("score=\\d+"), "score=999999")
            val second = HexDropViewModel(runStore = MemoryStore(tampered), wallClock = { now + 60_000 }, timeSource = testScheduler.timeSource).also { it.league = league }
            try {
                assertTrue(second.ranked.value)
                assertEquals(first.state.value.grid, second.state.value.grid)
                assertEquals(first.state.value.score, second.state.value.score)
                second.resume()
                playUntilOver(second)
                advanceTimeBy(100)
                val (ticket, run) = league.submitted.single()
                val verified = RunReplay.verify(ticket.seed, run.events, 6 * 60 * 60 * 1000L)
                assertEquals(second.state.value.score, verified.score)
            } finally { second.dispose() }
        } finally { first.dispose(); Dispatchers.resetMain() }
    }

    @Test fun expiredTicketContinuesAsFreePlay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = MemoryStore()
        val league = FakeLeague(seed = 7, now = now)
        val first = HexDropViewModel(runStore = store, wallClock = { now }, timeSource = testScheduler.timeSource).also { it.league = league }
        try {
            first.startLeague {}
            advanceTimeBy(10)
            repeat(4) { first.drop(it); advanceTimeBy(400) }
            val later = HexDropViewModel(runStore = MemoryStore(store.value), wallClock = { now + 7 * 3_600_000L }, timeSource = testScheduler.timeSource)
            try {
                assertFalse(later.ranked.value)
                assertEquals("expired", later.restoreNotice.value)
                assertEquals(first.state.value.grid, later.state.value.grid)
            } finally { later.dispose() }
        } finally { first.dispose(); Dispatchers.resetMain() }
    }

    @Test fun finishedRankedRunIsResentAfterProcessDeath() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val league = FakeLeague(seed = 9, now = now)
        val ticket = league.startRun()
        val bag = StoneBag(kotlin.random.Random(9)); var s = RunReplay.initial(bag)
        val events = mutableListOf<RunEvent>(); var time = 0L
        while (s.phase == GamePhase.Playing) {
            val col = (0 until COLS).firstOrNull { !s.grid.isColumnFull(it) }
            val action = if (col != null) "D$col" else "C${(0 until COLS).first { cycleColumn(s.grid, it) != null }}"
            events += RunEvent(time, action); time += 500
            s = RunReplay.apply(s, bag, action)
        }
        val saved = SavedRun(s.copy(runId = 3), bag.snapshot(), ticket, events, time, awaitingSubmission = true).encode()
        val store = MemoryStore(saved)
        val vm = HexDropViewModel(runStore = store, wallClock = { now }, timeSource = testScheduler.timeSource)
        try {
            assertTrue(league.submitted.isEmpty())
            vm.league = league
            advanceTimeBy(10)
            assertEquals(events, league.submitted.single().second.events)
            assertNull(store.value)
        } finally { vm.dispose(); Dispatchers.resetMain() }
    }
}
