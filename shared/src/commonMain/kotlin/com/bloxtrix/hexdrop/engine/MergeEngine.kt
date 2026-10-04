package com.bloxtrix.hexdrop.engine

import com.bloxtrix.hexdrop.model.*
import kotlin.math.abs

typealias Grid = List<List<Int?>>

// ── Grid helpers ──────────────────────────────────────────────────────────────

fun Grid.get(pos: HexPos): Int?   = this[pos.row][pos.col]
fun Grid.isEmptyAt(pos: HexPos)   = get(pos) == null
fun Grid.isColumnFull(col: Int)   = this[0][col] != null
fun Grid.isGameOver()             = (0 until COLS).all { isColumnFull(it) }

fun Grid.set(row: Int, col: Int, value: Int?): Grid =
    mapIndexed { r, rowList ->
        if (r == row) rowList.mapIndexed { c, v -> if (c == col) value else v }
        else rowList
    }

fun Grid.set(pos: HexPos, value: Int?) = set(pos.row, pos.col, value)

fun Grid.deepCopy(): Grid = map { it.toList() }

// ── Drop ─────────────────────────────────────────────────────────────────────

data class DropResult(val grid: Grid, val landRow: Int)

/**
 * Drops [value] into [col]. Returns new grid + landing row, or null if column is full.
 */
fun dropPiece(grid: Grid, col: Int, value: Int): DropResult? {
    if (col !in 0 until COLS || grid.isColumnFull(col)) return null
    val landRow = (ROWS - 1 downTo 0).firstOrNull { grid[it][col] == null } ?: return null
    return DropResult(grid.set(landRow, col, value), landRow)
}

// ── Merge detection ───────────────────────────────────────────────────────────

private fun findGroup(grid: Grid, start: HexPos, visited: MutableSet<HexPos>): Set<HexPos> {
    val value = grid.get(start) ?: return emptySet()
    val group = mutableSetOf<HexPos>()
    val queue = ArrayDeque<HexPos>()
    queue.add(start)
    while (queue.isNotEmpty()) {
        val pos = queue.removeFirst()
        if (pos in group) continue
        group.add(pos)
        visited.add(pos)
        pos.neighbors()
            .filter { it !in group && grid.get(it) == value }
            .forEach { queue.add(it) }
    }
    return group
}

/** Returns all groups of 3+ same-value adjacent hexes. */
fun findMergeGroups(grid: Grid): List<Set<HexPos>> {
    val visited = mutableSetOf<HexPos>()
    val groups  = mutableListOf<Set<HexPos>>()
    for (row in 0 until ROWS) for (col in 0 until COLS) {
        val pos = HexPos(row, col)
        if (pos in visited || grid.get(pos) == null) continue
        val group = findGroup(grid, pos, visited)
        if (group.size >= 3) groups.add(group)
    }
    return groups
}

// ── Apply merges ──────────────────────────────────────────────────────────────

data class MergeResult(val grid: Grid, val score: Long, val highestValue: Int)

/** The surviving cell: the deepest one, with a deterministic centreward tie break. */
fun mergeTarget(group: Set<HexPos>): HexPos = group.maxWith(compareBy({ it.row }, { -abs(it.col - COLS / 2) }))

fun applyMerges(grid: Grid, groups: List<Set<HexPos>>): MergeResult {
    var current    = grid
    var totalScore = 0L
    var highestVal = 0

    for (group in groups) {
        val value    = current.get(group.first()) ?: continue
        val newValue = (value.toLong() * 2).coerceAtMost(1L shl 30).toInt()
        highestVal   = maxOf(highestVal, newValue)

        for (pos in group) current = current.set(pos, null)

        val target = mergeTarget(group)
        current = current.set(target, newValue)

        totalScore += newValue * group.size.toLong()
    }
    return MergeResult(current, totalScore, highestVal)
}

// ── Gravity ───────────────────────────────────────────────────────────────────

/** Makes all pieces fall to the bottom of their column. */
fun applyGravity(grid: Grid): Grid {
    val result = MutableList(ROWS) { MutableList<Int?>(COLS) { null } }
    for (col in 0 until COLS) {
        val values = (0 until ROWS).mapNotNull { row -> grid[row][col] }
        val startRow = ROWS - values.size
        values.forEachIndexed { i, v -> result[startRow + i][col] = v }
    }
    return result.map { it.toList() }
}

// ── Full board processing ─────────────────────────────────────────────────────

data class ProcessResult(
    val grid             : Grid,
    val score            : Long,
    val chains           : Int,
    val highestMergeValue: Int,
    /** Centroid row of the first merge group (for particle origin). */
    val mergeRow         : Int = -1,
    /** Centroid col of the first merge group. */
    val mergeCol         : Int = -1,
)

/**
 * Runs merge → gravity loop until stable.
 * Returns final grid, total score, chain count, highest merged value, and merge centroid.
 */
fun processBoard(grid: Grid): ProcessResult {
    var current    = grid
    var totalScore = 0L
    var chains     = 0
    var highestVal = 0
    var firstMergeRow = -1
    var firstMergeCol = -1

    while (true) {
        val groups = findMergeGroups(current)
        if (groups.isEmpty()) break

        // Capture centroid of the first merge group from the first chain only
        if (chains == 0 && groups.isNotEmpty()) {
            val g = groups[0]
            firstMergeRow = g.map { it.row }.average().toInt()
            firstMergeCol = g.map { it.col }.average().toInt()
        }

        val result  = applyMerges(current, groups)
        current     = applyGravity(result.grid)
        totalScore += result.score * (chains + 1)
        highestVal  = maxOf(highestVal, result.highestValue)
        chains++
    }
    return ProcessResult(current, totalScore, chains, highestVal, firstMergeRow, firstMergeCol)
}

/** One fusing group inside a wave: [cells] become a single stone worth [value] at [target]. */
data class MergeGroup(val cells: Set<HexPos>, val target: HexPos, val value: Int)

/**
 * A single cascade wave for presentation: [before] → fuse → [merged] → gravity → [settled].
 * [score] already includes the wave multiplier. Rules are unchanged: the final settled grid and
 * the summed score equal [processBoard].
 */
data class MergeWave(val before: Grid, val groups: List<MergeGroup>, val merged: Grid, val settled: Grid, val score: Long)

fun resolveWaves(grid: Grid): List<MergeWave> {
    val waves = mutableListOf<MergeWave>()
    var current = grid
    while (true) {
        val groups = findMergeGroups(current)
        if (groups.isEmpty()) break
        val described = groups.map { group ->
            MergeGroup(group, mergeTarget(group), (current.get(group.first())!!.toLong() * 2).coerceAtMost(1L shl 30).toInt())
        }
        val result = applyMerges(current, groups)
        val settled = applyGravity(result.grid)
        waves += MergeWave(current, described, result.grid, settled, result.score * (waves.size + 1))
        current = settled
    }
    return waves
}

/** Lift the bottom stone to the top of its stack without creating gaps or stones. */
fun cycleColumn(grid: Grid, col: Int): Grid? {
    if (col !in 0 until COLS) return null
    val values = grid.mapNotNull { it[col] }
    if (values.size < 2 || values.distinct().size < 2) return null
    val rotated = listOf(values.last()) + values.dropLast(1)
    var result = grid
    for (row in 0 until ROWS) {
        result = result.set(row, col, rotated.getOrNull(row - (ROWS - rotated.size)))
    }
    return result
}

/**
 * Fixed twelve-stone bags add distinct values in three stages without reacting to
 * the board. Existing bags finish before a new stage begins, and 2s never retire.
 */
class StoneBag(private val random: kotlin.random.Random = kotlin.random.Random.Default) {
    private val remaining = ArrayDeque<Int>()
    fun next(level: Int): Int {
        if (remaining.isEmpty()) {
            val counts = when {
                level >= 4 -> listOf(2, 2, 2, 2, 2, 2)
                level >= 2 -> listOf(3, 3, 2, 2, 2)
                else -> listOf(4, 3, 3, 2)
            }
            val values = counts.flatMapIndexed { index, count -> List(count) { 2 shl index } }
            remaining.addAll(values.shuffled(random))
        }
        return remaining.removeFirst()
    }
    fun reset() = remaining.clear()
    /** Stones still waiting in the current bag, used to persist a free-play run. */
    fun snapshot(): List<Int> = remaining.toList()
    fun restore(values: List<Int>) { remaining.clear(); remaining.addAll(values) }
}

fun canCycle(state: GameState): Boolean = state.energy >= 3 && cycleColumn(state.grid, state.currentCol) != null

fun hasMoves(grid: Grid, energy: Int): Boolean = !grid.isGameOver() ||
    (energy >= 3 && (0 until COLS).any { cycleColumn(grid, it) != null })

fun resolveTurn(state: GameState, board: Grid, isDrop: Boolean): GameState {
    val result = processBoard(board)
    val energy = (state.energy - (if (isDrop) 0 else 3) + result.chains).coerceIn(0, 6)
    val score = state.score + result.score
    return state.copy(
        grid = result.grid, score = score, highScore = maxOf(state.highScore, score),
        energy = energy, turnKey = state.turnKey + 1,
        mergeKey = state.mergeKey + if (result.chains > 0) 1 else 0,
        lastChains = result.chains, lastGain = result.score, lastMergeValue = result.highestMergeValue,
        bestChain = maxOf(state.bestChain, result.chains),
        dropCount = state.dropCount + if (isDrop) 1 else 0, autoDropProgress = 0f,
        phase = if (hasMoves(result.grid, energy)) GamePhase.Playing else GamePhase.GameOver,
        lastDropRow = -1, lastDropCol = -1, lastDropValue = 0,
    )
}
