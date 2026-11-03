package com.selawi.cockpit.features.calls

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class CallState { Idle, Ringing, Active }

/** [since] is SystemClock.elapsedRealtime() when the current state began. */
data class CallInfo(val state: CallState = CallState.Idle, val since: Long = 0)

/**
 * Whether a phone call is ringing or in progress, from Android's audio mode. Any app may read it,
 * so it works for calls through the car's Bluetooth and through Android Auto alike. Android does
 * not give apps the caller's name or number here; the car's own call screen shows those.
 */
class CallRepository(private val context: Context, scope: CoroutineScope) {
    private val _call = MutableStateFlow(CallInfo())
    val call: StateFlow<CallInfo> = _call

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { audio.addOnModeChangedListener(context.mainExecutor) { update() } }
        }
        // Also check every second: some head units do not report mode changes to listeners.
        scope.launch {
            while (true) {
                update()
                delay(1000)
            }
        }
    }

    private fun update() {
        val state = when (audio.mode) {
            AudioManager.MODE_RINGTONE -> CallState.Ringing
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, MODE_CALL_SCREENING -> CallState.Active
            else -> CallState.Idle
        }
        val current = _call.value
        if (state != current.state) _call.value = CallInfo(state, SystemClock.elapsedRealtime())
    }

    /** Opens the car's phone app (BYD's Bluetooth Phone), or the dialer when it cannot be found. */
    fun openPhone() {
        val pm = context.packageManager
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .firstOrNull { it.loadLabel(pm).toString().contains("Phone", ignoreCase = true) }
        val intent = phone?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) } ?: Intent(Intent.ACTION_DIAL)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private companion object {
        /** AudioManager.MODE_CALL_SCREENING (API 30), spelled out for older SDK levels. */
        const val MODE_CALL_SCREENING = 4
    }
}
