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
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Storm }

data class HourForecast(val time: LocalTime, val temperature: Int, val sky: Sky, val isDay: Boolean)

data class Weather(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val condition: String,
    val isDay: Boolean,
    val place: String,
    val sky: Sky = Sky.Clear,
    val hours: List<HourForecast> = emptyList(),
    val sunrise: LocalTime? = null,
    val sunset: LocalTime? = null,
)

/**
 * Weather from Open-Meteo for the car's position: free, no key, no Google services. Refreshes
 * every 20 minutes. Without a position there is no forecast; [needsLocation] says so instead of
 * showing the weather for some other place.
 */
class WeatherRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    private val _needsLocation = MutableStateFlow(false)
    /** True while there is no forecast because the car's position is not known yet. */
    val needsLocation: StateFlow<Boolean> = _needsLocation

    init {
        scope.launch {
            while (true) {
                refresh()
                delay(if (_weather.value == null) 60_000 else 20 * 60_000)
            }
        }
    }

    /** Called again when location permission arrives, so the forecast loads straight away. */
    fun refreshNow() {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val location = context.lastKnownLocation()
        if (location == null) {
            // No position yet (no permission or no GPS fix): ask for it rather than guess a place.
            // A forecast already shown stays, since it was for where the car really was.
            if (_weather.value == null) _needsLocation.value = true
            return
        }
        val lat = location.latitude
        val lon = location.longitude
        val place = "Here"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&hourly=temperature_2m,weather_code,is_day&forecast_hours=7" +
                    "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=1&timezone=auto"
                parse(JSONObject(get(url)), place)
            }.getOrNull()
        }
        // Keep the last good reading when the car is offline.
        if (result != null) {
            _weather.value = result
            _needsLocation.value = false
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: JSONObject, place: String): Weather {
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val code = current.getInt("weather_code")
        val hourly = json.optJSONObject("hourly")
        val hours = buildList {
            if (hourly != null) {
                val times = hourly.getJSONArray("time")
                val temps = hourly.getJSONArray("temperature_2m")
                val codes = hourly.getJSONArray("weather_code")
                val days = hourly.optJSONArray("is_day")
                // Skip the current hour; the next six follow.
                for (i in 1 until times.length()) {
                    add(
                        HourForecast(
                            time = LocalDateTime.parse(times.getString(i)).toLocalTime(),
                            temperature = temps.getDouble(i).roundToInt(),
                            sky = skyOf(codes.getInt(i)),
                            isDay = (days?.optInt(i, 1) ?: 1) == 1,
                        ),
                    )
                }
            }
        }
        fun dailyTime(key: String) = runCatching {
            LocalDateTime.parse(daily.getJSONArray(key).getString(0)).toLocalTime()
        }.getOrNull()
        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            condition = describe(code),
            isDay = current.optInt("is_day", 1) == 1,
            place = place,
            sky = skyOf(code),
            hours = hours.take(6),
            sunrise = dailyTime("sunrise"),
            sunset = dailyTime("sunset"),
        )
    }

    private fun skyOf(code: Int) = when (code) {
        0, 1 -> Sky.Clear
        2 -> Sky.PartlyCloudy
        3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        in 51..67, in 80..82 -> Sky.Rain
        in 71..77, 85, 86 -> Sky.Snow
        in 95..99 -> Sky.Storm
        else -> Sky.Cloudy
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
}
