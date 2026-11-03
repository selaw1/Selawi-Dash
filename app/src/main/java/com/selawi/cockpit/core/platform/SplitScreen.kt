package com.selawi.cockpit.core.platform

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Puts another app beside the cockpit, the way Android Auto shows a map next to the player.
 *
 * When the cockpit is already in split screen, Android's documented launch-adjacent flag places the
 * app in the other half. From full screen, Android 12 offers apps no documented way in; the cockpit
 * then asks for split screen through ActivityOptions.setLaunchWindowingMode, a hidden Android method.
 * If the car refuses it, the app simply opens full screen as before.
 */
object SplitScreen {
    private const val WINDOWING_MODE_SPLIT_SCREEN_PRIMARY = 3

    /** Returns true when the app was asked to open beside the cockpit. */
    fun openBeside(activity: Activity, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (activity.isInMultiWindowMode) {
            return runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)) }.isSuccess
        }
        val options = runCatching {
            ActivityOptions.makeBasic().also {
                ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .invoke(it, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY)
            }
        }.getOrNull()
        if (options == null || runCatching { activity.startActivity(intent, options.toBundle()) }.isFailure) {
            activity.tryStart(intent)
            return false
        }
        // The other app took one half; bring the cockpit back into the other half.
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                activity.startActivity(
                    Intent(activity, activity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
                )
            }
        }, 700)
        return true
    }
}
