package cn.photomap.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Shader
import android.os.Build
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/** Draw the bundled globe below MapLibre's labels and photo markers. */
internal class GlobeBackdrop(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val vertices = FloatArray(RINGS * SLICES * 12)
    private val textureCoordinates = FloatArray(vertices.size)
    private val x = DoubleArray((RINGS + 1) * SLICES)
    private val y = DoubleArray(x.size)
    private val u = DoubleArray(x.size)
    private val v = DoubleArray(x.size)
    private val east = DoubleArray(SLICES) { cos(it * TWO_PI / SLICES) }
    private val north = DoubleArray(SLICES) { sin(it * TWO_PI / SLICES) }
    private var map: MapLibreMap? = null
    private var image: Bitmap? = null
    private var satellite = false
    private var visible = false

    init {
        // Canvas.drawVertices is hardware accelerated starting with API 29.
        if (Build.VERSION.SDK_INT < 29) setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(map: MapLibreMap, bitmap: Bitmap, satellite: Boolean) {
        this.map = map
        image = bitmap
        this.satellite = satellite
        paint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        cameraChanged()
    }

    fun cameraChanged() {
        val shouldDraw = image != null && (satellite || (map?.cameraPosition?.zoom ?: Double.MAX_VALUE) < OnlineMapAssets.GLOBE_TILE_ZOOM)
        if (shouldDraw || visible) postInvalidateOnAnimation()
        visible = shouldDraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visible || width == 0 || height == 0) return
        val camera = map ?: return
        val bitmap = image ?: return
        if (camera.cameraPosition.zoom >= OnlineMapAssets.GLOBE_TILE_ZOOM) {
            drawSatelliteFallback(canvas, camera, bitmap)
        } else {
            drawSphere(canvas, camera, bitmap)
        }
    }

    private fun drawSphere(canvas: Canvas, camera: MapLibreMap, bitmap: Bitmap) {
        val target = camera.cameraPosition.target ?: return
        val projection = camera.projection
        val origin = projection.toScreenLocation(target)
        val direction = if (target.latitude >= 0) -1 else 1
        val near = projection.toScreenLocation(LatLng(target.latitude + direction * 10.0, target.longitude))
        val far = projection.toScreenLocation(LatLng(target.latitude + direction * 20.0, target.longitude))

        // Recover the sphere's perspective from two points on the central
        // meridian so the bitmap aligns with the live map camera and its labels.
        val nearAngle = PI / 18
        val farAngle = PI / 9
        val nearScale = hypot((near.x - origin.x).toDouble(), (near.y - origin.y).toDouble()) / sin(nearAngle)
        val farScale = hypot((far.x - origin.x).toDouble(), (far.y - origin.y).toDouble()) / sin(farAngle)
        val distance = (nearScale * cos(nearAngle) - farScale * cos(farAngle)) / (nearScale - farScale)
        val focal = nearScale * (distance - cos(nearAngle))
        if (!distance.isFinite() || distance <= 1.0 || !focal.isFinite() || focal <= 0.0) return

        val horizon = acos(1 / distance)
        val latitude = Math.toRadians(target.latitude)
        val longitude = Math.toRadians(target.longitude)
        val sinLatitude = sin(latitude)
        val cosLatitude = cos(latitude)
        for (ring in 0..RINGS) {
            val angle = horizon * ring / RINGS
            val sinAngle = sin(angle)
            val cosAngle = cos(angle)
            val radius = focal * sinAngle / (distance - cosAngle)
            for (slice in 0 until SLICES) {
                val index = ring * SLICES + slice
                val upward = sinAngle * north[slice]
                val sampleLatitude = asin((cosAngle * sinLatitude + upward * cosLatitude).coerceIn(-1.0, 1.0))
                val sampleLongitude = longitude + atan2(sinAngle * east[slice], cosAngle * cosLatitude - upward * sinLatitude)
                x[index] = origin.x + radius * east[slice]
                y[index] = origin.y - radius * north[slice]
                u[index] = .5 + sampleLongitude / TWO_PI
                v[index] = mercatorY(sampleLatitude)
            }
        }
        drawMesh(canvas, bitmap, RINGS, SLICES, wrapAround = true)
    }

    private fun drawSatelliteFallback(canvas: Canvas, camera: MapLibreMap, bitmap: Bitmap) {
        // Use the current native projection even through the globe-to-street
        // transition. Detailed satellite tiles cover this mesh when available.
        val projection = camera.projection
        val point = PointF()
        for (row in 0..FLAT_CELLS) {
            for (column in 0..FLAT_CELLS) {
                val index = row * (FLAT_CELLS + 1) + column
                point.set(width * column.toFloat() / FLAT_CELLS, height * row.toFloat() / FLAT_CELLS)
                val coordinate = projection.fromScreenLocation(point)
                x[index] = point.x.toDouble()
                y[index] = point.y.toDouble()
                u[index] = .5 + coordinate.longitude / 360
                v[index] = mercatorY(Math.toRadians(coordinate.latitude))
            }
        }
        drawMesh(canvas, bitmap, FLAT_CELLS, FLAT_CELLS + 1, wrapAround = false)
    }

    private fun drawMesh(canvas: Canvas, bitmap: Bitmap, rows: Int, columns: Int, wrapAround: Boolean) {
        var offset = 0
        fun vertex(index: Int, wrapReference: Double) {
            vertices[offset] = x[index].toFloat()
            textureCoordinates[offset++] = ((u[index] - round(u[index] - wrapReference)) * bitmap.width).toFloat()
            vertices[offset] = y[index].toFloat()
            textureCoordinates[offset++] = (v[index] * bitmap.height).toFloat()
        }
        fun triangle(a: Int, b: Int, c: Int) {
            val reference = u[a]
            vertex(a, reference)
            vertex(b, reference)
            vertex(c, reference)
        }
        val cells = if (wrapAround) columns else columns - 1
        for (row in 0 until rows) {
            for (column in 0 until cells) {
                val next = (column + 1) % columns
                val a = row * columns + column
                val b = row * columns + next
                val c = (row + 1) * columns + column
                val d = (row + 1) * columns + next
                triangle(a, c, d)
                triangle(a, d, b)
            }
        }
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, offset, vertices, 0, textureCoordinates, 0,
            null, 0, null, 0, 0, paint)
    }

    private fun mercatorY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MERCATOR_LIMIT, MERCATOR_LIMIT)
        return .5 - ln(tan(PI / 4 + clamped / 2)) / TWO_PI
    }

    private companion object {
        const val SLICES = 72
        const val RINGS = 24
        const val FLAT_CELLS = 8
        const val TWO_PI = 2 * PI
        const val MERCATOR_LIMIT = 1.4844222297
    }
}
