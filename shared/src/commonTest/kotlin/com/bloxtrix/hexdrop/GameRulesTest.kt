package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random
import kotlin.test.*

class GameRulesTest {
    @Test fun hexNeighboursAreReciprocalAndBounded() {
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val p = HexPos(r, c)
            assertEquals(p.neighbors().size, p.neighbors().distinct().size)
            p.neighbors().forEach { assertTrue(it.isValid()); assertTrue(p in it.neighbors()) }
        }
        assertEquals(6, HexPos(3, 2).neighbors().size)
    }
    @Test fun dropRejectsInvalidAndFullColumns() {
        assertNull(dropPiece(emptyGrid(), -1, 2))
        assertNull(dropPiece(emptyGrid(), COLS, 2))
        val full = List(ROWS) { List<Int?>(COLS) { 2 } }
        assertNull(dropPiece(full, 0, 4))
        assertEquals(ROWS - 1, dropPiece(emptyGrid(), 0, 2)!!.landRow)
    }
    @Test fun threeNeighboursFuseAndGravitySettles() {
        val board = emptyGrid().set(6, 0, 2).set(6, 1, 2).set(5, 1, 2)
        val result = processBoard(board)
        assertEquals(1, result.chains)
        assertEquals(12L, result.score)
        assertEquals(listOf(4), result.grid.flatten().filterNotNull())
        assertEquals(4, result.grid[6][1])
    }
    @Test fun twoStonesNeverMerge() {
        val board = emptyGrid().set(6, 0, 2).set(6, 1, 2)
        assertEquals(board, processBoard(board).grid)
        assertEquals(0, processBoard(board).chains)
    }
    @Test fun cascadeHasIncreasingWaveScore() {
        val board = emptyGrid().set(6, 0, 2).set(6, 1, 2).set(5, 1, 2)
            .set(6, 2, 4).set(5, 2, 4)
        val result = processBoard(board)
        assertEquals(2, result.chains)
        assertEquals(12L + 24L * 2, result.score)
        assertEquals(listOf(8), result.grid.flatten().filterNotNull())
    }
    @Test fun cyclePreservesOrderAndPopulation() {
        val board = emptyGrid().set(4, 2, 2).set(5, 2, 4).set(6, 2, 8)
        val cycled = cycleColumn(board, 2)!!
        assertEquals(listOf(8, 2, 4), cycled.mapNotNull { it[2] })
        assertEquals(board, cycleColumn(cycleColumn(cycled, 2)!!, 2))
        assertNull(cycleColumn(emptyGrid(), 2))
        assertNull(cycleColumn(emptyGrid().set(6, 0, 2).set(5, 0, 2), 0))
    }
    @Test fun cycleSpendsChargeAndDoesNotConsumeTheNextStone() {
        val board = emptyGrid().set(5, 0, 4).set(6, 0, 2)
        val s = GameState(grid = board, energy = 3, phase = GamePhase.Playing)
        val result = resolveTurn(s, cycleColumn(board, 0)!!, false)
        assertEquals(0, result.energy)
        assertEquals(s.current, result.current)
        assertEquals(s.nextQueue, result.nextQueue)
        assertEquals(0, result.dropCount)
    }
    @Test fun fullBoardMayBeRescuedOnlyWithCharge() {
        val full = List(ROWS) { r -> List<Int?>(COLS) { c -> 1 shl ((r * COLS + c) % 20 + 1) } }
        assertTrue(hasMoves(full, 3))
        assertFalse(hasMoves(full, 2))
        assertFalse(hasMoves(List(ROWS) { List<Int?>(COLS) { 2 } }, 6))
    }
    @Test fun energyIsCappedAndRecordNeverFalls() {
        val s = GameState(energy = 6, highScore = 900)
        val board = emptyGrid().set(6, 0, 2).set(6, 1, 2).set(5, 1, 2)
        val result = resolveTurn(s, board, true)
        assertEquals(6, result.energy)
        assertEquals(900L, result.highScore)
        assertEquals(1, result.dropCount)
    }
    @Test fun bagDistributionAndSeedAreReproducible() {
        val a = StoneBag(Random(12)); val b = StoneBag(Random(12))
        val values = List(12) { a.next(1) }
        assertEquals(values, List(12) { b.next(1) })
        assertEquals(mapOf(2 to 4, 4 to 3, 8 to 3, 16 to 2), values.groupingBy { it }.eachCount())
    }
    @Test fun everyStageUsesTwelveStonesAndKeepsSmallValuesAvailable() {
        val stages = listOf(
            1 to mapOf(2 to 4, 4 to 3, 8 to 3, 16 to 2),
            2 to mapOf(2 to 3, 4 to 3, 8 to 2, 16 to 2, 32 to 2),
            3 to mapOf(2 to 3, 4 to 3, 8 to 2, 16 to 2, 32 to 2),
            4 to mapOf(2 to 2, 4 to 2, 8 to 2, 16 to 2, 32 to 2, 64 to 2),
            1000 to mapOf(2 to 2, 4 to 2, 8 to 2, 16 to 2, 32 to 2, 64 to 2),
        )
        for ((level, expected) in stages) repeat(5) { seed ->
            val bag = StoneBag(Random(seed))
            repeat(3) {
                val values = List(12) { bag.next(level) }
                assertEquals(expected, values.groupingBy { it }.eachCount())
                assertTrue(2 in values)
                assertTrue(values.all { it in 2..64 && (it and (it - 1)) == 0 })
            }
        }
    }
    @Test fun stageChangesNeverReplaceTheUnfinishedBag() {
        val bag = StoneBag(Random(73))
        val reference = StoneBag(Random(73))
        val opening = List(5) { bag.next(1) }
        val remaining = List(7) { bag.next(4) }
        assertEquals(List(12) { reference.next(1) }, opening + remaining)
        assertEquals(mapOf(2 to 2, 4 to 2, 8 to 2, 16 to 2, 32 to 2, 64 to 2),
            List(12) { bag.next(4) }.groupingBy { it }.eachCount())
    }
    @Test fun resetDiscardsOldStonesAndPreservesSeedReproducibility() {
        val a = StoneBag(Random(28)); val b = StoneBag(Random(28))
        assertEquals(List(5) { a.next(4) }, List(5) { b.next(4) })
        a.reset(); b.reset()
        val restarted = List(12) { a.next(1) }
        assertEquals(restarted, List(12) { b.next(1) })
        assertEquals(mapOf(2 to 4, 4 to 3, 8 to 3, 16 to 2), restarted.groupingBy { it }.eachCount())
    }
    @Test fun incomingStageChangesAtTheAdvertisedDropBoundaries() {
        for ((drops, stage, maxValue) in listOf(
            Triple(0, 1, 16), Triple(17, 1, 16), Triple(18, 2, 32),
            Triple(53, 2, 32), Triple(54, 3, 64), Triple(10000, 3, 64),
        )) {
            val state = GameState(dropCount = drops)
            assertEquals(stage, state.incomingStage)
            assertEquals(maxValue, state.incomingMaxValue)
        }
    }
    @Test fun largeValuesDoNotOverflow() {
        val n = 1 shl 30
        val board = emptyGrid().set(6, 0, n).set(6, 1, n).set(5, 1, n)
        val result = processBoard(board)
        assertEquals(n, result.highestMergeValue)
        assertTrue(result.score > Int.MAX_VALUE)
        assertTrue(result.grid.flatten().filterNotNull().all { it > 0 })
    }
    @Test fun simulatedRunsStayStableAndCompact() {
        repeat(30) { seed ->
            val random = Random(seed)
            val bag = StoneBag(random)
            var state = GameState(phase = GamePhase.Playing)
            repeat(150) {
                if (state.phase == GamePhase.GameOver) return@repeat
                val cols = (0 until COLS).filter { !state.grid.isColumnFull(it) }
                if (cols.isNotEmpty()) {
                    val col = cols.random(random)
                    state = resolveTurn(state, dropPiece(state.grid, col, bag.next(state.level))!!.grid, true)
                } else {
                    val col = (0 until COLS).firstOrNull { cycleColumn(state.grid, it) != null }
                    if (col != null && state.energy >= 3) state = resolveTurn(state, cycleColumn(state.grid, col)!!, false)
                }
                assertTrue(findMergeGroups(state.grid).isEmpty())
                assertEquals(applyGravity(state.grid), state.grid)
                assertTrue(state.energy in 0..6)
                assertTrue(state.score >= 0)
                assertEquals(state.grid.isGameOver() && !hasMoves(state.grid, state.energy), state.phase == GamePhase.GameOver)
            }
        }
    }
}
