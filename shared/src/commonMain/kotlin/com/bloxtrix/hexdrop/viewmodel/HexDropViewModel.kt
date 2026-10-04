package com.bloxtrix.hexdrop.viewmodel

import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import com.bloxtrix.hexdrop.ads.GameOverAdGate
import com.bloxtrix.hexdrop.persistence.NoRunStore
import com.bloxtrix.hexdrop.persistence.RunStore
import com.bloxtrix.hexdrop.persistence.SavedRun
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.bloxtrix.hexdrop.competition.*

private const val TICKET_LIFETIME_MS = 6 * 60 * 60 * 1000L

/**
 * Commands and clock ticks are confined to the UI dispatcher.
 * [wallClock] is epoch milliseconds; it only decides whether a restored league ticket expired.
 */
class HexDropViewModel(
    savedHighScore: Long = 0L,
    private val saveBest: (Long) -> Unit = {},
    private val runStore: RunStore = NoRunStore,
    private val wallClock: () -> Long = { 0L },
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val bag = StoneBag()
    private val _state = MutableStateFlow(GameState(highScore = savedHighScore))
    val state = _state.asStateFlow()
    val gameOverAds = GameOverAdGate()
    private var settleJob: Job? = null
    private var ticket: RankedTicket? = null
    private var rankedBag: StoneBag? = null
    private var runClock: TimeMark = timeSource.markNow()
    private val events = mutableListOf<RunEvent>()
    private val _leagueStarting = MutableStateFlow(false)
    val leagueStarting = _leagueStarting.asStateFlow()
    private val _ranked = MutableStateFlow(false)
    val ranked = _ranked.asStateFlow()
    private val _submission = MutableStateFlow("")
    val submission = _submission.asStateFlow()
    /** Set when a restored league ticket had already closed; the board continues as free play. */
    private val _restoreNotice = MutableStateFlow("")
    val restoreNotice = _restoreNotice.asStateFlow()
    private var soundedTurn: Pair<Long,Int>? = null
    private var soundedGameOver = -1L
    private var unsentRun: Pair<RankedTicket, CompletedRun>? = null
    /** Best score before the current run began; a finished run above it is a new record. */
    var runStartBest: Long = savedHighScore
        private set

    /** Assigned by the host after construction; a result saved before process death is resent once. */
    var league: LeagueClient = UnavailableLeague
        set(value) {
            field = value
            unsentRun?.let { (savedTicket, run) -> unsentRun = null; send(savedTicket, run, value) }
        }

    fun claimTurnSound(run:Long,turn:Int):Boolean {
        val key=run to turn
        if(soundedTurn==key) return false
        soundedTurn=key; return true
    }
    fun claimGameOverSound(run:Long):Boolean {
        if(soundedGameOver==run) return false
        soundedGameOver=run; return true
    }
    fun clearRestoreNotice() { _restoreNotice.value = "" }

    fun startLeague(onStarted: () -> Unit) {
        if (_leagueStarting.value) return
        _leagueStarting.value = true
        scope.launch {
            try {
                val next = league.startRun() ?: return@launch
                restart(GameMode.Calm)
                ticket = next; rankedBag = StoneBag(kotlin.random.Random(next.seed))
                _state.value = RunReplay.initial(rankedBag!!).copy(runId = _state.value.runId, highScore = _state.value.highScore)
                runClock = timeSource.markNow(); _ranked.value = true
                persist()
                onStarted()
            } finally { _leagueStarting.value = false }
        }
    }
    private fun record(action: String) {
        if (ticket == null) return
        if (events.size >= MAX_RUN_EVENTS) { ticket = null; _ranked.value = false; _submission.value = "limit"; return }
        events.add(RunEvent(runClock.elapsedNow().inWholeMilliseconds, action))
    }

    init {
        restoreSaved()
        scope.launch {
            while (isActive) {
                delay(50)
                val s = _state.value
                if (s.phase != GamePhase.Playing || s.resolving || s.mode == GameMode.Calm) continue
                if (s.grid.isGameOver()) continue // Give a charged final rescue time to be planned.
                val progress = s.autoDropProgress + 50f / s.autoDropMs
                if (progress >= 1f) {
                    val col = (0 until COLS).filter { !s.grid.isColumnFull(it) }
                        .minByOrNull { abs(it - s.currentCol) } ?: continue
                    drop(col)
                } else _state.value = s.copy(autoDropProgress = progress)
            }
        }
    }
    fun selectColumn(col: Int) {
        val s = _state.value
        if (s.phase == GamePhase.Playing && !s.resolving && col in 0 until COLS) _state.value = s.copy(currentCol = col)
    }
    fun moveLeft() = selectColumn((_state.value.currentCol - 1).coerceAtLeast(0))
    fun moveRight() = selectColumn((_state.value.currentCol + 1).coerceAtMost(COLS - 1))
    fun drop(col: Int? = null) {
        val s = _state.value
        if (s.phase != GamePhase.Playing || s.resolving) return
        val target = col ?: s.currentCol
        val dropped = dropPiece(s.grid, target, s.current) ?: return
        record("D$target")
        val result = resolveTurn(s.copy(currentCol = target), dropped.grid, true)
        publish(result.copy(current = s.nextQueue.first(), nextQueue = s.nextQueue.drop(1) + (rankedBag ?: bag).next(result.level),
            lastDropRow = dropped.landRow, lastDropCol = target, lastDropValue = s.current,
            fxStart = dropped.grid, fxWaves = resolveWaves(dropped.grid)))
    }
    fun cycle() {
        val s = _state.value
        if (s.phase != GamePhase.Playing || s.resolving || s.energy < 3) return
        val board = cycleColumn(s.grid, s.currentCol) ?: return
        record("C${s.currentCol}")
        publish(resolveTurn(s, board, false).copy(fxStart = board, fxWaves = resolveWaves(board)))
    }
    private fun publish(next: GameState) {
        val oldBest = _state.value.highScore
        _state.value = next.copy(resolving = true)
        if (next.highScore > oldBest) saveBest(next.highScore)
        val completedTicket = if (next.phase == GamePhase.GameOver) ticket else null
        if (completedTicket != null) {
            val completed = CompletedRun(completedTicket.seed, events.toList())
            // Keep the finished replay on disk until the league client has queued it.
            persist(awaitingSubmission = true)
            ticket = null
            send(completedTicket, completed, league)
        } else persist()
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(if (next.lastDropRow >= 0) DROP_SETTLE_MS else CYCLE_SETTLE_MS)
            _state.update { it.copy(resolving = false) }
        }
    }
    private fun send(completedTicket: RankedTicket, completed: CompletedRun, client: LeagueClient) {
        _submission.value = "sending"
        scope.launch {
            try { client.submit(completedTicket, completed); _submission.value = "saved" }
            catch (e: CancellationException) { throw e }
            catch (_: LeagueFailure) { _submission.value = "rejected" }
            catch (_: Exception) { _submission.value = "pending" }
            // Saved, rejected or queued by the client: the run no longer needs local recovery.
            // If a new game already started, this rewrites that game instead of the finished one.
            persist()
        }
    }
    fun pause() {
        _state.update { if (it.phase == GamePhase.Playing) it.copy(phase = GamePhase.Paused) else it }
        persist()
    }
    fun resume() { _state.update { if (it.phase == GamePhase.Paused) it.copy(phase = GamePhase.Playing) else it } }
    fun togglePause() { _state.update {
        when (it.phase) {
            GamePhase.Playing -> it.copy(phase = GamePhase.Paused)
            GamePhase.Paused -> it.copy(phase = GamePhase.Playing)
            else -> it
        }
    } }
    fun restart(mode: GameMode = _state.value.mode) {
        settleJob?.cancel()
        bag.reset()
        ticket = null; rankedBag = null; events.clear(); _ranked.value = false; _submission.value = ""
        _restoreNotice.value = ""
        gameOverAds.beginRun()
        runStartBest = _state.value.highScore
        _state.value = GameState(runId = _state.value.runId + 1, current = bag.next(1), nextQueue = List(3) { bag.next(1) },
            highScore = _state.value.highScore, mode = mode, phase = GamePhase.Playing)
        persist()
    }

    /** Writes the current run; a finished free run is cleared because it cannot be resumed. */
    private fun persist(awaitingSubmission: Boolean = false) {
        val s = _state.value
        if (s.phase == GamePhase.GameOver && !awaitingSubmission) { runStore.save(null); return }
        if (s.dropCount == 0 && ticket == null) { runStore.save(null); return }
        val lastEvent = events.lastOrNull()?.ms ?: 0L
        val elapsed = if (ticket == null) 0L
            else maxOf(runClock.elapsedNow().inWholeMilliseconds, lastEvent + CYCLE_SETTLE_MS + 30)
        runStore.save(SavedRun(s, (rankedBag ?: bag).snapshot(), ticket, if (ticket != null) events.toList() else emptyList(),
            elapsed, awaitingSubmission).encode())
    }

    private fun restoreSaved() {
        val saved = SavedRun.decode(runStore.load()) ?: run { runStore.save(null); return }
        val best = _state.value.highScore
        val savedTicket = saved.ticket
        if (savedTicket == null) {
            if (saved.state.phase == GamePhase.GameOver) { runStore.save(null); return }
            bag.restore(saved.bag)
            _state.value = saved.state.copy(highScore = maxOf(best, saved.state.score), phase = GamePhase.Paused, resolving = false)
        } else {
            // The board is rebuilt from the seed and moves; storage cannot alter a ranked game.
            val (replayed, seededBag) = runCatching { RunReplay.replay(savedTicket.seed, saved.events) }.getOrNull()
                ?: run { runStore.save(null); return }
            if (replayed.phase == GamePhase.GameOver) {
                if (saved.awaitingSubmission && saved.events.isNotEmpty())
                    unsentRun = savedTicket to CompletedRun(savedTicket.seed, saved.events)
                else runStore.save(null)
                return
            }
            val now = wallClock()
            val expired = now <= 0L || now >= minOf(savedTicket.endsAt, savedTicket.startsAt + TICKET_LIFETIME_MS)
            _state.value = replayed.copy(runId = saved.state.runId, highScore = maxOf(best, replayed.score),
                currentCol = saved.state.currentCol, mode = GameMode.Calm, phase = GamePhase.Paused)
            if (expired) {
                bag.restore(seededBag.snapshot())
                _restoreNotice.value = "expired"
            } else {
                ticket = savedTicket; rankedBag = seededBag
                events.addAll(saved.events)
                runClock = timeSource.markNow() - saved.elapsedMs.milliseconds
                _ranked.value = true
            }
        }
        soundedTurn = _state.value.runId to _state.value.turnKey
        persist()
    }
    fun dispose() = scope.cancel()
}
