package com.selawi.cockpit.features.weather

import android.content.Context
import com.selawi.cockpit.core.platform.lastKnownLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val condition: String,
    val isDay: Boolean,
    val place: String,
)

/** Current weather from Open-Meteo: free, no key, no Google services. Refreshes every 20 minutes. */
class WeatherRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    init {
        scope.launch {
            while (true) {
                refresh()
                delay(if (_weather.value == null) 60_000 else 20 * 60_000)
            }
        }
    }

    /** Called again when location permission arrives, so the forecast moves from Amman to the car. */
    fun refreshNow() {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val location = context.lastKnownLocation()
        val lat = location?.latitude ?: AMMAN_LAT
        val lon = location?.longitude ?: AMMAN_LON
        val place = if (location != null) "Here" else "Amman"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val body = try {
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                Weather(
                    temperature = current.getDouble("temperature_2m").roundToInt(),
                    high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                    low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                    condition = describe(current.getInt("weather_code")),
                    isDay = current.optInt("is_day", 1) == 1,
                    place = place,
                )
            }.getOrNull()
        }
        // Keep the last good reading when the car is offline.
        if (result != null) _weather.value = result
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }

    private companion object {
        const val AMMAN_LAT = 31.9539
        const val AMMAN_LON = 35.9106
    }
}
