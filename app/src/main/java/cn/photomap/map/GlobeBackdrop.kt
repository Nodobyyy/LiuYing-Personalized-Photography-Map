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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
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
    private var borders: GlobeBorders? = null
    private var borderColor = 0
    private var visible = false

    init {
        // Canvas.drawVertices is hardware accelerated starting with API 29.
        if (Build.VERSION.SDK_INT < 29) setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(map: MapLibreMap, bitmap: Bitmap, satellite: Boolean, borders: GlobeBorders?, borderColor: Int) {
        this.map = map
        image = bitmap
        this.satellite = satellite
        this.borders = borders
        this.borderColor = borderColor
        paint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        cameraChanged()
    }

    fun cameraChanged() {
        // Keep the atlas behind the native tiles during the handoff. The two
        // views can present adjacent camera frames during a pinch or reset.
        val shouldDraw = image != null &&
            (satellite || (map?.cameraPosition?.zoom ?: Double.MAX_VALUE) < OnlineMapAssets.GLOBE_TILE_ZOOM + .25)
        if (shouldDraw || visible) postInvalidateOnAnimation()
        visible = shouldDraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visible || width == 0 || height == 0) return
        val camera = map ?: return
        val bitmap = image ?: return
        val position = camera.cameraPosition
        if (satellite && position.zoom >= OnlineMapAssets.GLOBE_TILE_ZOOM) {
            drawFlatFallback(canvas, camera, bitmap)
        } else {
            drawSphere(canvas, position, bitmap)
        }
    }

    private fun drawSphere(canvas: Canvas, camera: CameraPosition, bitmap: Bitmap) {
        val target = camera.target?.wrap() ?: return
        val padding = camera.padding
        val originX = (width + (padding?.get(0) ?: 0.0) - (padding?.get(2) ?: 0.0)) / 2
        val originY = (height + (padding?.get(1) ?: 0.0) - (padding?.get(3) ?: 0.0)) / 2
        // MapLibre's default vertical field of view is 2 * atan(1 / 3).
        // Compute the entire sphere from one camera snapshot. Reading several
        // projected points during a drag can mix transforms from different frames.
        val focal = height * 1.5
        val zoom = GlobeCamera.equatorialZoom(camera.zoom, target.latitude)
        val distance = 1 + focal / GlobeCamera.surfaceRadius(zoom, resources.displayMetrics.density)
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
                x[index] = originX + radius * east[slice]
                y[index] = originY - radius * north[slice]
                u[index] = .5 + sampleLongitude / TWO_PI
                v[index] = mercatorY(sampleLatitude)
            }
        }
        canvas.save()
        canvas.rotate(-camera.bearing.toFloat(), originX.toFloat(), originY.toFloat())
        drawMesh(canvas, bitmap, RINGS, SLICES, wrapAround = true)
        if (camera.zoom < OnlineMapAssets.GLOBE_TILE_ZOOM) {
            borders?.draw(canvas, originX, originY, latitude, longitude, distance, focal, camera.zoom, borderColor)
        }
        canvas.restore()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cameraChanged()
    }

    private fun drawFlatFallback(canvas: Canvas, camera: MapLibreMap, bitmap: Bitmap) {
        // Use the current native projection even through the globe-to-street
        // transition. Native tiles cover this mesh when available.
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
