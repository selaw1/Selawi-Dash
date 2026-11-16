package com.selawi.cockpit

import com.selawi.cockpit.data.local.FlightSource
import com.selawi.cockpit.features.vehicle.AdsbParser
import com.selawi.cockpit.features.vehicle.FlightDataLevel
import com.selawi.cockpit.features.vehicle.FlightDataStatus
import com.selawi.cockpit.features.vehicle.FlightNumbers
import com.selawi.cockpit.features.vehicle.GpsInfo
import com.selawi.cockpit.features.vehicle.GpsState
import com.selawi.cockpit.features.vehicle.OnlineState
import com.selawi.cockpit.features.vehicle.OnlineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightDataTest {
    @Test
    fun flightNumbersBecomeCallsigns() {
        assertEquals("RJA183", FlightNumbers.callsign("RJ183"))
        assertEquals("RJA183", FlightNumbers.callsign("rj 183"))
        assertEquals("RJA12", FlightNumbers.callsign("RJ0012"))
        assertEquals("UAE201", FlightNumbers.callsign("EK201"))
        assertEquals("RJA183", FlightNumbers.callsign("RJA183"))
        assertEquals("BAW12AB", FlightNumbers.callsign("BAW12AB"))
        assertNull(FlightNumbers.callsign("hello"))
        assertNull(FlightNumbers.callsign("ZZ123"))
    }

    @Test
    fun readsTheServicesFormat() {
        val json = """{"ac":[{"hex":"740abc","flight":"RJA183  ","alt_baro":36000,"alt_geom":36475,"gs":470.0,"track":284.2,
            "geom_rate":-64,"lat":31.95,"lon":35.91,"seen_pos":0.8,"seen":0.2}],"msg":"No error","now":1,"total":1}"""
        val fix = AdsbParser.parse(json, "RJA183")
        assertNotNull(fix)
        fix!!
        assertEquals("RJA183", fix.callsign)
        assertEquals(11_118.0, fix.altitudeM!!, 1.0)
        assertEquals(870.4f, fix.speedMps * 3.6f, 0.5f)
        assertEquals(284.2f, fix.trackDeg!!, 0.01f)
        assertEquals(false, fix.onGround)
        val ground = AdsbParser.parse("""{"ac":[{"flight":"RJA183","alt_baro":"ground","gs":12,"lat":31.7,"lon":35.99}]}""", "RJA183")!!
        assertTrue(ground.onGround)
        assertNull(ground.altitudeM)
        assertNull(AdsbParser.parse("""{"ac":[],"total":0}""", "RJA183"))
    }

    @Test
    fun explainsWhatIsHappening() {
        val noSky = GpsInfo(GpsState.Searching, 0, 0)
        val auto = FlightDataStatus.describe(FlightSource.Auto, noSky, OnlineStatus(OnlineState.NoInternet, "RJA183"), "RJ183", 0)
        assertEquals("No position yet", auto.headline)
        assertEquals(FlightDataLevel.Problem, auto.level)
        assertTrue(auto.lines[0].startsWith("GPS: no satellites in view"))
        assertTrue(auto.lines[1].startsWith("Online: no internet"))

        val gps = FlightDataStatus.describe(FlightSource.Auto, GpsInfo(GpsState.Fixed, seen = 14, used = 9), OnlineStatus(), "", 0)
        assertEquals("Using GPS", gps.headline)
        assertEquals("9 of 14 satellites in use.", gps.lines[0])

        val number = FlightDataStatus.describe(FlightSource.Online, noSky, OnlineStatus(OnlineState.NoFlightNumber), "", 0)
        assertTrue(number.lines.single().startsWith("Online: enter your flight number"))
    }
}
