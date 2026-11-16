package com.selawi.cockpit.features.vehicle

import com.selawi.cockpit.data.local.FlightSource
import kotlin.math.roundToInt

/** How things stand, for the settings: working, waiting, or something the user can fix. */
enum class FlightDataLevel { Working, Waiting, Problem }

/** A headline and the reasons behind it, in plain words. */
data class FlightDataExplanation(val level: FlightDataLevel, val headline: String, val lines: List<String>)

/**
 * Puts the flight data method, the GPS and the online data together into what is happening right
 * now: which source is in use, and if neither gives a position, why and what to do about it.
 */
object FlightDataStatus {
    fun describe(source: FlightSource, gps: GpsInfo, online: OnlineStatus, flightNumber: String, nowElapsed: Long): FlightDataExplanation {
        val gpsFixed = gps.state == GpsState.Fixed
        val fix = online.fix
        if (source != FlightSource.Gps && online.state == OnlineState.Live && fix != null && (source == FlightSource.Online || !gpsFixed)) {
            val age = ((nowElapsed - online.updatedAt) / 1000f + fix.ageSeconds).roundToInt().coerceAtLeast(0)
            // Speed, altitude and the rest show on the dashboard, under the plane.
            val lines = mutableListOf("${online.callsign} from ${online.provider}, updated $age s ago.")
            if (source == FlightSource.Auto) lines += "The GPS has no fix, so Automatic switched to online flight data. It switches back as soon as the GPS finds satellites."
            return FlightDataExplanation(FlightDataLevel.Working, "Using online flight data", lines)
        }
        if (source != FlightSource.Online && gpsFixed) {
            val lines = mutableListOf("${gps.used} of ${gps.seen} satellites in use.")
            if (source == FlightSource.Auto) {
                lines += "If the GPS loses its fix for 15 seconds, Automatic switches to online flight data (it needs your flight number and internet)."
            } else {
                lines += "Online flight data is off: GPS only."
            }
            return FlightDataExplanation(FlightDataLevel.Working, "Using GPS", lines)
        }
        val lines = mutableListOf<String>()
        var problem = false
        if (source != FlightSource.Online) {
            lines += when (gps.state) {
                GpsState.NoPermission -> "GPS: location permission is off. Allow location for Selawi Dash in the phone's settings.".also { problem = true }
                GpsState.ApproximateOnly -> "GPS: only approximate location is allowed. Turn on precise location for Selawi Dash.".also { problem = true }
                GpsState.LocationOff -> "GPS: the phone's location is switched off.".also { problem = true }
                else -> if (gps.seen == 0) {
                    "GPS: no satellites in view."
                } else {
                    "GPS: ${gps.used} of ${gps.seen} satellites in use; it needs about 4 for a fix. Keep the phone by a window."
                }
            }
        }
        if (source == FlightSource.Gps) {
            lines += "Online flight data is off: GPS only."
        } else {
            val cs = online.callsign ?: FlightNumbers.callsign(flightNumber) ?: flightNumber
            lines += when (online.state) {
                OnlineState.Standby -> "Online: starts if the GPS still has no fix after 15 seconds."
                OnlineState.NoFlightNumber -> "Online: enter your flight number below to use online flight data.".also { problem = true }
                OnlineState.BadFlightNumber -> "Online: \"$flightNumber\" doesn't look like a flight number. Use the airline code and number, like RJ183, or the callsign, like RJA183.".also { problem = true }
                OnlineState.NoInternet -> "Online: no internet. Connect to the plane's Wi-Fi and buy internet; messaging-only packages usually block it.".also { problem = true }
                OnlineState.NoInternetYet -> "Online: the Wi-Fi has no internet access yet. Log in or buy internet on the Wi-Fi page.".also { problem = true }
                OnlineState.Searching -> "Online: looking up $cs…"
                OnlineState.NotFound -> "Online: $cs isn't in the online data right now. Check the number; over the sea there are often no receivers."
                OnlineState.Stale -> "Online: $cs was last seen ${(online.fix?.ageSeconds ?: 0f).roundToInt()} s ago. No receivers are near the plane right now (often over the sea); it continues when they are."
                OnlineState.Unreachable -> "Online: couldn't reach adsb.lol or airplanes.live. The internet may be too slow or blocked."
                OnlineState.Live -> "Online: $cs found."
            }
        }
        return FlightDataExplanation(if (problem) FlightDataLevel.Problem else FlightDataLevel.Waiting, "No position yet", lines)
    }
}
