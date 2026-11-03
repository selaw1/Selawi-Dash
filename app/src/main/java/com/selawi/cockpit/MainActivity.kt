package com.selawi.cockpit

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var fullScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CockpitApplication
        setContent { CockpitRoot(this, app) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.settings.fullScreen, app.settings.keepScreenOn) { full, keepOn -> full to keepOn }
                    .collect { (full, keepOn) ->
                        fullScreen = full
                        applySystemBars()
                        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // GPS speed only while the cockpit is on screen.
        (application as CockpitApplication).driving.start()
    }

    override fun onStop() {
        (application as CockpitApplication).driving.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Music apps opened from the cockpit may have changed while we were away.
        (application as CockpitApplication).apps.refresh()
        // Music apps started since last time can now accept a direct connection.
        (application as CockpitApplication).media.connectBrowsers()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        applySystemBars()
    }

    /** Hides BYD's bars when full screen is on. In split screen they stay, so the car stays usable. */
    private fun applySystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullScreen && !isInMultiWindowMode) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
