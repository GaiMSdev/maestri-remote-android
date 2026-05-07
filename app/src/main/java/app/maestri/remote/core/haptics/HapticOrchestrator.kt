package app.maestri.remote.core.haptics

import android.content.Context
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import app.maestri.remote.domain.model.AgentStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    fun triggerFeedback(status: AgentStatus) {
        val effect = when (status) {
            AgentStatus.RUNNING -> {
                // Subtle pulse for running state
                VibrationEffect.createWaveform(longArrayOf(0, 10, 500), intArrayOf(0, 30, 0), 1)
            }
            AgentStatus.NEEDS_INPUT -> {
                // Distinct "shoulder tap"
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 100)
                    .compose()
            }
            AgentStatus.ERROR -> {
                // Heavy thud for errors
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            }
            else -> null
        }

        effect?.let {
            val combined = CombinedVibration.createParallel(it)
            vibratorManager.vibrate(combined)
        }
    }

    fun stopAll() {
        vibratorManager.cancel()
    }
}
