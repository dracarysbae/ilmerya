package com.bloxtrix.hexdrop.model

enum class GameMode { Flow, Calm }

data class GameState(
    val runId: Long = 0,
    val grid: List<List<Int?>> = emptyGrid(),
    val current: Int = 2,
    val currentCol: Int = COLS / 2,
    val nextQueue: List<Int> = listOf(2, 4, 2),
    val score: Long = 0,
    val highScore: Long = 0,
    val phase: GamePhase = GamePhase.Paused,
    val mode: GameMode = GameMode.Calm,
    val energy: Int = 3,
    val turnKey: Int = 0,
    val mergeKey: Int = 0,
    val lastChains: Int = 0,
    val lastGain: Long = 0,
    val lastMergeValue: Int = 0,
    val dropCount: Int = 0,
    val bestChain: Int = 0,
    val autoDropProgress: Float = 0f,
    val resolving: Boolean = false,
    val lastDropRow: Int = -1,
    val lastDropCol: Int = -1,
    val lastDropValue: Int = 0,
    /** Presentation only: the board right after the placement or Cycle, before any wave resolved. */
    val fxStart: List<List<Int?>>? = null,
    /** Presentation only: the cascade waves of the last turn. Never persisted or replayed. */
    val fxWaves: List<com.bloxtrix.hexdrop.engine.MergeWave> = emptyList(),
)

fun emptyGrid(): List<List<Int?>> = List(ROWS) { List(COLS) { null } }
val GameState.level: Int get() = 1 + dropCount / 18
val GameState.autoDropMs: Long get() = (6000L - (level - 1) * 400L).coerceAtLeast(2400L)

/** Target mix at the next bag refill; stones already in the preview stay intact. */
val GameState.incomingStage: Int get() = when {
    level >= 4 -> 3
    level >= 2 -> 2
    else -> 1
}
val GameState.incomingMaxValue: Int get() = 1 shl (incomingStage + 3)
