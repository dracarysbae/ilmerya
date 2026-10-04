package com.bloxtrix.hexdrop.audio

import android.media.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

private fun track(data: ShortArray, channels: Int): AudioTrack = AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(if (channels == 2) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
    .setAudioFormat(AudioFormat.Builder().setSampleRate(AudioScore.rate)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(if(channels==2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build())
    .setBufferSizeInBytes(data.size*2).setTransferMode(AudioTrack.MODE_STATIC).build().also {
        it.write(data,0,data.size)
    }

actual class SoundEngine actual constructor() {
    private val scope=CoroutineScope(Dispatchers.IO+SupervisorJob())
    private val voices=ConcurrentHashMap.newKeySet<AudioTrack>()
    private val cache=ConcurrentHashMap<String,ShortArray>()
    @Volatile private var enabled=true
    @Volatile private var volume=.7f
    @Volatile private var generation=0
    private fun emit(name:String,variant:Int=0) {
        if(!enabled || voices.size>=6) return
        val stamp=generation
        scope.launch {
            var audio:AudioTrack?=null
            try {
                val data=cache.getOrPut("$name:$variant") { AudioScore.effect(name,variant) }
                if(!enabled || generation!=stamp) return@launch
                audio=track(data,1); voices.add(audio)
                audio.setVolume(volume)
                if(!enabled || generation!=stamp) return@launch
                audio.play(); delay(data.size*1000L/AudioScore.rate+30)
            } catch(e:CancellationException) { throw e }
            catch(_:RuntimeException) { /* Route may disappear while an effect plays. */ }
            finally { audio?.let { voices.remove(it); runCatching { it.stop() }; it.release() } }
        }
    }
    actual fun playMove()=emit("move")
    actual fun playDrop()=emit("drop")
    actual fun playLand(hard:Boolean)=emit("land")
    actual fun playMerge(tier:Int)=emit("merge",tier)
    actual fun playChainLink(count:Int)=emit("chain",count)
    actual fun playChainEnd(total:Int)=emit("chain",2)
    actual fun playButtonPress()=emit("button")
    actual fun playGameOver()=emit("over")
    actual fun playNewHighScore()=emit("record")
    actual fun playWarning()=emit("warning")
    actual fun setEnabled(on:Boolean) { enabled=on; if(!on) stop() }
    actual fun setVolume(level:Float) { volume=level.coerceIn(0f,1f); voices.forEach { runCatching { it.setVolume(volume) } } }
    actual fun stop() { generation++; voices.forEach { runCatching { it.pause(); it.flush() } } }
    actual fun release() { stop(); scope.cancel() }
}

actual class MusicEngine actual constructor() {
    private val scope=CoroutineScope(Dispatchers.IO+SupervisorJob())
    private var audio:AudioTrack?=null
    private var job:Job?=null
    private var wanted=false
    private var enabled=true
    private var volume=.45f
    private var intensity=0f
    @Synchronized actual fun start() { wanted=true; launchIfNeeded() }
    @Synchronized private fun launchIfNeeded() {
        if(!wanted || !enabled || audio!=null || job?.isActive==true) return
        job=scope.launch {
            val data=AudioScore.music
            synchronized(this@MusicEngine) {
                if(!isActive || !wanted || !enabled) return@synchronized
                try {
                    audio=track(data,2).also { it.setLoopPoints(0,data.size/2,-1); it.setVolume(gain()); it.play() }
                } catch(_:RuntimeException) { audio?.release(); audio=null }
            }
        }
    }
    private fun gain()=volume*(.7f+.3f*intensity)
    @Synchronized private fun silence() { job?.cancel(); job=null; audio?.let { runCatching { it.stop() }; it.release() }; audio=null }
    @Synchronized actual fun stop() { wanted=false; silence() }
    @Synchronized actual fun setEnabled(on:Boolean) { enabled=on; if(on) launchIfNeeded() else silence() }
    @Synchronized actual fun setVolume(level:Float) { volume=level.coerceIn(0f,1f); audio?.setVolume(gain()) }
    @Synchronized actual fun setIntensity(level:Float) { intensity=level.coerceIn(0f,1f); audio?.setVolume(gain()) }
    actual fun triggerMergeAccent() = Unit // Merge voice already carries the musical accent.
    actual fun triggerChainAccent(count:Int) = Unit
    actual fun triggerDangerMode(active:Boolean) = Unit // One restrained warning, no competing bass loop.
    actual fun release() { stop(); scope.cancel() }
}
