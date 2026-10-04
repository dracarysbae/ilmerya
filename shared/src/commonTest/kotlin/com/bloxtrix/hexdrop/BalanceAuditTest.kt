package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reproducible balance experiment with broad bounds on useful strategic advantage. */
class BalanceAuditTest {
    private enum class Mix { Baseline, Progressive, EarlyPressure }
    private enum class Bot { Random, RoundRobin, Greedy }
    private data class Run(val drops: Int, val actions: Int, val score: Long, val earlyFill: Double, val capped: Boolean)
    private data class Move(val col: Int, val cycle: Boolean, val state: GameState)

    /** Refill timing, current stone and three-stone preview match HexDropViewModel. */
    private class Source(private val mix: Mix, seed: Int) {
        private val random = Random(seed)
        private val native = StoneBag(Random(seed))
        private val bag = ArrayDeque<Int>()
        fun next(level: Int): Int {
            if (mix == Mix.EarlyPressure) return native.next(level)
            if (bag.isEmpty()) {
                // Preserve the exact old pre-shuffle order, including its replacements,
                // so historical seeded runs remain comparable to the production bag.
                if (mix == Mix.Baseline) {
                    val values = mutableListOf(2, 2, 2, 2, 2, 2, 4, 4, 4, 8, 8, 8)
                    if (level >= 4) { values[0] = 16; values[1] = 4 }
                    bag.addAll(values.shuffled(random))
                    return bag.removeFirst()
                }
                val counts = when (mix) {
                    Mix.Progressive -> when {
                        level >= 7 -> listOf(3, 2, 2, 2, 2, 1)
                        level >= 4 -> listOf(3, 3, 2, 2, 2)
                        level >= 2 -> listOf(4, 3, 3, 2)
                        else -> listOf(5, 4, 3)
                    }
                    else -> error("Native and frozen baseline bags are handled above")
                }
                bag.addAll(counts.flatMapIndexed { index, count -> List(count) { 2 shl index } }.shuffled(random))
            }
            return bag.removeFirst()
        }
    }

    private fun utility(state: GameState): Double {
        val heights = (0 until COLS).map { c -> state.grid.count { it[c] != null } }
        var adjacentPairs = 0
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val pos = HexPos(r, c)
            val value = state.grid.get(pos) ?: continue
            adjacentPairs += pos.neighbors().count { state.grid.get(it) == value }
        }
        return -14.0 * heights.sum() - 2.0 * heights.max() - 0.4 * heights.sumOf { it * it } +
            0.6 * adjacentPairs + 1.8 * state.energy
    }

    private fun choose(state: GameState, bot: Bot, random: Random): Move? {
        val cols = (0 until COLS).filter { !state.grid.isColumnFull(it) }
        fun drop(col: Int): Move = Move(col, false, resolveTurn(state.copy(currentCol = col),
            dropPiece(state.grid, col, state.current)!!.grid, true))
        fun cycles(): List<Move> = if (state.energy < 3) emptyList() else (0 until COLS).mapNotNull { col ->
            cycleColumn(state.grid, col)?.let { Move(col, true, resolveTurn(state.copy(currentCol = col), it, false)) }
        }
        if (bot == Bot.Greedy) {
            val bestDrop = cols.map(::drop).maxByOrNull { utility(it.state) }
            // A rotation must improve the current board after its actual charge cost;
            // it cannot win merely by postponing a drop or by rotating forever.
            val bestCycle = cycles().filter { bestDrop == null || utility(it.state) > utility(state) + 3.0 }
                .maxByOrNull { utility(it.state) }
            return when {
                bestDrop == null -> bestCycle
                bestCycle != null && utility(bestCycle.state) > utility(bestDrop.state) -> bestCycle
                else -> bestDrop
            }
        }
        if (cols.isNotEmpty()) {
            val col = if (bot == Bot.Random) cols.random(random) else {
                val preferred = state.dropCount % COLS
                (0 until COLS).map { (preferred + it) % COLS }.first { it in cols }
            }
            return drop(col)
        }
        val rescue = cycles()
        return if (bot == Bot.Random) rescue.randomOrNull(random) else rescue.firstOrNull()
    }

    private fun play(mix: Mix, bot: Bot, seed: Int): Run {
        val source = Source(mix, seed)
        val random = Random(seed xor 0x5a17)
        var state = GameState(current = source.next(1), nextQueue = List(3) { source.next(1) }, phase = GamePhase.Playing)
        var actions = 0
        var earlyFill = 0.0
        var earlySamples = 0
        while (state.phase == GamePhase.Playing && state.dropCount < 400 && actions < 500) {
            val move = choose(state, bot, random) ?: break
            state = if (move.cycle) move.state else move.state.copy(
                current = state.nextQueue.first(), nextQueue = state.nextQueue.drop(1) + source.next(move.state.level))
            actions++
            if (!move.cycle && state.dropCount <= 40) {
                earlyFill += state.grid.sumOf { row -> row.count { it != null } }.toDouble() / (COLS * ROWS)
                earlySamples++
            }
            assertEquals(applyGravity(state.grid), state.grid)
            assertTrue(findMergeGroups(state.grid).isEmpty())
            assertTrue(state.energy in 0..6)
            assertEquals(!hasMoves(state.grid, state.energy), state.phase == GamePhase.GameOver)
        }
        return Run(state.dropCount, actions, state.score, earlyFill / earlySamples.coerceAtLeast(1),
            state.phase == GamePhase.Playing && (state.dropCount >= 400 || actions >= 500))
    }

    @Test fun auditNativeEngineWithReproduciblePolicies() {
        println("BALANCE_LIMITS seeds=0..31 drop_cap=400 action_cap=500; capped lifetimes are lower bounds")
        println("BALANCE_CSV mix,bot,seeds,p10_drops,median_drops,p90_drops,survival100_pct,survival200_pct,cap_pct,mean_fill_first40_pct,median_score,mean_actions")
        fun pct(value: Double) = (value * 1000).toInt() / 10.0
        val medians = mutableMapOf<Pair<Mix, Bot>, Double>()
        for (mix in Mix.entries) for (bot in Bot.entries) {
            val runs = (0 until 32).map { play(mix, bot, it) }
            val drops = runs.map { it.drops }.sorted()
            val scores = runs.map { it.score }.sorted()
            medians[mix to bot] = (drops[15] + drops[16]) / 2.0
            println("BALANCE_CSV ${mix.name},${bot.name},32,${drops[3]},${(drops[15] + drops[16]) / 2.0},${drops[28]}," +
                "${pct(runs.count { it.drops >= 100 } / 32.0)},${pct(runs.count { it.drops >= 200 } / 32.0)}," +
                "${pct(runs.count { it.capped } / 32.0)},${pct(runs.map { it.earlyFill }.average())}," +
                "${(scores[15] + scores[16]) / 2.0},${runs.map { it.actions }.average()}")
        }
        val randomMedian = medians.getValue(Mix.EarlyPressure to Bot.Random)
        val greedyMedian = medians.getValue(Mix.EarlyPressure to Bot.Greedy)
        assertTrue(randomMedian in 50.0..160.0, "Random play should face pressure without an abrupt opening loss: $randomMedian")
        assertTrue(greedyMedian >= randomMedian * 2,
            "Planning should at least double random-play median lifetime (400-drop / 500-action cap): $greedyMedian vs $randomMedian")
    }
}
