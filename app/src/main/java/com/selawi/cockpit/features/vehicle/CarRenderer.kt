package com.selawi.cockpit.features.vehicle

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min

/**
 * The live 3D car. Only [Car3D] calls this, so screenshot tests never load the native engine.
 * [speedKmh] and [driving] are read every frame.
 */
@Composable
internal fun LiveCar(modifier: Modifier, speedKmh: Int, driving: Boolean) {
    val context = LocalContext.current
    val speed = rememberUpdatedState(speedKmh)
    val isDriving = rememberUpdatedState(driving)
    val renderer = remember { CarRenderer(context) { speed.value to isDriving.value } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(renderer) {
        val bytes = withContext(Dispatchers.IO) { CarModel.read(context) }
        if (bytes != null) renderer.load(bytes)
    }
    DisposableEffect(lifecycle, renderer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderer.resume()
                Lifecycle.Event.ON_PAUSE -> renderer.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            renderer.destroy()
        }
    }
    AndroidView(factory = { renderer.textureView }, modifier = modifier)
}

/** Where the car model comes from: the driver's own file if chosen, else the bundled one. */
object CarModel {
    fun customFile(context: Context) = File(context.filesDir, "custom_car.glb")

    fun read(context: Context): ByteArray? = runCatching {
        val custom = customFile(context)
        if (custom.exists()) custom.readBytes()
        else context.assets.open("car/car.glb").use { it.readBytes() }
    }.getOrNull()
}

/**
 * Minimal Filament scene: one glTF car on a transparent background, a sun and soft ambient light.
 * Parked, the car turns slowly. Driving, it swings round to a chase view and its wheels spin with
 * the GPS speed.
 */
private class CarRenderer(context: Context, private val telemetry: () -> Pair<Int, Boolean>) {
    val textureView = TextureView(context)

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private val sun: Int
    private val indirectLight: IndirectLight
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val materials: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private var swapChain: SwapChain? = null
    private var asset: FilamentAsset? = null

    private val baseRoot = FloatArray(16)
    private val wheels = mutableListOf<Wheel>()
    private var yawDeg = 35f
    private var wheelAngle = 0f
    private var lastFrame = 0L
    private var running = false
    private var destroyed = false

    private class Wheel(val instance: Int, val base: FloatArray, val pivot: FloatArray)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || destroyed) return
            Choreographer.getInstance().postFrameCallback(this)
            render(frameTimeNanos)
        }
    }

    init {
        Native.init()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        view.scene = scene
        view.camera = camera
        // Transparent, so the dashboard card shows through behind the car.
        view.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        view.antiAliasing = View.AntiAliasing.FXAA

        sun = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1f, 0.98f, 0.95f)
            .intensity(90_000f)
            .direction(0.4f, -1f, -0.5f)
            .castShadows(false)
            .build(engine, sun)
        scene.addEntity(sun)
        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.55f, 0.62f, 0.72f))
            .intensity(35_000f)
            .build(engine)
        scene.indirectLight = indirectLight

        materials = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materials, EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                camera.setProjection(28.0, width.toDouble() / height.coerceAtLeast(1), 0.05, 50.0, Camera.Fov.VERTICAL)
            }
        }
        uiHelper.attachTo(textureView)
        camera.lookAt(0.0, 0.62, 3.6, 0.0, 0.02, 0.0, 0.0, 1.0, 0.0)
    }

    fun load(bytes: ByteArray) {
        if (destroyed || asset != null) return
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.rewind()
        val loaded = assetLoader.createAsset(buffer) ?: return
        resourceLoader.loadResources(loaded)
        loaded.releaseSourceData()
        asset = loaded
        scene.addEntities(loaded.entities)
        fitToUnitSize(loaded)
        hideLogos(loaded)
        pickPaint(loaded)
        findWheels(loaded)
    }

    fun resume() {
        if (destroyed || running) return
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun destroy() {
        if (destroyed) return
        pause()
        destroyed = true
        uiHelper.detach()
        asset?.let { assetLoader.destroyAsset(it) }
        assetLoader.destroy()
        resourceLoader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        engine.destroyIndirectLight(indirectLight)
        engine.destroyEntity(sun)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        EntityManager.get().destroy(sun)
        engine.destroy()
    }

    private fun render(frameTimeNanos: Long) {
        val dt = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = frameTimeNanos
        val (speedKmh, driving) = telemetry()
        animate(dt, speedKmh, driving)
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun animate(dt: Float, speedKmh: Int, driving: Boolean) {
        val loaded = asset ?: return
        if (driving) {
            // Ease round to the chase view: rear of the car towards the driver.
            val target = 180f
            var diff = ((target - yawDeg) % 360f + 540f) % 360f - 180f
            if (abs(diff) < 0.05f) diff = 0f
            yawDeg += diff * min(1f, dt * 1.8f)
        } else {
            // Parked: a slow turn, like a showroom turntable.
            yawDeg = (yawDeg + dt * 9f) % 360f
        }
        val root = FloatArray(16)
        Matrix.setRotateM(root, 0, yawDeg, 0f, 1f, 0f)
        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, root, 0, baseRoot, 0)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(loaded.root), world)

        // Wheel speed from road speed, capped so it never strobes at 60 frames a second.
        val radPerSec = min(speedKmh / 3.6f / WHEEL_RADIUS_M, 24f)
        wheelAngle = (wheelAngle + radPerSec * dt) % (2f * PI.toFloat())
        val degrees = Math.toDegrees(wheelAngle.toDouble()).toFloat()
        for (w in wheels) {
            val m = w.base.copyOf()
            Matrix.translateM(m, 0, w.pivot[0], w.pivot[1], w.pivot[2])
            Matrix.rotateM(m, 0, -degrees, 1f, 0f, 0f)
            Matrix.translateM(m, 0, -w.pivot[0], -w.pivot[1], -w.pivot[2])
            tm.setTransform(w.instance, m)
        }
    }

    /** Scales and centres the model to about two units long, whatever its original size. */
    private fun fitToUnitSize(loaded: FilamentAsset) {
        val box = loaded.boundingBox
        val half = box.halfExtent
        val maxExtent = 2f * maxOf(half[0], half[1], half[2])
        val scale = if (maxExtent > 0f) 2f / maxExtent else 1f
        Matrix.setIdentityM(baseRoot, 0)
        Matrix.scaleM(baseRoot, 0, scale, scale, scale)
        val c = box.center
        // Sit the car on the ground line rather than floating at its centre.
        Matrix.translateM(baseRoot, 0, -c[0], -c[1] + half[1] * 0.15f, -c[2])
    }

    /** The bundled model carries Khronos logos on its plate and steering wheel; hide those parts. */
    private fun hideLogos(loaded: FilamentAsset) {
        val rm = engine.renderableManager
        for (name in listOf("License Plate", "InteriorSteeringEmblem")) {
            val entity = loaded.getFirstEntityByName(name)
            if (entity != 0 && rm.hasComponent(entity)) rm.setLayerMask(rm.getInstance(entity), 0xff, 0x00)
        }
    }

    /** The bundled model has three paints; pearl white stands out best on the dark cockpit. */
    private fun pickPaint(loaded: FilamentAsset) {
        runCatching {
            val instance = loaded.instance
            val names = instance.materialVariantNames
            val pearl = names.indexOfFirst { it.contains("Pearl", ignoreCase = true) }
            if (pearl >= 0) instance.applyMaterialVariant(pearl)
        }
    }

    /** Finds the four wheels and the point each one turns about. Other models simply get no spin. */
    private fun findWheels(loaded: FilamentAsset) {
        val tm = engine.transformManager
        val rm = engine.renderableManager
        for (name in WHEEL_NODES) {
            val entity = loaded.getFirstEntityByName(name)
            if (entity == 0) continue
            val instance = tm.getInstance(entity)
            val parts = mutableListOf(entity)
            parts += tm.getChildren(instance, IntArray(tm.getChildCount(instance))).toList()
            val min = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val max = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            var found = false
            for (part in parts) {
                if (!rm.hasComponent(part)) continue
                val box = rm.getAxisAlignedBoundingBox(rm.getInstance(part), Box())
                for (i in 0..2) {
                    min[i] = minOf(min[i], box.center[i] - box.halfExtent[i])
                    max[i] = maxOf(max[i], box.center[i] + box.halfExtent[i])
                }
                found = true
            }
            if (!found) continue
            val pivot = FloatArray(3) { (min[it] + max[it]) / 2f }
            wheels += Wheel(instance, tm.getTransform(instance, FloatArray(16)), pivot)
        }
    }

    private companion object {
        const val WHEEL_RADIUS_M = 0.34f
        val WHEEL_NODES = listOf("WheelFrontL", "WheelFrontR", "WheelRearL", "WheelRearR")
    }
}

/** Loads Filament's native libraries once per process. */
private object Native {
    private var ready = false

    fun init() {
        if (ready) return
        Filament.init()
        Gltfio.init()
        ready = true
    }
}
