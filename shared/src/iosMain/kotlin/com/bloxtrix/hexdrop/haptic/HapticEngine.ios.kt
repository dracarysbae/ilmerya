package com.bloxtrix.hexdrop.haptic

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** UIKit impact feedback. Calls arrive on the main thread from Compose; devices without a Taptic Engine ignore them. */
actual class HapticEngine actual constructor() {
    private var enabled = true
    private val generators = mutableMapOf<HapticStyle, UIImpactFeedbackGenerator>()

    private fun generator(style: HapticStyle) = generators.getOrPut(style) {
        UIImpactFeedbackGenerator(when (style) {
            HapticStyle.LIGHT -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            HapticStyle.MEDIUM -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            HapticStyle.HEAVY -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            HapticStyle.RIGID -> UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid
            HapticStyle.SOFT -> UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft
        }).also { it.prepare() }
    }

    actual fun impact(style: HapticStyle) {
        if (!enabled) return
        val feedback = generator(style)
        feedback.impactOccurred()
        feedback.prepare()
    }

    /** Android-style waveform approximated by one impact per non-silent segment. */
    actual fun pattern(durations: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        var offsetMs = 0L
        durations.forEachIndexed { index, duration ->
            val amplitude = amplitudes.getOrElse(index) { 0 }
            if (amplitude > 0) {
                val intensity = (amplitude / 255.0).coerceIn(0.1, 1.0)
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, offsetMs * 1_000_000), dispatch_get_main_queue()) {
                    if (enabled) generator(HapticStyle.MEDIUM).impactOccurredWithIntensity(intensity)
                }
            }
            offsetMs += duration
        }
    }

    actual fun setEnabled(on: Boolean) { enabled = on }
}
