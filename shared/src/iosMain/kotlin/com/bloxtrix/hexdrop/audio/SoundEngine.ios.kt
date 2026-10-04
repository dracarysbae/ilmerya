@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.bloxtrix.hexdrop.audio

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.*

/** Ambient category: silenced by the ring/silent switch and mixed with the player's own audio. */
fun configureGameAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryAmbient, error = null)
    session.setActive(true, error = null)
}

private fun player(samples: ShortArray, channels: Int = 1): AVAudioPlayer? {
    val wav = AudioScore.wav(samples, channels)
    val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
    return AVAudioPlayer(data = data, error = null)
}

/** Effects are prepared once per name/variant; two players per effect let rapid repeats overlap briefly. */
actual class SoundEngine actual constructor() {
    private var enabled = true
    private var volume = .7f
    private val voices = mutableMapOf<String, List<AVAudioPlayer>>()
    private val turn = mutableMapOf<String, Int>()
    private fun emit(name: String, variant: Int = 0) {
        if (!enabled) return
        val key = "$name:$variant"
        val pair = voices.getOrPut(key) {
            val samples = AudioScore.effect(name, variant)
            listOfNotNull(player(samples), player(samples)).onEach { it.prepareToPlay() }
        }
        if (pair.isEmpty()) return
        val index = (turn[key] ?: 0) % pair.size
        turn[key] = index + 1
        val audio = pair[index]
        audio.stop(); audio.currentTime = 0.0; audio.volume = volume; audio.play()
    }
    actual fun playMove() = emit("move")
    actual fun playDrop() = emit("drop")
    actual fun playLand(hard: Boolean) = emit("land")
    actual fun playMerge(tier: Int) = emit("merge", tier.coerceIn(0, 7))
    actual fun playChainLink(count: Int) = emit("chain", count.coerceIn(0, 7))
    actual fun playChainEnd(total: Int) = emit("chain", 2)
    actual fun playButtonPress() = emit("button")
    actual fun playGameOver() = emit("over")
    actual fun playNewHighScore() = emit("record")
    actual fun playWarning() = emit("warning")
    actual fun setEnabled(on: Boolean) { enabled = on; if (!on) stop() }
    actual fun setVolume(level: Float) { volume = level.coerceIn(0f, 1f); voices.values.flatten().forEach { it.volume = volume } }
    actual fun stop() { voices.values.flatten().forEach { it.stop() } }
    actual fun release() { stop(); voices.clear() }
}

/** The 24-second loop is generated once off the main thread; stopping pauses, so a resume continues smoothly. */
actual class MusicEngine actual constructor() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audio: AVAudioPlayer? = null
    private var job: Job? = null
    private var enabled = true
    private var wanted = false
    private var volume = .45f
    private var intensity = 0f
    private fun gain() = volume * (.7f + .3f * intensity)
    actual fun start() {
        wanted = true
        if (!enabled) return
        audio?.let { it.volume = gain(); it.play(); return }
        if (job?.isActive == true) return
        job = scope.launch {
            val samples = withContext(Dispatchers.Default) { AudioScore.music }
            if (!wanted || !enabled) return@launch
            audio = player(samples, 2)?.also { it.numberOfLoops = -1; it.volume = gain(); it.prepareToPlay(); it.play() }
        }
    }
    actual fun stop() { wanted = false; job?.cancel(); audio?.pause() }
    actual fun setEnabled(on: Boolean) { enabled = on; if (!on) audio?.pause() else if (wanted) start() }
    actual fun setVolume(level: Float) { volume = level.coerceIn(0f, 1f); audio?.volume = gain() }
    actual fun setIntensity(level: Float) { intensity = level.coerceIn(0f, 1f); audio?.volume = gain() }
    actual fun triggerMergeAccent() = Unit
    actual fun triggerChainAccent(count: Int) = Unit
    actual fun triggerDangerMode(active: Boolean) = Unit
    actual fun release() { stop(); audio?.stop(); audio = null; scope.cancel() }
}
