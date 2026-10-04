package com.bloxtrix.hexdrop.haptic

import androidx.compose.runtime.compositionLocalOf

enum class HapticStyle { LIGHT, MEDIUM, HEAVY, RIGID, SOFT }

expect class HapticEngine() {
    fun impact(style: HapticStyle)
    fun pattern(durations: LongArray, amplitudes: IntArray)
    fun setEnabled(on: Boolean)
}

val LocalHapticEngine = compositionLocalOf { HapticEngine() }
