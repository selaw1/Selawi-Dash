package com.selawi.cockpit.features.vehicle

import android.content.Context
import android.os.SystemClock
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
import com.selawi.cockpit.features.intro.Intro
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
        val plane = withContext(Dispatchers.IO) { runCatching { context.assets.open("plane/plane.glb").use { it.readBytes() } }.getOrNull() }
        if (plane != null) renderer.loadPlane(plane)
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
    /** The paint to show (see CarPaint); the renderer applies it when it changes. */
    var paint: String = CarPaint.Pearl
    /** 0 car … 1 plane, eased by the UI; the swap animation follows it. */
    var flight = 0f
    /** 0 on the ground … 1 in the air, eased by the UI: the gear folds and the plane banks. */
    var air = 0f
    /** Climb in metres a second; the plane's nose follows it in the air. */
    var climb = 0f
    /** The jet's body colour (ARGB). */
    var planePaint: Int = 0xFFE9EEF2.toInt()
    /** Its tails, stripes and wingtips (ARGB): the accent colour. */
    var livery: Int = 0xFF2F6BFF.toInt()

    /** World positions of the two ends of the rear indicator light (x, y, z each), when known. */
    val signalEnds = FloatArray(6)
    var hasSignalEnds = false

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
    private var plane: FilamentAsset? = null
    private val planeBase = FloatArray(16)
    private var gearEntity = 0
    private val gearPivot = FloatArray(3)
    private val planeLights = mutableMapOf<String, MutableList<MaterialInstance>>()
    private var appliedLivery: Int? = null
    private var appliedPlanePaint: Int? = null
    // Parts that stay hidden however the car or plane is shown (the model's logos).
    private val hiddenParts = mutableSetOf<Int>()
    private val shown = mutableMapOf<FilamentAsset, Boolean>()

    private val baseRoot = FloatArray(16)
    private val wheels = mutableListOf<Wheel>()
    private val brakeLights = mutableListOf<MaterialInstance>()
    private var shownBrake = -1f
    private val headlights = mutableListOf<MaterialInstance>()
    private var signalEntity = 0
    private var appliedPaint: String? = null
    private var shownHeadlights = -1f
    private var yawDeg = FRONT_YAW
    private var aspect = 1.6
    private var clock = 0f
    private var userYaw = FRONT_YAW
    private var resetting = false
    // At start-up the car faces away, then swings round to the front as the intro fades.
    private var introSwing = Intro.swingAt > SystemClock.elapsedRealtime()
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
        fitToUnitSize(loaded, baseRoot)
        hideLogos(loaded)
        coverTireLettering(loaded)
        findWheels(loaded)
        findBrakeLights(loaded)
        signalEntity = loaded.getFirstEntityByName("BodyTurnsignalsRear")
    }

    /** The airliner for flight mode, loaded next to the car and hidden until the swap. */
    fun loadPlane(bytes: ByteArray) {
        if (destroyed || plane != null) return
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.rewind()
        val loaded = assetLoader.createAsset(buffer) ?: return
        resourceLoader.loadResources(loaded)
        loaded.releaseSourceData()
        plane = loaded
        scene.addEntities(loaded.entities)
        fitToUnitSize(loaded, planeBase)
        show(loaded, false)
        val rm = engine.renderableManager
        gearEntity = loaded.getFirstEntityByName("Gear")
        if (gearEntity != 0 && rm.hasComponent(gearEntity)) {
            val box = rm.getAxisAlignedBoundingBox(rm.getInstance(gearEntity), Box())
            // The gear folds up towards where it meets the body.
            gearPivot[0] = box.center[0]
            gearPivot[1] = box.center[1] + box.halfExtent[1]
            gearPivot[2] = box.center[2]
        }
        for (entity in loaded.renderableEntities) {
            val instance = rm.getInstance(entity)
            for (prim in 0 until rm.getPrimitiveCount(instance)) {
                val material = rm.getMaterialInstanceAt(instance, prim)
                val name = material.name ?: continue
                if (name in LIGHT_MATERIALS || name == "Livery" || name == "Paint") planeLights.getOrPut(name) { mutableListOf() }.add(material)
            }
        }
    }

    /** Shows or hides a whole model without unloading it; hidden parts such as logos stay hidden. */
    private fun show(loaded: FilamentAsset, visible: Boolean) {
        if (shown[loaded] == visible) return
        shown[loaded] = visible
        val rm = engine.renderableManager
        for (entity in loaded.renderableEntities) {
            if (entity in hiddenParts || !rm.hasComponent(entity)) continue
            rm.setLayerMask(rm.getInstance(entity), 0xff, if (visible) 0xff else 0x00)
        }
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
        plane?.let { assetLoader.destroyAsset(it) }
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
        if (introSwing) {
            val t = (SystemClock.elapsedRealtime() - Intro.swingAt) / INTRO_SWING_MS
            if (t >= 1f) {
                introSwing = false
                yawDeg = userYaw
            } else {
                yawDeg = FRONT_YAW - 180f * (1f - CarScene.smooth(t.coerceIn(0f, 1f)))
            }
        }

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
        // The swap to the plane: the car lifts, spins away and shrinks to nothing; the plane then
        // swoops down in its place and settles with a small overshoot. Reversed, it plays backwards.
        val flight = control.flight.coerceIn(0f, 1f)
        val air = CarScene.smooth(control.air.coerceIn(0f, 1f))
        val carGone = CarScene.smooth((flight / 0.55f).coerceIn(0f, 1f))
        val away = FloatArray(16)
        Matrix.setIdentityM(away, 0)
        Matrix.translateM(away, 0, 0f, carGone * 0.5f, 0f)
        Matrix.rotateM(away, 0, carGone * 160f, 0f, 1f, 0f)
        val carScale = (1f - carGone).coerceAtLeast(0.001f)
        Matrix.scaleM(away, 0, carScale, carScale, carScale)
        val carWorld = FloatArray(16)
        Matrix.multiplyMM(carWorld, 0, away, 0, world, 0)
        tm.setTransform(tm.getInstance(loaded.root), carWorld)
        show(loaded, carGone < 0.999f)
        animatePlane(((flight - 0.35f) / 0.65f).coerceIn(0f, 1f), air, yaw, steer)
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
        if (control.paint != appliedPaint) applyPaint(loaded, control.paint)
        lightBrakes(control.brake.coerceIn(0f, 1f))
        locateSignals()
        lightHeadlights(Intro.headlights())
    }

    /**
     * The plane: [arrive] 0…1 brings it in, [air] folds the gear and lets it bank into turns and
     * pitch with the climb. Wingtip navigation lights stay on, the strobes double-flash and the
     * beacon pulses.
     */
    private fun animatePlane(arrive: Float, air: Float, yaw: Float, steer: Float) {
        val p = plane ?: return
        show(p, arrive > 0.001f)
        if (arrive <= 0.001f) return
        val tm = engine.transformManager
        val settle = CarScene.smooth(arrive)
        // Ease out with a slight overshoot, so the plane lands in place with some weight.
        val t = arrive - 1f
        val scale = (1f + 2.70158f * t * t * t + 1.70158f * t * t).coerceAtLeast(0.001f)
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        val hover = air * (0.32f + kotlin.math.sin(clock * 0.9f) * 0.025f)
        Matrix.translateM(m, 0, 0f, (1f - settle) * 0.9f + hover, 0f)
        Matrix.rotateM(m, 0, yaw - (1f - settle) * 160f, 0f, 1f, 0f)
        // Nose up while climbing, down while descending; banked into the turn in the air.
        val pitch = (control.climb / 8f).coerceIn(-1f, 1f) * 9f * air
        Matrix.rotateM(m, 0, -pitch, 1f, 0f, 0f)
        Matrix.rotateM(m, 0, -steer * (24f * air + 1.2f * (1f - air)), 0f, 0f, 1f)
        Matrix.scaleM(m, 0, scale, scale, scale)
        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, m, 0, planeBase, 0)
        tm.setTransform(tm.getInstance(p.root), world)
        if (gearEntity != 0) {
            val g = (1f - air).coerceAtLeast(0.001f)
            val gear = FloatArray(16)
            Matrix.setIdentityM(gear, 0)
            Matrix.translateM(gear, 0, gearPivot[0], gearPivot[1], gearPivot[2])
            Matrix.scaleM(gear, 0, g, g, g)
            Matrix.translateM(gear, 0, -gearPivot[0], -gearPivot[1], -gearPivot[2])
            tm.setTransform(tm.getInstance(gearEntity), gear)
        }
        val cycle = clock % 1.3f
        val strobe = if (cycle < 0.05f || cycle in 0.13f..0.18f) 9f else 0.03f
        lightPlane("NavRed", 2.6f, 0.06f, 0.04f)
        lightPlane("NavGreen", 0.06f, 2.6f, 0.5f)
        lightPlane("Strobe", strobe, strobe, strobe)
        val beacon = if (clock % 1.1f < 0.14f) 4f else 0.12f
        lightPlane("Beacon", beacon, beacon * 0.05f, beacon * 0.03f)
        if (control.livery != appliedLivery) {
            appliedLivery = control.livery
            recolour("Livery", control.livery)
        }
        if (control.planePaint != appliedPlanePaint) {
            appliedPlanePaint = control.planePaint
            recolour("Paint", control.planePaint)
        }
    }

    private fun recolour(material: String, argb: Int) {
        val r = linear(((argb shr 16) and 0xff) / 255f)
        val g = linear(((argb shr 8) and 0xff) / 255f)
        val b = linear((argb and 0xff) / 255f)
        planeLights[material]?.forEach { runCatching { it.setParameter("baseColorFactor", r, g, b, 1f) } }
    }

    private fun lightPlane(name: String, r: Float, g: Float, b: Float) {
        planeLights[name]?.forEach { runCatching { it.setParameter("emissiveFactor", r, g, b) } }
    }

    /** The model's brake and head light materials, so they can light up. */
    private fun findBrakeLights(loaded: FilamentAsset) {
        val rm = engine.renderableManager
        for (entity in loaded.renderableEntities) {
            val instance = rm.getInstance(entity)
            for (prim in 0 until rm.getPrimitiveCount(instance)) {
                val material = rm.getMaterialInstanceAt(instance, prim)
                if (material.name.equals("Brakelight", ignoreCase = true) && material !in brakeLights) brakeLights += material
                if (material.name.equals("Headlight", ignoreCase = true) && material !in headlights) headlights += material
            }
        }
        lightBrakes(0f)
    }

    /** Where the rear indicator's two ends are this frame, so the UI can blink one of them. */
    private fun locateSignals() {
        val entity = signalEntity
        val rm = engine.renderableManager
        if (entity == 0 || !rm.hasComponent(entity)) return
        val tm = engine.transformManager
        val world = tm.getWorldTransform(tm.getInstance(entity), FloatArray(16))
        val box = rm.getAxisAlignedBoundingBox(rm.getInstance(entity), Box())
        val c = box.center
        val h = box.halfExtent
        for ((i, side) in listOf(-1f, 1f).withIndex()) {
            val local = floatArrayOf(c[0] + side * h[0] * 0.85f, c[1], c[2], 1f)
            val out = FloatArray(4)
            Matrix.multiplyMV(out, 0, world, 0, local, 0)
            control.signalEnds[i * 3] = out[0]
            control.signalEnds[i * 3 + 1] = out[1]
            control.signalEnds[i * 3 + 2] = out[2]
        }
        control.hasSignalEnds = true
    }

    /** Cool white headlights; the start-up intro flicks them on. */
    private fun lightHeadlights(level: Float) {
        if (headlights.isEmpty() || abs(level - shownHeadlights) < 0.01f) return
        shownHeadlights = level
        val k = 0.03f + level * 0.97f
        for (m in headlights) {
            runCatching { m.setParameter("emissiveFactor", 0.54f * k, 0.74f * k, 1f * k) }
        }
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
    private fun fitToUnitSize(loaded: FilamentAsset, baseRoot: FloatArray) {
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
            if (entity != 0 && rm.hasComponent(entity)) {
                rm.setLayerMask(rm.getInstance(entity), 0xff, 0x00)
                hiddenParts += entity
            }
        }
    }

    /**
     * The bundled model has three paints (pearl white, carmine red, graphite). A custom colour
     * starts from the red paint, which has a clear coat and no colour-shift, and replaces its
     * colour on the body. Models without these paints keep their own.
     */
    private fun applyPaint(loaded: FilamentAsset, paint: String) {
        appliedPaint = paint
        runCatching {
            val instance = loaded.instance
            val names = instance.materialVariantNames
            val custom = CarPaint.customColor(paint)
            val variant = if (custom != null) CarPaint.Carmine else paint
            val index = names.indexOfFirst { it.contains(variant, ignoreCase = true) }
            if (index < 0) return
            instance.applyMaterialVariant(index)
            // Body panels use "Paint 1 Carmine" in the red paint.
            val rm = engine.renderableManager
            val (r, g, b) = if (custom != null) {
                Triple(linear(custom.red), linear(custom.green), linear(custom.blue))
            } else {
                Triple(0.666f, 0f, 0f)
            }
            for (entity in loaded.renderableEntities) {
                val ri = rm.getInstance(entity)
                for (prim in 0 until rm.getPrimitiveCount(ri)) {
                    val material = rm.getMaterialInstanceAt(ri, prim)
                    if (material.name == "Paint 1 Carmine") material.setParameter("baseColorFactor", r, g, b, 1f)
                    // The red paint comes with red seat trim; a custom colour gets neutral dark trim.
                    if (material.name == "Interior 3 Carmine") {
                        if (custom != null) material.setParameter("baseColorFactor", 0.02f, 0.02f, 0.02f, 1f)
                        else material.setParameter("baseColorFactor", 0.2158f, 0.005f, 0f, 1f)
                    }
                }
            }
        }
    }

    /** Screen colour (sRGB) to the linear value the renderer expects. */
    private fun linear(c: Float) = Math.pow(c.toDouble(), 2.2).toFloat()

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
        val v = CarScene.view(aspect, control.drive, control.speedFx, control.air)
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
        const val INTRO_SWING_MS = 2400f
        const val WHEEL_RADIUS_M = 0.34f
        val WHEEL_NODES = listOf("WheelFrontL", "WheelFrontR", "WheelRearL", "WheelRearR")
        val LIGHT_MATERIALS = setOf("NavRed", "NavGreen", "Strobe", "Beacon")
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
