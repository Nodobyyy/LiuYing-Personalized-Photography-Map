package cn.photomap.map

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** One-finger globe drag and the optional continuous spin. Pinch stays native. */
internal class GlobeSpin(
    private val view: MapView,
    private val map: MapLibreMap,
    private val available: () -> Boolean,
    private val refreshMarkers: () -> Unit,
    private val onSettled: () -> Unit
) {
    private var tracker: VelocityTracker? = null
    private var startX = 0f
    private var startY = 0f
    private var dragStart: CameraPosition? = null
    private var dragPosition: CameraPosition? = null
    private var dragging = false
    private var nativeScrollSuspended = false
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val applyDrag = Runnable { applyPendingDrag() }
    private var animation: ValueAnimator? = null
    private var gestureId = 0
    val inMotion get() = dragging || animation != null

    init {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stop()
                    if (available()) {
                        startX = event.x
                        startY = event.y
                        dragStart = map.cameraPosition
                        nativeScrollSuspended = map.uiSettings.isScrollGesturesEnabled
                        if (nativeScrollSuspended) map.uiSettings.isScrollGesturesEnabled = false
                        tracker = VelocityTracker.obtain()
                    }
                }
                MotionEvent.ACTION_MOVE -> updateDrag(event)
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                    applyPendingDrag()
                    discardTouch()
                    restoreScroll()
                }
            }
            tracker?.addMovement(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                updateDrag(event)
                applyPendingDrag()
                val id = gestureId
                val didDrag = dragging
                var direction = 0
                tracker?.let { motion ->
                    motion.computeCurrentVelocity(1000)
                    val density = view.resources.displayMetrics.density
                    val speed = motion.xVelocity / density
                    if (available() && abs(speed) >= 1350 && abs(motion.xVelocity) > 2 * abs(motion.yVelocity)
                        && abs(event.x - startX) > 38 * density) {
                        direction = if (speed < 0) 1 else -1
                    }
                }
                discardTouch()
                // Keep native panning disabled until MapView handles ACTION_UP,
                // so it cannot start a competing fling from the same gesture.
                view.post {
                    if (id != gestureId) return@post
                    restoreScroll()
                    if (direction != 0 && available()) spin(direction)
                    else if (didDrag) onSettled()
                }
            }
            false
        }
    }

    private fun updateDrag(event: MotionEvent) {
        val camera = dragStart ?: return
        val target = camera.target ?: return
        val dx = (event.x - startX).toDouble()
        val dy = (event.y - startY).toDouble()
        if (!dragging && hypot(dx, dy) < touchSlop) return
        dragging = true
        val zoom = GlobeCamera.equatorialZoom(camera.zoom, target.latitude)
        val radius = GlobeCamera.surfaceRadius(zoom, view.resources.displayMetrics.density)
        val degreesPerPixel = Math.toDegrees(1 / radius)
        val latitude = (target.latitude + dy * degreesPerPixel)
            .coerceIn(-GlobeCamera.MAX_MERCATOR_LATITUDE, GlobeCamera.MAX_MERCATOR_LATITUDE)
        val longitude = target.longitude - dx * degreesPerPixel / cos(Math.toRadians(target.latitude.coerceIn(-60.0, 60.0)))
        if (dragPosition == null) view.postOnAnimation(applyDrag)
        dragPosition = CameraPosition.Builder(camera).target(LatLng(latitude, longitude).wrap())
            .zoom(GlobeCamera.zoomAtLatitude(zoom, latitude)).bearing(0.0).build()
    }

    private fun applyPendingDrag() {
        view.removeCallbacks(applyDrag)
        val position = dragPosition ?: return
        dragPosition = null
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
    }

    private fun restoreScroll() {
        if (!nativeScrollSuspended) return
        nativeScrollSuspended = false
        map.uiSettings.isScrollGesturesEnabled = true
    }

    private fun spin(direction: Int) {
        val camera = map.cameraPosition
        val target = camera.target ?: return
        map.cancelTransitions()
        var lastMarkers = 0L
        animation = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 16_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
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
        view.removeCallbacks(applyDrag)
        dragStart = null
        dragPosition = null
        dragging = false
    }

    fun stop() {
        gestureId++
        discardTouch()
        restoreScroll()
        val current = animation ?: return
        animation = null
        current.cancel()
        refreshMarkers()
    }
}
