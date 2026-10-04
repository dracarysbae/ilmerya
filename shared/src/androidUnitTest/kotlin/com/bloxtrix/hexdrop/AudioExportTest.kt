package com.bloxtrix.hexdrop
import com.bloxtrix.hexdrop.audio.AudioScore
import java.io.File
import kotlin.test.Test
class AudioExportTest {
    @Test fun exportAuditionFromProductionSynthesizer() {
        val output=File("build/reports/audio").also{it.mkdirs()}
        File(output,"ilmerya-original-score.wav").writeBytes(AudioScore.wav(AudioScore.music,2))
        val effects=File(output,"effects").also{it.mkdirs()}
        for (name in listOf("move","button","drop","land","warning","over","record")) File(effects,"$name.wav").writeBytes(AudioScore.wav(AudioScore.effect(name)))
        for (tier in 0..7) File(effects,"merge-$tier.wav").writeBytes(AudioScore.wav(AudioScore.effect("merge",tier)))
        for (count in 2..7) File(effects,"chain-$count.wav").writeBytes(AudioScore.wav(AudioScore.effect("chain",count)))
    }
}
