package com.bloxtrix.hexdrop.persistence

import com.bloxtrix.hexdrop.competition.RankedTicket
import com.bloxtrix.hexdrop.competition.RunEvent
import com.bloxtrix.hexdrop.model.*

/** Platform storage for one unfinished run. Writes must not block the UI thread for long. */
interface RunStore {
    fun load(): String?
    fun save(encoded: String?)
}

object NoRunStore : RunStore {
    override fun load(): String? = null
    override fun save(encoded: String?) {}
}

/**
 * An unfinished run, or a finished ranked run whose result has not yet been handed to the
 * league queue. Ranked boards are never trusted from storage: they are rebuilt from the
 * seed and recorded moves, so a restored game is always the game the server will replay.
 */
data class SavedRun(
    val state: GameState,
    val bag: List<Int>,
    val ticket: RankedTicket? = null,
    val events: List<RunEvent> = emptyList(),
    val elapsedMs: Long = 0,
    val awaitingSubmission: Boolean = false,
) {
    fun encode(): String = buildString {
        fun put(key: String, value: Any) { append(key).append('=').append(value).append('\n') }
        put("v", VERSION)
        put("run", state.runId)
        put("mode", state.mode.name)
        put("grid", state.grid.flatten().joinToString(",") { it?.toString() ?: "" })
        put("current", state.current)
        put("queue", state.nextQueue.joinToString(","))
        put("col", state.currentCol)
        put("score", state.score)
        put("energy", state.energy)
        put("turn", state.turnKey)
        put("merge", state.mergeKey)
        put("drops", state.dropCount)
        put("chain", state.bestChain)
        put("flow", state.autoDropProgress)
        put("over", state.phase == GamePhase.GameOver)
        put("bag", bag.joinToString(","))
        put("elapsed", elapsedMs)
        put("awaiting", awaitingSubmission)
        ticket?.let {
            put("ticket", listOf(it.id, it.seed, it.week, it.endsAt, it.playerId, it.startsAt).joinToString("|"))
            put("events", events.joinToString(";") { e -> "${e.ms}:${e.action}" })
        }
    }

    companion object {
        const val VERSION = 1

        fun decode(text: String?): SavedRun? = runCatching {
            if (text.isNullOrBlank()) return null
            val map = text.lineSequence().filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            if (map["v"]?.toInt() != VERSION) return null
            fun ints(key: String) = map.getValue(key).split(',').filter { it.isNotEmpty() }.map { it.toInt() }
            val cells = map.getValue("grid").split(',').map { it.toIntOrNull() }
            require(cells.size == ROWS * COLS)
            val values = cells.filterNotNull() + ints("queue") + ints("bag") + map.getValue("current").toInt()
            require(values.all { it >= 2 && it and (it - 1) == 0 })
            val queue = ints("queue")
            require(queue.size == 3)
            val ticket = map["ticket"]?.split('|')?.let {
                require(it.size == 6)
                RankedTicket(it[0], it[1].toLong(), it[2], it[3].toLong(), it[4], it[5].toLong())
            }
            val events = map["events"].orEmpty().split(';').filter { it.isNotEmpty() }.map {
                RunEvent(it.substringBefore(':').toLong(), it.substringAfter(':'))
            }
            val over = map["over"].toBoolean()
            val state = GameState(
                runId = map.getValue("run").toLong(),
                grid = cells.chunked(COLS),
                current = map.getValue("current").toInt(),
                currentCol = map.getValue("col").toInt().coerceIn(0, COLS - 1),
                nextQueue = queue,
                score = map.getValue("score").toLong(),
                mode = GameMode.valueOf(map.getValue("mode")),
                energy = map.getValue("energy").toInt().coerceIn(0, 6),
                turnKey = map.getValue("turn").toInt(),
                mergeKey = map.getValue("merge").toInt(),
                dropCount = map.getValue("drops").toInt(),
                bestChain = map.getValue("chain").toInt(),
                autoDropProgress = map.getValue("flow").toFloat().coerceIn(0f, .95f),
                phase = if (over) GamePhase.GameOver else GamePhase.Paused,
            )
            SavedRun(state, ints("bag"), ticket, events, map.getValue("elapsed").toLong(), map["awaiting"].toBoolean())
        }.getOrNull()
    }
}
