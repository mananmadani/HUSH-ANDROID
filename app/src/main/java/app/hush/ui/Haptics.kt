package app.hush.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import app.hush.Prefs

/** How strong a tap should feel. */
enum class Tap { Tick, Click, Heavy, Confirm }

/**
 * Returns a function that gives a small vibration. It does nothing when the person turned
 * "Haptic feedback" off in Settings, and Android itself skips it when touch vibration is off
 * in the phone's own settings.
 */
@Composable
fun rememberHaptics(): (Tap) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { tap: Tap ->
            if (Prefs.haptics) {
                val code = when (tap) {
                    Tap.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    Tap.Click -> HapticFeedbackConstants.VIRTUAL_KEY
                    Tap.Heavy -> HapticFeedbackConstants.LONG_PRESS
                    Tap.Confirm ->
                        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                        else HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(code)
            }
        }
    }
}
