package com.bloxtrix.hexdrop.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual class HapticEngine actual constructor() {
    private var vibrator: Vibrator? = null
    private var enabled = true

    fun init(ctx: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    actual fun impact(style: HapticStyle) {
        if (!enabled) return
        val (ms, amp) = when (style) {
            HapticStyle.LIGHT  -> 30  to 60
            HapticStyle.MEDIUM -> 50  to 100
            HapticStyle.HEAVY  -> 80  to 160
            HapticStyle.RIGID  -> 100 to 220
            HapticStyle.SOFT   -> 60  to 40
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms.toLong(), amp))
        }
    }

    actual fun pattern(durations: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(durations, amplitudes, -1))
        }
    }

    actual fun setEnabled(on: Boolean) { enabled = on }
}
