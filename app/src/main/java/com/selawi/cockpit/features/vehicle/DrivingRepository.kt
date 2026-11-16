package com.selawi.cockpit.features.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import com.selawi.cockpit.core.platform.hasLocationPermission
import com.selawi.cockpit.core.platform.hasPreciseLocation
import android.location.GnssStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Distance, moving time and speeds since the trip started. */
data class Trip(
    val distanceKm: Double = 0.0,
    val movingSeconds: Long = 0,
    val maxKmh: Int = 0,
    /** Times the car came to a halt (traffic lights included). */
    val stops: Int = 0,
) {
    val averageKmh: Int get() = if (movingSeconds < 30) 0 else (distanceKm / (movingSeconds / 3600.0)).roundToInt()
}

/** A flight's numbers, from GPS: counted while the plane is in the air. */
data class FlightStats(
    val distanceKm: Double = 0.0,
    val airSeconds: Long = 0,
    val maxKmh: Int = 0,
    /** Highest GPS altitude above sea level, in metres. */
    val maxAltitudeM: Int = 0,
)

data class DrivingState(
    val speedKmh: Int = 0,
    val driving: Boolean = false,
    /** Direction of travel in degrees from north, or null before the car has moved. */
    val headingDeg: Float? = null,
    val trip: Trip = Trip(),
    val motion: MotionState = MotionState(),
    /** Sideways acceleration in m/s² from speed and the change in heading; positive turning right. */
    val lateral: Float = 0f,
    /** Which way the car is turning, as a stand-in for its indicators (which apps cannot read). */
    val signal: Turn = Turn.None,
    /** GPS altitude above sea level in metres, when the GPS gives one. */
    val altitudeM: Int? = null,
    /** Climb (positive) or descent in metres a second, smoothed. */
    val climbMps: Float = 0f,
    /** The speed is a plane's (see [DrivingRepository]); Auto mode then shows the plane. */
    val planeSpeed: Boolean = false,
    /** Off the ground: fast, or well above the height where the plane last rolled slowly. */
    val airborne: Boolean = false,
    val flight: FlightStats = FlightStats(),
    /** Plane on the dashboard: [planeSpeed] in Auto mode, or chosen. Set by the screen. */
    val flying: Boolean = false,
    val gps: GpsInfo = GpsInfo(),
)

enum class Turn { None, Left, Right }

/** Why there is no position yet, so the screen can say what to do. */
enum class GpsState { Fixed, Searching, LocationOff, ApproximateOnly, NoPermission }

/** The GPS as the phone or car reports it: its state and the satellites it can see and is using. */
data class GpsInfo(val state: GpsState = GpsState.Fixed, val seen: Int = 0, val used: Int = 0)

/** Indicator blink: about 90 a minute, shared so the arrows and the car blink together. */
fun blinkOn(now: Long = System.currentTimeMillis()) = now % 667 < 370

/**
 * Speed, heading and trip from the car's GPS. Android gives apps no access to the car's own speed
 * signal, so this is the documented way to know how the car is moving. A new trip starts after the
 * car has been parked for two hours.
 */
class DrivingRepository(private val context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state

    val motion = MotionDetector(context)

    init {
        // Braking and acceleration join the driving state as they happen, between GPS fixes too.
        scope.launch { motion.state.collect { m -> _state.value = _state.value.copy(motion = m) } }
    }

    private var listening = false
    private var slowSince = 0L
    private var lastFix: Location? = null
    private var lastFixAt = 0L
    private var lastMovingAt = 0L
    private var lateral = 0f
    private var signal = Turn.None
    private var movingMs = 0L
    private var movedSinceStop = false
    private var refHeading: Float? = null
    private var refHeadingAt = 0L
    private var turnRate = 0f
    private var signalUntil = 0L
    private var planeSpeed = false
    private var fastSince = 0L
    private var crawlSince = 0L
    private var airborne = false
    private var groundAltitude: Double? = null
    private var refAltitude: Double? = null
    private var refAltitudeAt = 0L
    private var climb = 0f
    private var verticalSince = 0L
    private var levelSince = 0L
    private var airMs = 0L
    private var landedAt = 0L
    private var flight = FlightStats()

    // All methods overridden: before Android 11 they have no default implementations.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = update(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private var satellitesSeen = 0
    private var satellitesUsed = 0
    private var lastFixElapsed = 0L

    /** Satellites in view and in use, about once a second, so a missing fix can be explained. */
    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satellitesSeen = status.satelliteCount
            satellitesUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
            publishGps()
        }

        override fun onStopped() = publishGps()
    }

    /** What the GPS is doing now: no permission, approximate only, switched off, searching or fixed. */
    private fun gpsInfo(): GpsInfo {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val state = when {
            !context.hasLocationPermission() -> GpsState.NoPermission
            !context.hasPreciseLocation() -> GpsState.ApproximateOnly
            runCatching { !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> GpsState.LocationOff
            lastFixElapsed != 0L && SystemClock.elapsedRealtime() - lastFixElapsed < 5_000 -> GpsState.Fixed
            else -> GpsState.Searching
        }
        return GpsInfo(state, satellitesSeen, satellitesUsed)
    }

    private fun publishGps() {
        val info = gpsInfo()
        if (info != _state.value.gps) _state.value = _state.value.copy(gps = info)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        motion.start()
        publishGps()
        if (listening || !context.hasLocationPermission()) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listening = runCatching {
            // Ask for a fix every 0.2 s; the GPS sends as often as it can (many only once a second).
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200L, 0f, listener)
        }.isSuccess
        runCatching { lm.registerGnssStatusCallback(gnss, android.os.Handler(android.os.Looper.getMainLooper())) }
    }

    fun stop() {
        motion.stop()
        if (!listening) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(listener)
        runCatching { lm.unregisterGnssStatusCallback(gnss) }
        listening = false
        lastFix = null
        refAltitude = null
        lateral = 0f
        signal = Turn.None
        _state.value = _state.value.copy(speedKmh = 0, driving = false, motion = MotionState(), lateral = 0f, signal = Turn.None)
    }

    private fun update(location: Location) {
        val now = SystemClock.elapsedRealtime()
        lastFixElapsed = now
        val mps = if (location.hasSpeed()) location.speed else 0f
        motion.onGps(mps, location.accuracy)
        val kmh = (mps * 3.6f).roundToInt()
        val current = _state.value

        // Start driving above 8 km/h; stop only after 5 s below 3 km/h, so traffic lights do not flicker it.
        val driving = when {
            kmh >= 8 -> { slowSince = 0; true }
            kmh < 3 && current.driving -> {
                if (slowSince == 0L) slowSince = now
                now - slowSince < 5000
            }
            else -> current.driving
        }

        val altitude = if (location.hasAltitude()) location.altitude else null
        updateFlight(now, kmh, altitude)
        val inPlane = planeSpeed || airborne

        var trip = current.trip
        if (lastMovingAt != 0L && now - lastMovingAt > NEW_TRIP_AFTER_MS) {
            trip = Trip()
            movingMs = 0L
        }
        val previous = lastFix
        // A plane's speed covers hundreds of metres between fixes; those go to the flight instead.
        if (previous != null && inPlane && location.accuracy < 60f) {
            val metres = previous.distanceTo(location)
            val elapsed = (now - lastFixAt).coerceIn(0L, 5_000L)
            if (airborne) {
                airMs += elapsed
                // Allow what the speed explains, so a GPS jump does not add distance.
                if (metres < mps * elapsed / 1000f * 1.5f + 60f) flight = flight.copy(distanceKm = flight.distanceKm + metres / 1000.0)
                flight = flight.copy(airSeconds = airMs / 1000)
            }
        } else if (previous != null && kmh >= 3 && location.accuracy < 40f) {
            val metres = previous.distanceTo(location)
            // Whole milliseconds, so fixes that arrive a little under a second apart still count.
            val elapsed = (now - lastFixAt).coerceIn(0L, 5_000L)
            // Ignore GPS jumps that would need an impossible speed.
            if (metres < 120f) {
                movingMs += elapsed
                trip = trip.copy(distanceKm = trip.distanceKm + metres / 1000.0, movingSeconds = movingMs / 1000)
            }
        }
        // A stop: the car slows below 3 km/h after having driven at 15 km/h or more since the last one.
        if (kmh >= 15 && !inPlane) movedSinceStop = true
        if (kmh < 3 && movedSinceStop) {
            movedSinceStop = false
            trip = trip.copy(stops = trip.stops + 1)
        }
        if (kmh >= 3) lastMovingAt = now
        if (!inPlane && kmh > trip.maxKmh && location.accuracy < 30f) trip = trip.copy(maxKmh = kmh)
        if (airborne) {
            if (kmh > flight.maxKmh && location.accuracy < 60f) flight = flight.copy(maxKmh = kmh)
            if (altitude != null && altitude > flight.maxAltitudeM) flight = flight.copy(maxAltitudeM = altitude.roundToInt())
        }
        lastFix = location
        lastFixAt = now

        val heading = if (location.hasBearing() && kmh >= 5) location.bearing else current.headingDeg
        // Turn rate over at least 0.9 s, however often the GPS reports, so small jitter between
        // quick fixes does not count as turning.
        val ref = refHeading
        if (heading == null || kmh < 5) {
            refHeading = null
            turnRate = 0f
        } else if (ref == null) {
            refHeading = heading
            refHeadingAt = now
        } else if (now - refHeadingAt >= 900) {
            val dt = (now - refHeadingAt) / 1000f
            turnRate = if (dt <= 3f) ((((heading - ref + 540f) % 360f) - 180f) / dt) else 0f
            refHeading = heading
            refHeadingAt = now
        }
        // Cornering force: speed times how fast the heading turns (v·ω), smoothed.
        val turn = mps * Math.toRadians(turnRate.toDouble()).toFloat()
        lateral = lateral * 0.5f + turn.coerceIn(-12f, 12f) * 0.5f
        // Turning at 7° a second or more counts as a corner; the "indicator" stays on a moment
        // after the corner, as a real one does until the wheel straightens.
        if (kmh >= 8 && !inPlane && kotlin.math.abs(turnRate) >= 7f) {
            signal = if (turnRate > 0) Turn.Right else Turn.Left
            signalUntil = now + 1_500
        } else if (now > signalUntil || !driving || inPlane) {
            signal = Turn.None
        }
        val shownLateral = (lateral / 0.2f).roundToInt() * 0.2f
        _state.value = DrivingState(
            kmh, driving, heading, trip, _state.value.motion, shownLateral, signal,
            altitudeM = altitude?.roundToInt(),
            climbMps = (climb * 2f).roundToInt() / 2f,
            planeSpeed = planeSpeed,
            airborne = airborne,
            flight = flight,
            flying = _state.value.flying,
            gps = gpsInfo(),
        )
    }

    /**
     * Plane or car, and ground or air.
     *
     * Plane or car: a plane's speed (250 km/h for three seconds) is something no car on the road
     * reaches. It stays a plane until three minutes below 60 km/h, so landing, rolling out and
     * taxiing to the gate keep the plane.
     *
     * Ground or air: GPS altitude is above sea level, not above the ground (a car in the hills
     * reads higher than a plane on many runways), so the height itself cannot tell. How fast it
     * changes can: in the air a plane climbs or descends steadily, on the runway it holds level.
     * - In the air: above 300 km/h; or above 150 km/h while climbing or descending faster than
     *   2.5 m/s for ten seconds; or 60 m above the height the plane last rolled slowly at.
     * - On the ground: below 120 km/h; or below 200 km/h with the height level for five seconds
     *   (the touchdown). This works whatever the airports' heights.
     */
    private fun updateFlight(now: Long, kmh: Int, altitude: Double?) {
        if (kmh >= 250) {
            crawlSince = 0L
            if (fastSince == 0L) fastSince = now
            if (now - fastSince >= 3_000) planeSpeed = true
        } else {
            fastSince = 0L
            if (planeSpeed && kmh < 60) {
                if (crawlSince == 0L) crawlSince = now
                if (now - crawlSince >= 180_000) planeSpeed = false
            } else {
                crawlSince = 0L
            }
        }
        // Climb rate over at least two seconds, smoothed, so GPS height jitter does not show.
        if (altitude != null) {
            val ref = refAltitude
            if (ref == null) {
                refAltitude = altitude
                refAltitudeAt = now
            } else if (now - refAltitudeAt >= 2_000) {
                val rate = ((altitude - ref) / ((now - refAltitudeAt) / 1000.0)).toFloat()
                climb = climb * 0.6f + rate.coerceIn(-60f, 60f) * 0.4f
                refAltitude = altitude
                refAltitudeAt = now
            }
        }
        val vertical = kotlin.math.abs(climb)
        if (altitude != null && vertical > 2.5f) { if (verticalSince == 0L) verticalSince = now } else verticalSince = 0L
        if (altitude != null && vertical < 1.2f) { if (levelSince == 0L) levelSince = now } else levelSince = 0L
        val steadyVertical = verticalSince != 0L && now - verticalSince >= 10_000
        val level = levelSince != 0L && now - levelSince >= 5_000

        if (altitude != null && kmh < 100 && !airborne) {
            groundAltitude = groundAltitude?.let { it * 0.8 + altitude * 0.2 } ?: altitude
        }
        val aboveGround = if (altitude != null) groundAltitude?.let { altitude - it } else null
        val wasAirborne = airborne
        airborne = when {
            kmh >= 300 -> true
            kmh < 120 -> false
            kmh >= 150 && steadyVertical -> true
            kmh < 200 && level -> false
            kmh >= 200 && aboveGround != null && aboveGround > 60 -> true
            else -> airborne
        }
        if (airborne && !wasAirborne) {
            // A new flight once the plane has been on the ground for an hour.
            if (landedAt == 0L || now - landedAt > NEW_FLIGHT_AFTER_MS) {
                flight = FlightStats()
                airMs = 0L
            }
        }
        if (!airborne && wasAirborne) landedAt = now
    }

    private companion object {
        const val NEW_TRIP_AFTER_MS = 2 * 60 * 60 * 1000L
        const val NEW_FLIGHT_AFTER_MS = 60 * 60 * 1000L
    }
}
