package com.maslarski.sudoku.data.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.maslarski.sudoku.domain.model.AppSettings
import com.maslarski.sudoku.domain.repository.GameFeedback
import com.maslarski.sudoku.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceGameFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
) : GameFeedback {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())
    private val toneGenerator by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun cellSelected() {
        play(ToneGenerator.TONE_PROP_BEEP, 40) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        }
    }

    override fun mistake() {
        play(ToneGenerator.TONE_PROP_NACK, 150) {
            VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    override fun puzzleCompleted() {
        play(ToneGenerator.TONE_PROP_ACK, 200) {
            VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1)
        }
    }

    private fun play(tone: Int, durationMillis: Int, vibration: () -> VibrationEffect) {
        val currentSettings = settings.value
        if (currentSettings.soundEnabled) runCatching { toneGenerator.startTone(tone, durationMillis) }
        if (currentSettings.vibrationEnabled) {
            runCatching {
                vibrator?.takeIf { it.hasVibrator() }?.vibrate(vibration())
            }
        }
    }
}
