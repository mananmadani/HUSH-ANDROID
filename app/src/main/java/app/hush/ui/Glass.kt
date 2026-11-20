package app.hush.ui

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.hush.Prefs
import kotlin.math.abs

/**
 * How the phone is tilted, from -1 to 1 on each side, relative to how it is usually held.
 * Read it only inside draw blocks so the screen never has to be rebuilt when it moves.
 */
class GlassTilt {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

val LocalGlassTilt = staticCompositionLocalOf { GlassTilt() }

/** Listens to the motion sensor while the app is on screen, and only if "Glass motion" is on. */
@Composable
fun ProvideGlassMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tilt = remember { GlassTilt() }
    val enabled = Prefs.glassMotion

    DisposableEffect(owner, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            tilt.x = 0f
            tilt.y = 0f
            return@DisposableEffect onDispose { }
        }
        var sx = 0f
        var sy = 0f
        var bx = 0f
        var by = 0f
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val rx = e.values[0] / 9.81f
                val ry = e.values[2] / 9.81f
                if (!primed) {
                    sx = rx; sy = ry; bx = rx; by = ry; primed = true
                }
                // Smooth the shake out, and let the "resting" angle drift to wherever it is held.
                sx += 0.12f * (rx - sx)
                sy += 0.12f * (ry - sy)
                bx += 0.004f * (rx - bx)
                by += 0.004f * (ry - by)
                val nx = ((sx - bx) * 2.6f).coerceIn(-1f, 1f)
                val ny = ((sy - by) * 2.6f).coerceIn(-1f, 1f)
                if (abs(nx - tilt.x) > 0.004f) tilt.x = nx
                if (abs(ny - tilt.y) > 0.004f) tilt.y = ny
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    primed = false
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }

    CompositionLocalProvider(LocalGlassTilt provides tilt, content = content)
}

/**
 * A live copy of the screen that glass can blur (Android 12 and newer). The app's pages are
 * recorded once per frame into [node]; each glass piece then draws its own blurred patch of it.
 */
class GlassBackdrop {
    internal var node: Any? = null
    var tick by mutableIntStateOf(0)
}

/** Set only for glass that floats above the pages (the bottom bar and mini player). */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private object GlassGpu {
    @RequiresApi(31)
    fun record(scope: ContentDrawScope, backdrop: GlassBackdrop) {
        val w = scope.size.width.toInt()
        val h = scope.size.height.toInt()
        if (w <= 0 || h <= 0) {
            scope.drawContent()
            return
        }
        val node = (backdrop.node as? RenderNode) ?: RenderNode("hushBackdrop").also { backdrop.node = it }
        node.setPosition(0, 0, w, h)
        val recording = node.beginRecording()
        val context = scope.drawContext
        val previous = context.canvas
        context.canvas = androidx.compose.ui.graphics.Canvas(recording)
        try {
            scope.drawContent()
        } finally {
            context.canvas = previous
            node.endRecording()
        }
        scope.drawIntoCanvas { canvas ->
            if (canvas.nativeCanvas.isHardwareAccelerated) canvas.nativeCanvas.drawRenderNode(node)
        }
        Snapshot.withoutReadObservation { backdrop.tick = backdrop.tick + 1 }
    }

    /** Draws the blurred, slightly more colourful patch of the backdrop that sits under this glass. */
    @RequiresApi(31)
    fun draw(scope: DrawScope, backdrop: GlassBackdrop, holder: NodeHolder, origin: Offset, radiusPx: Float): Boolean {
        val source = backdrop.node as? RenderNode ?: return false
        val w = scope.size.width.toInt()
        val h = scope.size.height.toInt()
        if (w <= 0 || h <= 0) return false
        var drawn = false
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (!native.isHardwareAccelerated) return@drawIntoCanvas
            val patch = (holder.node as? RenderNode) ?: RenderNode("hushGlass").also { holder.node = it }
            patch.setPosition(0, 0, w, h)
            val saturate = ColorMatrix().apply { setSaturation(1.7f) }
            patch.setRenderEffect(
                RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(saturate),
                    RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                )
            )
            val rc = patch.beginRecording()
            rc.translate(-origin.x, -origin.y)
            rc.drawRenderNode(source)
            patch.endRecording()
            native.drawRenderNode(patch)
            drawn = true
        }
        return drawn
    }
}

internal class NodeHolder {
    var node: Any? = null
}

/** Marks the pages as the thing glass blurs. Put it first in the chain, before backgrounds. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier =
    if (Build.VERSION.SDK_INT < 31) this
    else this.drawWithContent { GlassGpu.record(this, backdrop) }

/**
 * Liquid glass: a see-through body, a bright rim that slides with the tilt of the phone, a
 * soft highlight that follows the tilt and then your finger, and (if [interactive]) a springy
 * swell when pressed, like a drop of liquid.
 *
 * [tint] colours the glass (for example with the accent). [lift] adds a soft shadow so it floats.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    lift: Dp = 0.dp,
    interactive: Boolean = false,
    backing: Color = Color.Unspecified
): Modifier = composed {
    val tilt = LocalGlassTilt.current
    val backdrop = LocalGlassBackdrop.current
    val holder = remember { NodeHolder() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    var down by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (down) 1f else 0f,
        spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "glassPress"
    )

    val base = this
        .then(if (interactive) {
            Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down = true
                        touch = first.position
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.firstOrNull()?.let { touch = it.position }
                        } while (event.changes.any { it.pressed })
                        down = false
                    }
                }
                .graphicsLayer {
                    val s = 1f + 0.06f * press
                    scaleX = s
                    scaleY = s
                }
        } else Modifier)

    base
        .then(if (backdrop != null) Modifier.onGloballyPositioned { origin = it.positionInRoot() } else Modifier)
        .then(if (lift > 0.dp) Modifier.shadow(lift, shape, clip = false,
            ambientColor = Color(0x55000000), spotColor = Color(0x66000000)) else Modifier)
        .clip(shape)
        .drawBehind {
            val light = Prefs.light
            val w = size.width
            val h = size.height
            val big = maxOf(w, h)

            // Real frosted glass: a blurred copy of whatever is behind (Android 12 and newer).
            val clarity = Prefs.glassClarity
            var blurred = false
            if (backdrop != null && Build.VERSION.SDK_INT >= 31) {
                backdrop.tick // redraw whenever the screen behind moves
                val radius = (12f + 20f * (1f - clarity)) * density
                blurred = GlassGpu.draw(this, backdrop, holder, origin, radius)
            }

            // A veil over it. The transparency slider decides how much of it there is.
            val veil = if (backing != Color.Unspecified) backing else Dusk.Surface.copy(alpha = 0.55f)
            val strength = (0.2f + 0.8f * (1f - clarity)) * (if (blurred) 0.55f else 1f)
            drawRect(veil.copy(alpha = (veil.alpha * strength).coerceIn(0f, 1f)))

            // The body of the glass.
            val bodyTop = if (light) Color(0xB3FFFFFF) else Color(0x26FFFFFF)
            val bodyBottom = if (light) Color(0x80FFFFFF) else Color(0x0DFFFFFF)
            drawOutline(
                shape.createOutline(size, layoutDirection, this),
                Brush.linearGradient(listOf(bodyTop, bodyBottom), Offset(0f, 0f), Offset(w * 0.4f, h))
            )
            if (tint != Color.Unspecified) {
                drawRect(tint.copy(alpha = tint.alpha * (0.55f + 0.45f * press)))
            }

            // A soft highlight that slides with the tilt and is pulled to the finger when pressed.
            val tiltPoint = Offset(w * (0.5f + 0.45f * tilt.x), h * (0.5f + 0.45f * tilt.y))
            val centre = if (touch.isSpecified && press > 0.01f)
                Offset(
                    tiltPoint.x + (touch.x - tiltPoint.x) * press,
                    tiltPoint.y + (touch.y - tiltPoint.y) * press
                )
            else tiltPoint
            val glow = (if (light) 0.5f else 0.26f) + 0.22f * press
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = glow), Color.Transparent),
                    center = centre,
                    radius = big * 0.8f
                )
            )
        }
        .drawWithContent {
            drawContent()
            // The bright edge: its light and dark ends swing as the phone tilts.
            val w = size.width
            val h = size.height
            val dx = w * 0.7f * tilt.x
            val rimStrong = if (Prefs.light) Color(0xFFFFFFFF) else Color(0xB3FFFFFF)
            val rimSoft = if (Prefs.light) Color(0x33000000) else Color(0x14FFFFFF)
            drawOutline(
                shape.createOutline(size, layoutDirection, this),
                Brush.linearGradient(
                    listOf(rimStrong, rimSoft, rimSoft, rimStrong.copy(alpha = rimStrong.alpha * 0.55f)),
                    start = Offset(dx, 0f),
                    end = Offset(w + dx, h)
                ),
                style = Stroke(width = 2.dp.toPx())
            )
        }
}

/** A light sheen that slides across a cover (or any picture) as the phone tilts. */
fun Modifier.glassSheen(): Modifier = composed {
    val tilt = LocalGlassTilt.current
    drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        val c = Offset(w * (0.5f + 0.55f * tilt.x), h * (0.35f + 0.5f * tilt.y))
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                center = c,
                radius = maxOf(w, h) * 0.75f
            )
        )
    }
}

/** Turns a card a few degrees towards the tilt, so it feels like a physical pane. */
fun Modifier.tiltParallax(maxDegrees: Float = 7f): Modifier = composed {
    val tilt = LocalGlassTilt.current
    graphicsLayer {
        rotationY = tilt.x * maxDegrees
        rotationX = -tilt.y * maxDegrees
        cameraDistance = 18f * density
    }
}
