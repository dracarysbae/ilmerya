package com.bloxtrix.hexdrop.ui

import com.bloxtrix.hexdrop.competition.RunReplay
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random

/**
 * Store-screenshot scenes, available only in debug/QA builds. Boards come from a real seeded game
 * played by the engine with a one-step greedy policy, so every screen is a reachable game state.
 */
object DemoScenes {
    val names = listOf("menu", "board", "tutorial", "result", "settings")

    private fun greedy(state: GameState): String {
        var best: String? = null; var bestScore = Long.MIN_VALUE
        for (col in 0 until COLS) {
            val drop = dropPiece(state.grid, col, state.current) ?: continue
            val result = processBoard(drop.grid)
            val height = drop.grid.count { row -> row[col] != null }
            val score = result.score * 10 - height
            if (score > bestScore) { bestScore = score; best = "D$col" }
        }
        if (best == null && state.energy >= 3)
            (0 until COLS).firstOrNull { cycleColumn(state.grid, it) != null }?.let { return "C$it" }
        return best ?: error("No legal move in a playing state")
    }

    /** A mid-game board with varied minerals (including 64+) and room left to play. */
    fun board(seed: Long = 20261005): GameState {
        val bag = StoneBag(Random(seed)); var state = RunReplay.initial(bag)
        var chosen = state
        while (state.phase == GamePhase.Playing && state.dropCount < 400) {
            state = RunReplay.apply(state, bag, greedy(state))
            val filled = state.grid.sumOf { r -> r.count { it != null } }
            val top = state.grid.flatten().filterNotNull().maxOrNull() ?: 0
            if (filled in 17..22 && top >= 128 && state.dropCount > 70) { chosen = state; break }
            if (filled in 15..24 && top >= 64) chosen = state
        }
        return chosen.copy(highScore = maxOf(chosen.score, 4180), currentCol = 2, energy = 4, phase = GamePhase.Playing,
            lastChains = 2, lastGain = 96, resolving = false)
    }

    /** A finished run: the same seeded game played until no move remains. */
    fun result(seed: Long = 20261005): GameState {
        val bag = StoneBag(Random(seed)); var state = RunReplay.initial(bag)
        while (state.phase == GamePhase.Playing && state.dropCount < 1000) state = RunReplay.apply(state, bag, greedy(state))
        return state.copy(highScore = state.score, resolving = false)
    }
}
