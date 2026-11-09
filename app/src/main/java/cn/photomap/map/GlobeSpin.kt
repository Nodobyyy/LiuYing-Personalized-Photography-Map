package cn.photomap.map

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.animation.LinearInterpolator
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.abs

/** An optional longitude pan at globe zoom. It never changes the compass bearing. */
internal class GlobeSpin(
    private val view: MapView,
    private val map: MapLibreMap,
    private val available: () -> Boolean,
    private val refreshMarkers: () -> Unit
) {
    private var tracker: VelocityTracker? = null
    private var startX = 0f
    private var animation: ValueAnimator? = null
    private var gestureId = 0
    val spinning get() = animation != null

    init {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stop()
                    if (available()) {
                        startX = event.x
                        tracker = VelocityTracker.obtain()
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> discardTouch()
            }
            tracker?.addMovement(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                tracker?.let { motion ->
                    motion.computeCurrentVelocity(1000)
                    val density = view.resources.displayMetrics.density
                    val speed = motion.xVelocity / density
                    if (available() && abs(speed) >= 1350 && abs(motion.xVelocity) > 2 * abs(motion.yVelocity)
                        && abs(event.x - startX) > 38 * density) {
                        val id = gestureId
                        // Let MapView finish processing the same ACTION_UP first.
                        view.post { if (id == gestureId && available()) spin(if (speed < 0) 1 else -1) }
                    }
                }
                discardTouch()
            }
            false
        }
    }

    private fun spin(direction: Int) {
        val camera = map.cameraPosition
        val target = camera.target ?: return
        map.cancelTransitions()
        var lastFrame = 0L
        var lastMarkers = 0L
        animation = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 16_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                if (now - lastFrame < 30) return@addUpdateListener
                lastFrame = now
                val longitude = target.longitude + direction * (it.animatedValue as Float)
                map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera)
                    .target(LatLng(target.latitude, longitude).wrap()).bearing(0.0).build()))
                if (now - lastMarkers >= 750) {
                    lastMarkers = now
                    refreshMarkers()
                }
            }
        }
        animation?.start()
    }

    private fun discardTouch() {
        tracker?.recycle()
        tracker = null
    }

    fun stop() {
        gestureId++
        discardTouch()
        val current = animation ?: return
        animation = null
        current.cancel()
        refreshMarkers()
    }
}
