package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random
import kotlin.test.*

/** The presentation waves must describe exactly the rule engine's result. */
class MergeWavesTest {
    @Test fun wavesMatchProcessBoardOnRandomBoards() {
        val random = Random(2026)
        var cascades = 0
        repeat(3000) {
            var grid: Grid = emptyGrid()
            repeat(random.nextInt(4, 30)) {
                val col = random.nextInt(COLS)
                dropPiece(grid, col, 2 shl random.nextInt(4))?.let { grid = it.grid }
            }
            val processed = processBoard(grid)
            val waves = resolveWaves(grid)
            assertEquals(processed.chains, waves.size)
            assertEquals(processed.score, waves.sumOf { it.score })
            assertEquals(processed.grid, waves.lastOrNull()?.settled ?: grid)
            waves.zipWithNext().forEach { (a, b) -> assertEquals(a.settled, b.before) }
            waves.forEach { wave ->
                wave.groups.forEach { g ->
                    assertTrue(g.target in g.cells)
                    assertEquals(g.value, wave.merged[g.target.row][g.target.col])
                    assertTrue(g.cells.size >= 3)
                }
            }
            if (waves.size > 1) cascades++
        }
        assertTrue(cascades > 0, "random boards should include cascades")
    }
}
