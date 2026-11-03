package com.selawi.cockpit.features.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Navigation comes from the driver's phone: Google Maps on the phone, shown on the car through
 * DiAuto (Android Auto projection). The cockpit only opens DiAuto; it does not include its code.
 */
object PhoneNavigation {
    private val packages = listOf(
        "com.andrerinas.headunitrevived",
        "com.andrerinas.headunitrevived.bydhudtest",
    )

    fun isInstalled(context: Context): Boolean = launchIntent(context) != null

    private fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        packages.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }?.let { return it }
        // Fall back to any app labelled DiAuto, in case its package name changes.
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .firstOrNull { it.loadLabel(pm).toString().contains("DiAuto", ignoreCase = true) }
            ?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
    }

    /** Opens DiAuto full screen. */
    fun open(context: Context) {
        val intent = launchIntent(context)
        if (intent == null) {
            Toast.makeText(context, "Install DiAuto to show navigation from your phone", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Drive mode: DiAuto beside the cockpit. Android 12 can only place an app next to us when the
     * cockpit is already in split screen; otherwise the driver uses the car's split-screen button.
     */
    fun openBeside(activity: Activity) {
        val intent = launchIntent(activity)
        if (intent == null) {
            Toast.makeText(activity, "Install DiAuto to show navigation from your phone", Toast.LENGTH_LONG).show()
            return
        }
        val started = runCatching {
            activity.startActivity(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
            )
        }.isSuccess
        if (started && !activity.isInMultiWindowMode) {
            Toast.makeText(
                activity,
                "Tap the car's split-screen button to put the cockpit beside the map",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
