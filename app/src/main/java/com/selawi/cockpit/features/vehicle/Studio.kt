package com.selawi.cockpit.features.vehicle

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Small procedural textures for the car scene, so no image files are needed. */
internal object Studio {
    private const val FACE = 32

    /**
     * A studio-like cube map: bright overhead softbox, light grey walls, dark floor.
     * Faces in Filament order: +X, -X, +Y, -Y, +Z, -Z.
     */
    fun cubemap(engine: Engine): Texture {
        val texture = Texture.Builder()
            .width(FACE)
            .height(FACE)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.RGB16F)
            .build(engine)
        val faceBytes = FACE * FACE * 3 * 4
        val data = ByteBuffer.allocateDirect(faceBytes * 6).order(ByteOrder.nativeOrder())
        for (face in 0 until 6) {
            for (y in 0 until FACE) {
                for (x in 0 until FACE) {
                    val u = (x + 0.5f) / FACE * 2f - 1f
                    val v = (y + 0.5f) / FACE * 2f - 1f
                    val (dx, dy, dz) = direction(face, u, v)
                    val c = studio(dx, dy, dz)
                    data.putFloat(c[0]); data.putFloat(c[1]); data.putFloat(c[2])
                }
            }
        }
        data.flip()
        val offsets = IntArray(6) { it * faceBytes }
        texture.setImage(
            engine,
            0,
            Texture.PixelBufferDescriptor(data, Texture.Format.RGB, Texture.Type.FLOAT),
            offsets,
        )
        return texture
    }

    /** A 1x1 colour, used to replace the tyre sidewall lettering. */
    fun solid(engine: Engine, r: Float, g: Float, b: Float): Texture {
        val texture = Texture.Builder()
            .width(1)
            .height(1)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .build(engine)
        val data = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        data.put((r * 255).toInt().toByte()).put((g * 255).toInt().toByte()).put((b * 255).toInt().toByte()).put(255.toByte())
        data.flip()
        texture.setImage(engine, 0, Texture.PixelBufferDescriptor(data, Texture.Format.RGBA, Texture.Type.UBYTE))
        return texture
    }

    private fun direction(face: Int, u: Float, v: Float): Triple<Float, Float, Float> {
        val d = when (face) {
            0 -> Triple(1f, -v, -u)
            1 -> Triple(-1f, -v, u)
            2 -> Triple(u, 1f, v)
            3 -> Triple(u, -1f, -v)
            4 -> Triple(u, -v, 1f)
            else -> Triple(-u, -v, -1f)
        }
        val len = sqrt(d.first * d.first + d.second * d.second + d.third * d.third)
        return Triple(d.first / len, d.second / len, d.third / len)
    }

    private fun studio(x: Float, y: Float, z: Float): FloatArray {
        // Floor dark, walls a soft grey that brightens towards the ceiling.
        var level = if (y < 0f) 0.08f + 0.12f * (1f + y) else 0.35f + 0.65f * y
        // Long overhead softbox for the classic car-studio highlight along the body.
        if (y > 0.55f && abs(x) < 0.35f) level += 3.5f
        // Two side panels for highlights on the doors.
        if (abs(y) < 0.35f && abs(z) < 0.5f && abs(x) > 0.8f) level += 1.2f
        val cool = max(0f, y) * 0.06f
        return floatArrayOf(level, level + cool * 0.5f, level + cool)
    }
}
