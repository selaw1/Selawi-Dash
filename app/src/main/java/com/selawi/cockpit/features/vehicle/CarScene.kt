package com.selawi.cockpit.features.vehicle

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The car scene's camera, shared by the 3D renderer and the road drawn behind it, so the car
 * sits exactly on the road. World units: the car is about 2 long (about 2.35 m per unit), its
 * wheels touch the ground at y = 0, and it faces +z when turned to 0°.
 */
object CarScene {
    const val V_FOV_DEG = 30.0
    /** Metres per world unit for the bundled car (about 4.7 m long). */
    const val METRES_PER_UNIT = 2.35f

    class View(val eye: DoubleArray, val target: DoubleArray, val aspect: Double)

    /**
     * Parked ([drive] = 0): eye level a little above the bonnet, framing the whole car.
     * Driving ([drive] = 1): behind and to the right, a little higher, looking down the road,
     * so the side and the turning wheels show.
     */
    fun view(aspect: Double, drive: Float): View {
        val k = smooth(drive.coerceIn(0f, 1f)).toDouble()
        val tanV = tan(Math.toRadians(V_FOV_DEG / 2))
        val tanH = tanV * aspect
        val parked = max(1.25 / tanH, 0.62 / tanV) + 1.0
        val dist = parked * (1.0 - 0.06 * k)
        val angle = Math.toRadians(30.0 * k)
        val height = dist * (0.19 + 0.12 * k)
        val eye = doubleArrayOf(dist * sin(angle), height, dist * cos(angle))
        val target = doubleArrayOf(0.0, 0.24 - 0.08 * k, -0.6 * k)
        return View(eye, target, aspect)
    }

    /** Screen position of a world point, or null when it is behind the camera. */
    fun project(v: View, x: Double, y: Double, z: Double, width: Float, height: Float): Offset? {
        val f = normalize(v.target[0] - v.eye[0], v.target[1] - v.eye[1], v.target[2] - v.eye[2])
        val r = normalize(f[1] * 0 - f[2] * 1, f[2] * 0 - f[0] * 0, f[0] * 1 - f[1] * 0) // f × up
        val u = doubleArrayOf(r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
        val px = x - v.eye[0]
        val py = y - v.eye[1]
        val pz = z - v.eye[2]
        val depth = px * f[0] + py * f[1] + pz * f[2]
        if (depth < 0.05) return null
        val tanV = tan(Math.toRadians(V_FOV_DEG / 2))
        val nx = (px * r[0] + py * r[1] + pz * r[2]) / (depth * tanV * v.aspect)
        val ny = (px * u[0] + py * u[1] + pz * u[2]) / (depth * tanV)
        return Offset(((nx + 1) / 2 * width).toFloat(), ((1 - ny) / 2 * height).toFloat())
    }

    fun smooth(t: Float) = t * t * (3 - 2 * t)

    private fun normalize(x: Double, y: Double, z: Double): DoubleArray {
        val l = sqrt(x * x + y * y + z * z)
        return doubleArrayOf(x / l, y / l, z / l)
    }
}
