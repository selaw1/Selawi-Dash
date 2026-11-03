package com.selawi.cockpit

import android.app.Application
import com.selawi.cockpit.core.platform.CrashLog
import com.selawi.cockpit.data.local.SettingsStore
import com.selawi.cockpit.features.apps.AppsRepository
import com.selawi.cockpit.features.calls.CallRepository
import com.selawi.cockpit.features.media.MediaRepository
import com.selawi.cockpit.features.vehicle.DrivingRepository
import com.selawi.cockpit.features.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app's long-lived objects. Manual wiring; no DI framework needed at this size. */
class CockpitApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: SettingsStore
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var media: MediaRepository
        private set
    lateinit var weather: WeatherRepository
        private set
    lateinit var driving: DrivingRepository
        private set
    lateinit var calls: CallRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        settings = SettingsStore(this)
        apps = AppsRepository(this, settings, scope)
        media = MediaRepository(this, scope)
        weather = WeatherRepository(this, scope)
        driving = DrivingRepository(this)
        calls = CallRepository(this, scope)
    }
}
