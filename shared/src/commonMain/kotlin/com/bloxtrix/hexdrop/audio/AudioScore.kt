package com.bloxtrix.hexdrop.audio

import kotlin.math.*

/** Original D-minor pentatonic score. All event positions are samples, never milliseconds. */
object AudioScore {
    const val rate = 22050
    private val notes = doubleArrayOf(293.665,349.228,391.995,440.0,523.251,587.33,698.456,783.991)
    fun effect(name: String, variant: Int = 0): ShortArray {
        val length = when(name) { "merge" -> .48; "chain" -> .65; "record" -> 1.3; "over" -> 1.1; "warning" -> .24; else -> .12 }
        val data = DoubleArray((rate * length).toInt())
        fun bell(start: Double, frequency: Double, gain: Double, decay: Double) {
            val offset=(start*rate).toInt()
            for(i in offset until data.size) {
                val t=(i-offset).toDouble()/rate
                val attack=min(1.0,t/.004)
                data[i]+=gain*attack*exp(-t/decay)*(sin(2*PI*frequency*t)+.23*sin(2*PI*frequency*2.76*t)*exp(-t/.07))
            }
        }
        when(name) {
            "merge" -> { bell(0.0,notes[variant.coerceIn(0,7)],.48,.13); bell(.018,notes[variant.coerceIn(0,7)]*2,.1,.18) }
            "chain" -> repeat(3) { bell(it*.11,notes[(variant+it).coerceIn(0,7)],.3,.14) }
            "record" -> listOf(0,2,3,5).forEachIndexed { i,n -> bell(i*.17,notes[n],.3,.25) }
            // Fundamentals stay above ~240 Hz so phone speakers reproduce every cue.
            "over" -> listOf(3,1,0).forEachIndexed { i,n -> bell(i*.22,notes[n],.24,.26) }
            "warning" -> { bell(0.0,392.0,.15,.055); bell(.11,392.0,.11,.055) }
            "land" -> { bell(0.0,246.94,.34,.03); bell(.002,987.77,.09,.016) }
            "drop" -> bell(0.0,329.63,.16,.04)
            else -> bell(0.0,if(name=="move") 740.0 else 587.33,.16,.018)
        }
        // A raised-cosine release removes the click of a truncated decay.
        val fade=(rate*.025).toInt().coerceAtMost(data.size)
        for(i in 0 until fade) data[data.size-fade+i]*=0.5*(1+cos(PI*i/fade))
        return pcm(data)
    }
    // Eight bars at 80 BPM; sparse felt plucks, warm bass and slow, airy chords.
    val music: ShortArray by lazy {
        val duration=24.0; val mono=DoubleArray((duration*rate).toInt())
        val roots=doubleArrayOf(146.832,116.541,130.813,110.0)
        val melody=intArrayOf(0,2,3,5,3,2,1,0,2,3,5,7,5,3,2,1)
        fun add(start:Double,len:Double,freq:Double,gain:Double,pad:Boolean) {
            val offset=(start*rate).toInt(); val count=(len*rate).toInt()
            for(i in 0 until count) {
                val t=i.toDouble()/rate
                val env=if(pad) sin(PI*i/count).pow(2) else min(1.0,t/.009)*exp(-t/.32)*(1.0-i.toDouble()/count)
                // Low notes carry 2nd/3rd harmonics so small speakers still convey the bass line.
                val low=freq<160
                val v=sin(2*PI*freq*t)+(if(low) .45 else .15)*sin(2*PI*freq*2*t)+(if(low) .22 else 0.0)*sin(2*PI*freq*3*t)
                mono[(offset+i)%mono.size]+=v*env*gain
            }
        }
        repeat(8) { bar ->
            val root=roots[bar%4]; val start=bar*3.0
            add(start,3.0,root/2,.075,true)
            listOf(1.0,1.5,2.0).forEach { add(start,3.0,root*it,.027,true) }
            repeat(2) { beat -> add(start+beat*1.5,1.45,notes[melody[bar*2+beat]],.095,false) }
            add(start+.75,.9,root*2,.035,false)
        }
        // Stereo room reflection is circular, preserving the exact loop seam.
        val stereo=DoubleArray(mono.size*2)
        mono.indices.forEach { i ->
            stereo[i*2]=1.3*(mono[i]+.18*mono[(i-milliseconds(137)+mono.size)%mono.size])
            stereo[i*2+1]=1.3*(mono[i]+.18*mono[(i-milliseconds(211)+mono.size)%mono.size])
        }
        pcm(stereo)
    }
    private fun milliseconds(ms:Int)=rate*ms/1000
    private fun pcm(values:DoubleArray)=ShortArray(values.size) { i -> (tanh(values[i]) * 26000).toInt().toShort() }
    fun wav(samples:ShortArray,channels:Int=1):ByteArray {
        val bytes=ByteArray(44+samples.size*2)
        fun text(at:Int,s:String){s.forEachIndexed { i,c -> bytes[at+i]=c.code.toByte() }}
        fun number(at:Int,n:Int,count:Int){repeat(count){bytes[at+it]=(n ushr (it*8)).toByte()}}
        text(0,"RIFF");number(4,bytes.size-8,4);text(8,"WAVEfmt ");number(16,16,4)
        number(20,1,2);number(22,channels,2);number(24,rate,4);number(28,rate*channels*2,4)
        number(32,channels*2,2);number(34,16,2);text(36,"data");number(40,samples.size*2,4)
        samples.forEachIndexed { i,v -> number(44+i*2,v.toInt(),2) }
        return bytes
    }
}
