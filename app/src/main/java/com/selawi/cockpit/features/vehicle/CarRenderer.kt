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
import com.google.android.filament.MaterialInstance
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
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
internal fun LiveCar(modifier: Modifier, speedKmh: Int, driving: Boolean, control: CarControl) {
    val context = LocalContext.current
    val speed = rememberUpdatedState(speedKmh)
    val isDriving = rememberUpdatedState(driving)
    val renderer = remember { CarRenderer(context, control) { Telemetry(speed.value, isDriving.value) } }
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
internal class Telemetry(val speedKmh: Int, val driving: Boolean)

/**
 * Touch input for the car, written by the UI and read by the renderer on each frame
 * (both on the main thread).
 */
class CarControl {
    internal var pendingDegrees = 0f
    internal var flingDegreesPerSecond = 0f
    internal var resetRequested = false

    /** 0 parked … 1 driving, eased by the UI; moves the camera and the car together. */
    var drive = 0f
    /** -1 (left) … 1 (right): how sharply the road is curving, from GPS heading. */
    var steer = 0f
    /** 0 … 1: road speed eased by the UI, for the camera's sense of speed. */
    var speedFx = 0f
    /** 0 … 1: how hard the car is braking, eased by the UI; lights the brake lights. */
    var brake = 0f

    /** The driver dragged the car round by this many degrees. */
    fun drag(degrees: Float) {
        pendingDegrees += degrees
        flingDegreesPerSecond = 0f
    }

    /** The finger left the screen; the car keeps turning a little and slows down. */
    fun release(degreesPerSecond: Float) {
        flingDegreesPerSecond = degreesPerSecond.coerceIn(-720f, 720f)
    }

    /** Back to the default front three-quarter view. */
    fun reset() {
        resetRequested = true
        flingDegreesPerSecond = 0f
    }
}

private class CarRenderer(
    context: Context,
    private val control: CarControl,
    private val telemetry: () -> Telemetry,
) {
    val textureView = TextureView(context)

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private val sun: Int
    private val indirectLight: IndirectLight
    private val reflections: Texture
    private val plainTire: Texture
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val materials: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private var swapChain: SwapChain? = null
    private var asset: FilamentAsset? = null

    private val baseRoot = FloatArray(16)
    private val wheels = mutableListOf<Wheel>()
    private val brakeLights = mutableListOf<MaterialInstance>()
    private var shownBrake = -1f
    private var yawDeg = FRONT_YAW
    private var aspect = 1.6
    private var clock = 0f
    private var userYaw = FRONT_YAW
    private var resetting = false
    private var wheelAngle = 0f
    private var lastFrame = 0L
    private var running = false
    private var destroyed = false

    private class Wheel(val instance: Int, val base: FloatArray, val pivot: FloatArray, val front: Boolean)

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
        // A soft studio environment. Shiny paint and metal need something to reflect;
        // without it they render almost black.
        reflections = Studio.cubemap(engine)
        indirectLight = IndirectLight.Builder()
            .reflections(reflections)
            .irradiance(1, floatArrayOf(0.85f, 0.88f, 0.92f))
            .intensity(28_000f)
            .build(engine)
        scene.indirectLight = indirectLight

        plainTire = Studio.solid(engine, 0.05f, 0.05f, 0.055f)
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
                frame(width.toDouble() / height.coerceAtLeast(1))
            }
        }
        uiHelper.attachTo(textureView)
        frame(aspect)
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
        coverTireLettering(loaded)
        pickPaint(loaded)
        findWheels(loaded)
        findBrakeLights(loaded)
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
        engine.destroyTexture(reflections)
        engine.destroyTexture(plainTire)
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
        val t = telemetry()
        animate(dt, t.speedKmh, t.driving)
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun animate(dt: Float, speedKmh: Int, driving: Boolean) {
        val loaded = asset ?: return
        clock += dt
        val drive = CarScene.smooth(control.drive.coerceIn(0f, 1f))

        // Parked: wherever the driver turned it, with a little momentum after a flick.
        userYaw += control.pendingDegrees
        control.pendingDegrees = 0f
        if (abs(control.flingDegreesPerSecond) > 1f) {
            userYaw += control.flingDegreesPerSecond * dt
            control.flingDegreesPerSecond *= kotlin.math.exp(-3.5f * dt)
        } else {
            control.flingDegreesPerSecond = 0f
        }
        if (control.resetRequested) {
            control.resetRequested = false
            userYaw = yawDeg + (((FRONT_YAW - yawDeg) % 360f + 540f) % 360f - 180f)
            resetting = true
        }
        val diff = userYaw - yawDeg
        yawDeg += diff * min(1f, dt * if (resetting) 3f else 14f)
        if (abs(diff) < 0.05f) resetting = false

        // Driving: the car turns to face down the road (away from the camera) as the camera swings
        // behind it; both follow the same eased drive value, so the change is one smooth move.
        val toRoad = (((180f - yawDeg) % 360f + 540f) % 360f) - 180f
        val yaw = yawDeg + toRoad * drive
        val speedFactor = min(1f, speedKmh / 70f)
        val bob = (kotlin.math.sin(clock * 10.5f) * 0.0025f + kotlin.math.sin(clock * 3.1f) * 0.0018f) * speedFactor * drive
        val steer = control.steer.coerceIn(-1f, 1f) * drive
        val root = FloatArray(16)
        Matrix.setRotateM(root, 0, yaw, 0f, 1f, 0f)
        Matrix.translateM(root, 0, 0f, bob, 0f)
        // Lean out of the bend, as a real car does.
        Matrix.rotateM(root, 0, steer * 2.2f, 0f, 0f, 1f)
        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, root, 0, baseRoot, 0)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(loaded.root), world)
        placeCamera()

        // Wheels roll with road speed (capped so they never strobe) and the front ones steer.
        val radPerSec = min(speedKmh / 3.6f / WHEEL_RADIUS_M, 26f)
        wheelAngle = (wheelAngle + radPerSec * dt) % (2f * PI.toFloat())
        val degrees = Math.toDegrees(wheelAngle.toDouble()).toFloat()
        for (w in wheels) {
            val m = w.base.copyOf()
            Matrix.translateM(m, 0, w.pivot[0], w.pivot[1], w.pivot[2])
            if (w.front) Matrix.rotateM(m, 0, -steer * 16f, 0f, 0f, 1f)
            Matrix.rotateM(m, 0, -degrees, 1f, 0f, 0f)
            Matrix.translateM(m, 0, -w.pivot[0], -w.pivot[1], -w.pivot[2])
            tm.setTransform(w.instance, m)
        }
        lightBrakes(control.brake.coerceIn(0f, 1f))
    }

    /** The model's rear light materials, so they can glow when the car brakes. */
    private fun findBrakeLights(loaded: FilamentAsset) {
        val rm = engine.renderableManager
        for (entity in loaded.renderableEntities) {
            val instance = rm.getInstance(entity)
            for (prim in 0 until rm.getPrimitiveCount(instance)) {
                val material = rm.getMaterialInstanceAt(instance, prim)
                if (material.name.equals("Brakelight", ignoreCase = true) && material !in brakeLights) brakeLights += material
            }
        }
        lightBrakes(0f)
    }

    /** Dim tail lights at rest, bright red the harder the car brakes. */
    private fun lightBrakes(level: Float) {
        if (brakeLights.isEmpty() || abs(level - shownBrake) < 0.01f) return
        shownBrake = level
        val glow = 0.25f + level * 2.2f
        for (m in brakeLights) {
            runCatching { m.setParameter("emissiveFactor", glow, glow * 0.04f, glow * 0.03f) }
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
        // Wheels on the ground (y = 0), so the car sits on the road drawn behind it.
        Matrix.translateM(baseRoot, 0, -c[0], -c[1] + half[1], -c[2])
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
            wheels += Wheel(instance, tm.getTransform(instance, FloatArray(16)), pivot, name.contains("Front"))
        }
    }

    /**
     * Places the camera so the whole car fits the card, whatever its shape. The car is about two
     * units long after [fitToUnitSize]; at a three-quarter angle it spans about 2.2 units.
     */
    private fun frame(aspect: Double) {
        this.aspect = aspect
        camera.setProjection(CarScene.V_FOV_DEG, aspect, 0.05, 80.0, Camera.Fov.VERTICAL)
        placeCamera()
    }

    /** Same camera the road behind the car is drawn with. */
    private fun placeCamera() {
        val v = CarScene.view(aspect, control.drive, control.speedFx)
        camera.lookAt(v.eye[0], v.eye[1], v.eye[2], v.target[0], v.target[1], v.target[2], 0.0, 1.0, 0.0)
    }

    /** The bundled tyres carry Khronos lettering on the sidewall; paint the sidewall plain. */
    private fun coverTireLettering(loaded: FilamentAsset) {
        val rm = engine.renderableManager
        val sampler = TextureSampler()
        for (entity in loaded.renderableEntities) {
            val instance = rm.getInstance(entity)
            for (prim in 0 until rm.getPrimitiveCount(instance)) {
                val material = rm.getMaterialInstanceAt(instance, prim)
                if (material.name?.contains("Tireside", ignoreCase = true) == true) {
                    runCatching { material.setParameter("baseColorMap", plainTire, sampler) }
                }
            }
        }
    }

    private companion object {
        const val FRONT_YAW = 35f
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
