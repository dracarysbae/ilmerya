package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.model.*
import com.bloxtrix.hexdrop.viewmodel.HexDropViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameClockTest {
    @Test fun menuAndCalmNeverAutoDrop() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = HexDropViewModel()
        try {
            advanceTimeBy(20_000)
            assertEquals(0, vm.state.value.dropCount)
            vm.restart(GameMode.Calm)
            advanceTimeBy(20_000)
            assertEquals(0, vm.state.value.dropCount)
        } finally { vm.dispose(); Dispatchers.resetMain() }
    }
    @Test fun flowDropsAndPauseFreezesClock() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = HexDropViewModel()
        try {
            vm.restart(GameMode.Flow)
            advanceTimeBy(6200)
            assertEquals(1, vm.state.value.dropCount)
            vm.pause()
            val paused = vm.state.value
            advanceTimeBy(30_000)
            // The visual tap guard may settle while paused; the game and clock may not advance.
            assertEquals(paused.copy(resolving = false), vm.state.value)
            vm.togglePause()
            advanceTimeBy(6200)
            assertEquals(2, vm.state.value.dropCount)
        } finally { vm.dispose(); Dispatchers.resetMain() }
    }
    @Test fun fastTapsCannotOverlapAndRestartCancelsSettlement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = HexDropViewModel()
        try {
            vm.restart()
            repeat(20) { vm.drop() }
            assertEquals(1, vm.state.value.dropCount)
            vm.restart()
            advanceTimeBy(300)
            assertEquals(0, vm.state.value.dropCount)
            assertFalse(vm.state.value.resolving)
            vm.drop(-1)
            assertEquals(0, vm.state.value.dropCount)
        } finally { vm.dispose(); Dispatchers.resetMain() }
    }
    @Test fun invalidDropDoesNotBuyExtraTime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = HexDropViewModel()
        try {
            vm.restart(GameMode.Flow)
            advanceTimeBy(3000)
            val progress = vm.state.value.autoDropProgress
            vm.drop(-1)
            assertEquals(progress, vm.state.value.autoDropProgress)
            advanceTimeBy(3200)
            assertEquals(1, vm.state.value.dropCount)
        } finally { vm.dispose(); Dispatchers.resetMain() }
    }
}
