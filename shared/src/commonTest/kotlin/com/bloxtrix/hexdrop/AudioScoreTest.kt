package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.audio.AudioScore
import kotlin.math.*
import kotlin.test.*

class AudioScoreTest {
    @Test fun effectsHaveHeadroomAndQuietEdges() {
        listOf("move","button","land","merge","chain","record","over","warning").forEach { name ->
            val pcm=AudioScore.effect(name)
            assertTrue(pcm.any { abs(it.toInt())>200 });assertTrue(pcm.all {abs(it.toInt())<26000})
            assertEquals(0,pcm.first().toInt());assertTrue(pcm.takeLast(20).all { abs(it.toInt())<120 },"$name ends with a release, not a click")
        }
    }
    @Test fun musicIsStereoAndLoopsWithoutLargeBoundaryJump() {
        val pcm=AudioScore.music
        assertEquals(24*AudioScore.rate*2,pcm.size)
        repeat(2){channel->assertTrue(abs(pcm[channel]-pcm[pcm.size-2+channel])<500)}
        assertTrue(pcm.all {abs(it.toInt())<15000})
        val wav=AudioScore.wav(pcm,2)
        assertEquals(pcm.size*2+44,wav.size);assertEquals(2,wav[22].toInt())
    }
}
