package cn.photomap.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
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
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** Draw the bundled globe below MapLibre's labels and photo markers. */
internal class GlobeBackdrop(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class Star(val rx: Float, val ry: Float, val radiusDp: Float, val color: Int, val alpha: Int, val halo: Boolean)
    private val stars = buildList {
        val rng = Random(20261111)
        val palette = intArrayOf(
            Color.rgb(225, 240, 255), // Ice blue-white
            Color.rgb(255, 250, 240), // Warm stellar white
            Color.rgb(176, 218, 255), // Cyan-blue star
            Color.rgb(255, 224, 186), // Soft amber star
            Color.rgb(198, 232, 224)  // Mint-teal star
        )
        repeat(165) { i ->
            val rx = rng.nextFloat()
            val ry = rng.nextFloat()
            val bright = i < 22
            val medium = i in 22 until 60
            val radiusDp = when {
                bright -> 1.15f + rng.nextFloat() * 0.75f
                medium -> 0.72f + rng.nextFloat() * 0.38f
                else -> 0.42f + rng.nextFloat() * 0.28f
            }
            val alpha = when {
                bright -> 185 + rng.nextInt(65)
                medium -> 105 + rng.nextInt(70)
                else -> 48 + rng.nextInt(60)
            }
            add(Star(rx, ry, radiusDp, palette[i % palette.size], alpha, bright && i < 10))
        }
    }
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
    private var dark = false
    private var borders: GlobeBorders? = null
    private var borderColor = 0
    private var visible = false

    init {
        // Canvas.drawVertices is hardware accelerated starting with API 29.
        if (Build.VERSION.SDK_INT < 29) setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(map: MapLibreMap, bitmap: Bitmap, satellite: Boolean, dark: Boolean, borders: GlobeBorders?, borderColor: Int) {
        this.map = map
        image = bitmap
        this.satellite = satellite
        this.dark = dark
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
        val horizonRadius = (focal * sin(horizon) / (distance - cos(horizon))).toFloat()
        drawCosmicBackdrop(canvas, originX.toFloat(), originY.toFloat(), horizonRadius)
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
        drawSphereShading(canvas, originX.toFloat(), originY.toFloat(), horizonRadius)
        if (camera.zoom < OnlineMapAssets.GLOBE_TILE_ZOOM) {
            borders?.draw(canvas, originX, originY, latitude, longitude, distance, focal, camera.zoom, borderColor)
        }
        canvas.restore()
    }

    private fun drawCosmicBackdrop(canvas: Canvas, cx: Float, cy: Float, globeRadius: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        // 1. Deep cosmos gradient background
        spacePaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.rgb(3, 8, 18),
                Color.rgb(6, 15, 30),
                Color.rgb(4, 11, 22)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, spacePaint)

        // 2. Soft galactic nebula / aurora clouds behind the globe
        val maxDim = maxOf(w, h)
        nebulaPaint.shader = RadialGradient(
            w * 0.22f, h * 0.20f, maxDim * 0.65f,
            intArrayOf(Color.argb(46, 36, 118, 168), Color.argb(18, 22, 74, 120), Color.TRANSPARENT),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, nebulaPaint)

        nebulaPaint.shader = RadialGradient(
            w * 0.82f, h * 0.78f, maxDim * 0.60f,
            intArrayOf(Color.argb(40, 24, 128, 112), Color.argb(14, 18, 78, 86), Color.TRANSPARENT),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, nebulaPaint)

        // 3. Multi-layered starfield (drawn outside the globe disc)
        val rSq = globeRadius * globeRadius * 0.96f
        for (star in stars) {
            val sx = star.rx * w
            val sy = star.ry * h
            val dx = sx - cx
            val dy = sy - cy
            if (dx * dx + dy * dy < rSq) continue
            val sr = star.radiusDp * density
            if (star.halo) {
                starPaint.color = star.color
                starPaint.alpha = (star.alpha * 0.22f).toInt()
                canvas.drawCircle(sx, sy, sr * 2.8f, starPaint)
            }
            starPaint.color = star.color
            starPaint.alpha = star.alpha
            canvas.drawCircle(sx, sy, sr, starPaint)
        }

        // 4. Outer atmospheric corona around the Earth
        if (globeRadius > 8f) {
            val outerRadius = globeRadius * 1.24f
            val innerStop = (globeRadius / outerRadius).coerceIn(0.65f, 0.92f)
            val glowPrimary = if (satellite) {
                Color.argb(115, 76, 168, 245)
            } else if (dark) {
                Color.argb(110, 56, 168, 196)
            } else {
                Color.argb(105, 88, 182, 248)
            }
            val glowMid = if (dark && !satellite) {
                Color.argb(42, 32, 118, 150)
            } else {
                Color.argb(40, 48, 128, 210)
            }
            atmospherePaint.shader = RadialGradient(
                cx, cy, outerRadius,
                intArrayOf(glowPrimary, glowPrimary, glowMid, Color.TRANSPARENT),
                floatArrayOf(0f, innerStop, (innerStop + 1f) * 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, outerRadius, atmospherePaint)
        }
    }

    private fun drawSphereShading(canvas: Canvas, cx: Float, cy: Float, globeRadius: Float) {
        if (globeRadius <= 8f) return
        val density = resources.displayMetrics.density
        // 1. 3D spherical lighting: soft sunlit highlight in upper-left + deep limb shadow at outer rim
        val lightX = cx - globeRadius * 0.28f
        val lightY = cy - globeRadius * 0.28f
        val shadeRadius = globeRadius * 1.32f
        val highlightAlpha = if (dark && !satellite) 26 else 18
        val limbShadowAlpha = if (dark && !satellite) 138 else if (satellite) 115 else 72
        shadePaint.shader = RadialGradient(
            lightX, lightY, shadeRadius,
            intArrayOf(
                Color.argb(highlightAlpha, 210, 245, 255),
                Color.TRANSPARENT,
                Color.argb(limbShadowAlpha / 2, 2, 10, 22),
                Color.argb(limbShadowAlpha, 1, 6, 15)
            ),
            floatArrayOf(0f, 0.45f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, globeRadius, shadePaint)

        // 2. Crisp atmospheric horizon rim light
        rimPaint.strokeWidth = 1.35f * density
        rimPaint.color = if (satellite) {
            Color.argb(145, 130, 205, 255)
        } else if (dark) {
            Color.argb(150, 96, 198, 220)
        } else {
            Color.argb(135, 145, 215, 255)
        }
        canvas.drawCircle(cx, cy, globeRadius - 0.5f * density, rimPaint)
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
