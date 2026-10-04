package com.bloxtrix.hexdrop.audio

import androidx.compose.runtime.compositionLocalOf

expect class SoundEngine() {
    fun playMove()
    fun playDrop()
    fun playLand(hard: Boolean)
    fun playMerge(tier: Int)       // tier = log2(value)-1, so 2→0, 4→1, 8→2 …
    fun playChainLink(count: Int)
    fun playChainEnd(total: Int)
    fun playButtonPress()
    fun playGameOver()
    fun playNewHighScore()
    fun playWarning()
    fun setEnabled(on: Boolean)
    fun setVolume(level: Float)
    fun stop()
    fun release()
}

expect class MusicEngine() {
    fun start()
    fun stop()
    fun setIntensity(level: Float) // 0=calm … 1=intense
    fun triggerMergeAccent()
    fun triggerChainAccent(count: Int)
    fun triggerDangerMode(active: Boolean)
    fun setEnabled(on: Boolean)
    fun setVolume(level: Float)
    fun release()
}

val LocalSoundEngine  = compositionLocalOf { SoundEngine()  }
val LocalMusicEngine  = compositionLocalOf { MusicEngine()  }
