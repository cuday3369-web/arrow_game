package com.yogi.exitarrow

import android.media.AudioManager
import android.media.ToneGenerator
import java.util.concurrent.Executors

object GameAudio {

    private var toneGen: ToneGenerator? = null
    private val executor = Executors.newSingleThreadExecutor()

    init {
        toneGen = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {
            null
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        executor.execute {
            try {
                toneGen?.startTone(toneType, durationMs)
            } catch (_: Exception) {
                // Audio synthesis is optional; fallback gracefully if audio hardware is busy
            }
        }
    }

    fun playTap(enabled: Boolean) {
        if (!enabled) return
        playTone(ToneGenerator.TONE_PROP_BEEP, 30)
    }

    fun playArrowExit(enabled: Boolean) {
        if (!enabled) return
        playTone(ToneGenerator.TONE_PROP_PROMPT, 70)
    }

    fun playBlocked(enabled: Boolean) {
        if (!enabled) return
        playTone(ToneGenerator.TONE_SUP_ERROR, 120)
    }

    fun playCombo(enabled: Boolean, comboCount: Int) {
        if (!enabled) return
        val tone = when {
            comboCount >= 7 -> ToneGenerator.TONE_DTMF_A
            comboCount >= 5 -> ToneGenerator.TONE_DTMF_9
            comboCount >= 3 -> ToneGenerator.TONE_DTMF_6
            else -> ToneGenerator.TONE_DTMF_3
        }
        playTone(tone, 80)
    }

    fun playHint(enabled: Boolean) {
        if (!enabled) return
        playTone(ToneGenerator.TONE_PROP_ACK, 60)
    }

    fun playVictory(enabled: Boolean) {
        if (!enabled) return
        executor.execute {
            try {
                toneGen?.startTone(ToneGenerator.TONE_DTMF_1, 100)
                Thread.sleep(120)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_5, 100)
                Thread.sleep(120)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_9, 150)
                Thread.sleep(170)
                toneGen?.startTone(ToneGenerator.TONE_DTMF_A, 220)
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        try {
            toneGen?.release()
            toneGen = null
        } catch (_: Exception) {
        }
    }
}
