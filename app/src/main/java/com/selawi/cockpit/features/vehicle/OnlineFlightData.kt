package com.selawi.cockpit.features.vehicle

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.selawi.cockpit.data.local.FlightSource
import com.selawi.cockpit.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A plane's position from the internet, for when the phone's GPS cannot see the sky (in a cabin,
 * behind dimmable windows). Every airliner broadcasts its own GPS position, altitude, speed and
 * heading by radio (ADS-B); volunteers' receivers collect it, and adsb.lol (open data, ODbL) and
 * adsb.fi share it free. The flight is found by its callsign, made from the flight number
 * the user types (RJ183 → RJA183). Needs internet on board; the data is a few seconds behind the
 * plane and can pause where there are no receivers, often over the sea.
 */
data class OnlineFix(
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    /** GPS (geometric) altitude in metres, or the pressure altitude when that is all there is. */
    val altitudeM: Double?,
    val speedMps: Float,
    val trackDeg: Float?,
    val onGround: Boolean,
    /** Seconds since the position was received. */
    val ageSeconds: Float,
)

enum class OnlineState {
    /** Not needed: GPS is working, or the method is GPS only. */
    Standby,
    NoFlightNumber,
    BadFlightNumber,
    NoInternet,
    /** On a Wi-Fi without internet yet, as before logging in or paying on the plane's Wi-Fi page. */
    NoInternetYet,
    Searching,
    Live,
    NotFound,
    /** Found, but the last position is old: no receivers nearby. */
    Stale,
    Unreachable,
}

data class OnlineStatus(
    val state: OnlineState = OnlineState.Standby,
    val callsign: String? = null,
    val provider: String? = null,
    val fix: OnlineFix? = null,
    /** SystemClock.elapsedRealtime() of the last answer. */
    val updatedAt: Long = 0,
    /** What each service answered on the last try, in plain words, for the settings. */
    val results: List<String> = emptyList(),
)

/** One service's answer: the body, or why there is none, in plain words. */
private sealed interface Answer {
    data class Body(val text: String) : Answer
    data class Failed(val reason: String) : Answer
}

/** Flight number to the callsign planes broadcast: RJ183 → RJA183. ICAO callsigns pass through. */
object FlightNumbers {
    private val icao = mapOf(
        "RJ" to "RJA", "EK" to "UAE", "QR" to "QTR", "EY" to "ETD", "TK" to "THY", "LH" to "DLH", "BA" to "BAW",
        "AF" to "AFR", "KL" to "KLM", "SV" to "SVA", "MS" to "MSR", "FZ" to "FDB", "G9" to "ABY", "XY" to "KNE",
        "WY" to "OMA", "GF" to "GFA", "KU" to "KAC", "ME" to "MEA", "J9" to "JZR", "PC" to "PGT", "W6" to "WZZ",
        "FR" to "RYR", "U2" to "EZY", "LX" to "SWR", "OS" to "AUA", "AZ" to "ITY", "IB" to "IBE", "UA" to "UAL",
        "AA" to "AAL", "DL" to "DAL", "AC" to "ACA", "SQ" to "SIA", "CX" to "CPA", "QF" to "QFA", "NH" to "ANA",
        "JL" to "JAL", "ET" to "ETH", "A3" to "AEE", "LO" to "LOT", "SK" to "SAS", "AY" to "FIN", "VS" to "VIR",
        "TP" to "TAP", "SN" to "BEL", "EI" to "EIN", "AI" to "AIC", "6E" to "IGO", "PK" to "PIA", "UL" to "ALK",
    )

    /** The callsign for [input], or null when it does not look like a flight number. */
    fun callsign(input: String): String? {
        val s = input.uppercase().filter { it.isLetterOrDigit() }
        if (Regex("^[A-Z]{3}[0-9]{1,4}[A-Z]{0,2}$").matches(s)) return s
        val m = Regex("^([A-Z0-9]{2})0*([0-9]{1,4}[A-Z]?)$").matchEntire(s) ?: return null
        val airline = icao[m.groupValues[1]] ?: return null
        return airline + m.groupValues[2]
    }
}

/** Reads the "ac" list both services return (the readsb format). Feet and knots become metres. */
object AdsbParser {
    fun parse(json: String, callsign: String): OnlineFix? {
        val list = JSONObject(json).optJSONArray("ac") ?: return null
        for (i in 0 until list.length()) {
            val a = list.getJSONObject(i)
            if (!a.has("lat") || !a.has("lon")) continue
            val baro = a.opt("alt_baro")
            val onGround = baro == "ground"
            val feet = when {
                a.has("alt_geom") -> a.optDouble("alt_geom")
                baro is Number -> baro.toDouble()
                else -> Double.NaN
            }
            return OnlineFix(
                callsign = a.optString("flight", callsign).trim().ifEmpty { callsign },
                latitude = a.getDouble("lat"),
                longitude = a.getDouble("lon"),
                altitudeM = if (feet.isNaN()) null else feet * 0.3048,
                speedMps = (a.optDouble("gs", 0.0) * 0.514444).toFloat(),
                trackDeg = if (a.has("track")) a.getDouble("track").toFloat() else null,
                onGround = onGround,
                ageSeconds = a.optDouble("seen_pos", a.optDouble("seen", 0.0)).toFloat(),
            )
        }
        return null
    }
}

/**
 * Fetches the flight's position every five seconds when it is wanted: always in Online mode, and
 * in Automatic mode once the GPS has had no fix for 15 seconds. It says at every step what is
 * happening, so the settings can explain it.
 */
class OnlineFlightData(
    private val context: Context,
    scope: CoroutineScope,
    private val driving: DrivingRepository,
    private val settings: SettingsStore,
) {
    private val _status = MutableStateFlow(OnlineStatus())
    val status: StateFlow<OnlineStatus> = _status

    init {
        scope.launch { _status.collect { driving.setOnline(it) } }
    }

    init {
        scope.launch {
            while (true) {
                runCatching { step() }
                delay(5_000)
            }
        }
    }

    private suspend fun step() {
        val source = settings.flightSource.first()
        driving.source = source
        val wanted = when (source) {
            FlightSource.Gps -> false
            FlightSource.Online -> true
            FlightSource.Auto -> driving.gpsQuietMs() >= 15_000
        }
        if (!wanted) {
            _status.value = OnlineStatus(OnlineState.Standby)
            return
        }
        val number = settings.flightNumber.first()
        if (number.isBlank()) {
            _status.value = OnlineStatus(OnlineState.NoFlightNumber)
            return
        }
        val callsign = FlightNumbers.callsign(number)
        if (callsign == null) {
            _status.value = OnlineStatus(OnlineState.BadFlightNumber)
            return
        }
        when (internet()) {
            Internet.None -> { _status.value = OnlineStatus(OnlineState.NoInternet, callsign); return }
            Internet.NotYet -> { _status.value = OnlineStatus(OnlineState.NoInternetYet, callsign); return }
            Internet.Yes -> Unit
        }
        if (_status.value.state != OnlineState.Live) _status.value = _status.value.copy(state = OnlineState.Searching, callsign = callsign)
        // Ask each service in turn and keep what each one answered, so a failure is never vague.
        val results = mutableListOf<String>()
        var reached = false
        var stale: OnlineFix? = null
        var staleFrom: String? = null
        for ((name, base) in PROVIDERS) {
            when (val answer = withContext(Dispatchers.IO) { fetch(base + callsign) }) {
                is Answer.Failed -> results += "$name: ${answer.reason}"
                is Answer.Body -> {
                    reached = true
                    val fix = runCatching { AdsbParser.parse(answer.text, callsign) }
                    val now = SystemClock.elapsedRealtime()
                    when {
                        fix.isFailure -> results += "$name: answered, but not with flight data."
                        fix.getOrNull() == null -> results += "$name: $callsign isn't in its data right now."
                        fix.getOrNull()!!.ageSeconds > 60 -> {
                            results += "$name: last saw $callsign ${fix.getOrNull()!!.ageSeconds.toInt()} s ago."
                            stale = fix.getOrNull(); staleFrom = name
                        }
                        else -> {
                            val found = fix.getOrNull()!!
                            _status.value = OnlineStatus(OnlineState.Live, callsign, name, found, now, results + "$name: found $callsign.")
                            driving.onlineFix(found)
                            return
                        }
                    }
                }
            }
        }
        val now = SystemClock.elapsedRealtime()
        _status.value = when {
            stale != null -> OnlineStatus(OnlineState.Stale, callsign, staleFrom, stale, now, results)
            reached -> OnlineStatus(OnlineState.NotFound, callsign, null, null, now, results)
            else -> OnlineStatus(OnlineState.Unreachable, callsign, null, null, now, results)
        }
    }

    private enum class Internet { None, NotYet, Yes }

    private fun internet(): Internet {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return Internet.None
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Internet.None
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) Internet.Yes else Internet.NotYet
    }

    /** One request, turned into the body or a plain reason why not. */
    private fun fetch(url: String): Answer = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("User-Agent", "SelawiDash (open-source dashboard app)")
        c.setRequestProperty("Accept", "application/json")
        try {
            when (val code = c.responseCode) {
                200 -> Answer.Body(c.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> Answer.Failed("refused the app (HTTP $code).")
                404 -> Answer.Failed("doesn't have this address any more (HTTP 404).")
                429 -> Answer.Failed("too many requests; it asks to slow down (HTTP 429).")
                in 500..599 -> Answer.Failed("its server has a problem (HTTP $code).")
                else -> Answer.Failed("answered HTTP $code.")
            }
        } finally {
            c.disconnect()
        }
    } catch (e: java.net.SocketTimeoutException) {
        Answer.Failed("no answer within 6 seconds.")
    } catch (e: java.net.UnknownHostException) {
        Answer.Failed("couldn't look up its address; the internet may not be working.")
    } catch (e: javax.net.ssl.SSLException) {
        Answer.Failed("the secure connection failed; the Wi-Fi may be blocking it.")
    } catch (e: java.io.IOException) {
        Answer.Failed("connection failed (${e.javaClass.simpleName}).")
    }

    private companion object {
        /** adsb.lol first (open data, ODbL), then adsb.fi; both free, no key. */
        val PROVIDERS = listOf(
            "adsb.lol" to "https://api.adsb.lol/v2/callsign/",
            "adsb.fi" to "https://opendata.adsb.fi/api/v2/callsign/",
        )
    }
}
