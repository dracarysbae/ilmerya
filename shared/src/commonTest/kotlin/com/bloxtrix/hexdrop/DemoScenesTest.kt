package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.model.*
import com.bloxtrix.hexdrop.ui.DemoScenes
import kotlin.test.*

/** Screenshot scenes must be real, legal game states produced by the engine. */
class DemoScenesTest {
    @Test fun boardSceneIsAPlayableMidGame() {
        val s = DemoScenes.board()
        assertEquals(GamePhase.Playing, s.phase)
        val stones = s.grid.flatten().filterNotNull()
        assertTrue(stones.size in 12..26, "stones=${stones.size}")
        assertTrue(stones.max() >= 64)
        assertTrue((0 until COLS).any { s.grid[0][it] == null })
    }
    @Test fun resultSceneIsAFinishedRun() {
        val s = DemoScenes.result()
        assertEquals(GamePhase.GameOver, s.phase)
        assertTrue(s.score > 0)
    }
}
