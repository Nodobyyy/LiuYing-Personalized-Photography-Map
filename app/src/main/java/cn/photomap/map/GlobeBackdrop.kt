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
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** Draw the bundled globe below MapLibre's labels and photo markers. */
internal class GlobeBackdrop(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val terminatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private data class Star(
        val rx: Float,
        val ry: Float,
        val radiusDp: Float,
        val color: Int,
        val minAlpha: Int,
        val maxAlpha: Int,
        val speed: Float,
        val phase: Float,
        val halo: Boolean
    )

    private val stars = buildList {
        val rng = Random(20261111)
        val palette = intArrayOf(
            Color.rgb(228, 242, 255), // Ice blue-white
            Color.rgb(255, 250, 240), // Warm stellar white
            Color.rgb(172, 218, 255), // Cyan-blue star
            Color.rgb(255, 224, 184), // Soft amber star
            Color.rgb(196, 236, 226)  // Mint-teal star
        )
        repeat(175) { i ->
            val rx = rng.nextFloat()
            val ry = rng.nextFloat()
            val bright = i < 26
            val medium = i in 26 until 70
            val radiusDp = when {
                bright -> 1.15f + rng.nextFloat() * 0.72f
                medium -> 0.74f + rng.nextFloat() * 0.38f
                else -> 0.44f + rng.nextFloat() * 0.28f
            }
            val minAlpha = when {
                bright -> 45 + rng.nextInt(45)
                medium -> 25 + rng.nextInt(35)
                else -> 12 + rng.nextInt(25)
            }
            val maxAlpha = when {
                bright -> 220 + rng.nextInt(35)
                medium -> 145 + rng.nextInt(70)
                else -> 80 + rng.nextInt(65)
            }
            // Random twinkle frequency (1.4 to 4.6 rad/s) and phase so stars shimmer organically
            val speed = 1.4f + rng.nextFloat() * 3.2f
            val phase = rng.nextFloat() * (2f * PI.toFloat())
            add(Star(rx, ry, radiusDp, palette[i % palette.size], minAlpha, maxAlpha, speed, phase, bright && i < 14))
        }
    }

    private val vertices = FloatArray(RINGS * SLICES * 12)
    private val textureCoordinates = FloatArray(vertices.size)
    private val vertexColors = IntArray(RINGS * SLICES * 6)
    private val x = DoubleArray((RINGS + 1) * SLICES)
    private val y = DoubleArray(x.size)
    private val u = DoubleArray(x.size)
    private val v = DoubleArray(x.size)
    private val nodeColor = IntArray(x.size)
    private val east = DoubleArray(SLICES) { cos(it * TWO_PI / SLICES) }
    private val north = DoubleArray(SLICES) { sin(it * TWO_PI / SLICES) }
    private var map: MapLibreMap? = null
    private var image: Bitmap? = null
    private var satellite = false
    private var dark = false
    private var solarTerminator = true
    private var borders: GlobeBorders? = null
    private var borderColor = 0
    private var visible = false

    // Cached shaders to avoid per-frame allocations in onDraw
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedAtmosCx = Float.NaN
    private var cachedAtmosCy = Float.NaN
    private var cachedAtmosR = Float.NaN
    private var cachedAtmosMode = -1

    private val twinkleTick = object : Runnable {
        override fun run() {
            if (visible && isAttachedToWindow && windowVisibility == VISIBLE) {
                val zoom = map?.cameraPosition?.zoom ?: Double.MAX_VALUE
                if (zoom < OnlineMapAssets.GLOBE_TILE_ZOOM) {
                    postInvalidateOnAnimation()
                    postDelayed(this, 55L) // ~18 fps smooth low-power starlight shimmer
                }
            }
        }
    }

    init {
        // Canvas.drawVertices is hardware accelerated starting with API 29.
        if (Build.VERSION.SDK_INT < 29) setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(
        map: MapLibreMap,
        bitmap: Bitmap,
        satellite: Boolean,
        dark: Boolean,
        solarTerminator: Boolean,
        borders: GlobeBorders?,
        borderColor: Int
    ) {
        this.map = map
        image = bitmap
        this.satellite = satellite
        this.dark = dark
        this.solarTerminator = solarTerminator
        this.borders = borders
        this.borderColor = borderColor
        cachedAtmosMode = -1
        paint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        cameraChanged()
    }

    fun setSolarTerminator(enabled: Boolean) {
        if (solarTerminator != enabled) {
            solarTerminator = enabled
            postInvalidateOnAnimation()
        }
    }

    fun cameraChanged() {
        // Keep the atlas behind the native tiles during the handoff. The two
        // views can present adjacent camera frames during a pinch or reset.
        val zoom = map?.cameraPosition?.zoom ?: Double.MAX_VALUE
        val shouldDraw = image != null &&
            (satellite || zoom < OnlineMapAssets.GLOBE_TILE_ZOOM + .25)
        if (shouldDraw || visible) postInvalidateOnAnimation()
        visible = shouldDraw
        removeCallbacks(twinkleTick)
        if (shouldDraw && zoom < OnlineMapAssets.GLOBE_TILE_ZOOM) {
            postDelayed(twinkleTick, 55L)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(twinkleTick)
        super.onDetachedFromWindow()
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

    /**
     * Computes the real-time subsolar point (latitude & longitude in radians where the sun is
     * directly overhead right now) from current UTC epoch time.
     */
    private fun subsolarPointRadians(nowMillis: Long = System.currentTimeMillis()): Pair<Double, Double> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMillis }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val utcHours = cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60.0 +
            cal.get(Calendar.SECOND) / 3600.0
        val gamma = TWO_PI / 365.0 * (dayOfYear - 1 + (utcHours - 12.0) / 24.0)
        // Equation of time (minutes) and solar declination (radians, Spencer 1971)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val sunDeclination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        var sunLonDeg = (12.0 - utcHours) * 15.0 - eqTime / 4.0
        sunLonDeg = ((sunLonDeg + 540.0) % 360.0) - 180.0
        return sunDeclination to Math.toRadians(sunLonDeg)
    }

    private fun drawSphere(canvas: Canvas, camera: CameraPosition, bitmap: Bitmap) {
        val target = camera.target?.wrap() ?: return
        val padding = camera.padding
        val originX = (width + (padding?.get(0) ?: 0.0) - (padding?.get(2) ?: 0.0)) / 2
        val originY = (height + (padding?.get(1) ?: 0.0) - (padding?.get(3) ?: 0.0)) / 2
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

        val (sunLat, sunLon) = if (solarTerminator) subsolarPointRadians() else (0.0 to 0.0)
        val sinSunLat = sin(sunLat)
        val cosSunLat = cos(sunLat)

        for (ring in 0..RINGS) {
            val angle = horizon * ring / RINGS
            val sinAngle = sin(angle)
            val cosAngle = cos(angle)
            val radius = focal * sinAngle / (distance - cosAngle)
            for (slice in 0 until SLICES) {
                val index = ring * SLICES + slice
                val upward = sinAngle * north[slice]
                val sinSampleLat = (cosAngle * sinLatitude + upward * cosLatitude).coerceIn(-1.0, 1.0)
                val sampleLatitude = asin(sinSampleLat)
                val cosSampleLat = cos(sampleLatitude)
                val sampleLongitude = longitude + atan2(sinAngle * east[slice], cosAngle * cosLatitude - upward * sinLatitude)
                x[index] = originX + radius * east[slice]
                y[index] = originY - radius * north[slice]
                u[index] = .5 + sampleLongitude / TWO_PI
                v[index] = mercatorY(sampleLatitude)

                if (solarTerminator) {
                    // Cosine of solar zenith angle: > 0 is sunlit day, < 0 is night, [-0.15..0.15] is twilight zone
                    val cosZenith = sinSampleLat * sinSunLat + cosSampleLat * cosSunLat * cos(sampleLongitude - sunLon)
                    // Smoothstep across the twilight band (-9° to +9° solar elevation)
                    val t = ((cosZenith + 0.14) / 0.30).coerceIn(0.0, 1.0)
                    val smoothDay = t * t * (3.0 - 2.0 * t)
                    // Deep astronomical night shadow while keeping continents & coastlines recognizable
                    val minBrightness = if (satellite) 0.12 else if (dark) 0.22 else 0.16
                    val lum = minBrightness + (1.0 - minBrightness) * smoothDay
                    // Warm golden-amber atmospheric refraction along the dawn/dusk terminator line
                    val twilightGlow = (1.0 - kotlin.math.abs(t - 0.5) * 2.0).coerceIn(0.0, 1.0).let { it * it }
                    val r = ((lum * 255.0) + twilightGlow * 26.0).toInt().coerceIn(0, 255)
                    val g = ((lum * 250.0) + twilightGlow * 8.0).toInt().coerceIn(0, 255)
                    val b = ((lum * 255.0) + (1.0 - smoothDay) * 28.0).toInt().coerceIn(0, 255)
                    nodeColor[index] = Color.rgb(r, g, b)
                } else {
                    nodeColor[index] = Color.WHITE
                }
            }
        }
        canvas.save()
        canvas.rotate(-camera.bearing.toFloat(), originX.toFloat(), originY.toFloat())
        drawMesh(canvas, bitmap, RINGS, SLICES, wrapAround = true, useVertexColors = solarTerminator)
        drawSphereShading(canvas, originX.toFloat(), originY.toFloat(), horizonRadius)
        if (camera.zoom < OnlineMapAssets.GLOBE_TILE_ZOOM) {
            borders?.draw(canvas, originX, originY, latitude, longitude, distance, focal, camera.zoom, borderColor)
        }
        canvas.restore()
    }

    private fun ensureBackdropShaders(w: Float, h: Float) {
        if (cachedWidth == width && cachedHeight == height) return
        cachedWidth = width
        cachedHeight = height
        spacePaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.rgb(2, 6, 15),
                Color.rgb(5, 13, 27),
                Color.rgb(3, 9, 19)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        val maxDim = maxOf(w, h)
        nebulaPaint1.shader = RadialGradient(
            w * 0.22f, h * 0.20f, maxDim * 0.62f,
            intArrayOf(Color.argb(42, 34, 114, 165), Color.argb(16, 20, 70, 115), Color.TRANSPARENT),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        nebulaPaint2.shader = RadialGradient(
            w * 0.82f, h * 0.78f, maxDim * 0.58f,
            intArrayOf(Color.argb(36, 22, 122, 108), Color.argb(12, 16, 72, 82), Color.TRANSPARENT),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawCosmicBackdrop(canvas: Canvas, cx: Float, cy: Float, globeRadius: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        ensureBackdropShaders(w, h)

        // 1. Deep cosmos gradient + nebula clouds
        canvas.drawRect(0f, 0f, w, h, spacePaint)
        canvas.drawRect(0f, 0f, w, h, nebulaPaint1)
        canvas.drawRect(0f, 0f, w, h, nebulaPaint2)

        // 2. Twinkling starfield (animated smoothly with per-star frequency and phase)
        val timeSec = (System.currentTimeMillis() % 3600000L) / 1000f
        val rSq = globeRadius * globeRadius * 0.97f
        spikePaint.strokeWidth = 0.85f * density
        for (star in stars) {
            val sx = star.rx * w
            val sy = star.ry * h
            val dx = sx - cx
            val dy = sy - cy
            if (dx * dx + dy * dy < rSq) continue
            val wave = 0.5f + 0.5f * sin(timeSec * star.speed + star.phase)
            // Sharpen the twinkle peak so stars noticeably sparkle
            val pulse = wave * wave
            val alpha = (star.minAlpha + ((star.maxAlpha - star.minAlpha) * pulse).toInt()).coerceIn(0, 255)
            val sr = star.radiusDp * density * (0.82f + 0.32f * pulse)
            if (star.halo) {
                starPaint.color = star.color
                starPaint.alpha = (alpha * 0.26f).toInt()
                canvas.drawCircle(sx, sy, sr * 2.6f, starPaint)
                if (pulse > 0.65f) {
                    spikePaint.color = star.color
                    spikePaint.alpha = ((pulse - 0.65f) / 0.35f * 145f).toInt().coerceIn(0, 145)
                    val len = sr * 3.4f
                    canvas.drawLine(sx - len, sy, sx + len, sy, spikePaint)
                    canvas.drawLine(sx, sy - len, sx, sy + len, spikePaint)
                }
            }
            starPaint.color = star.color
            starPaint.alpha = alpha
            canvas.drawCircle(sx, sy, sr, starPaint)
        }

        // 3. Compact, refined atmospheric corona around the Earth (reduced from 1.24x to 1.095x)
        if (globeRadius > 8f) {
            val outerRadius = globeRadius * 1.095f
            val modeKey = (if (satellite) 4 else 0) + (if (dark) 2 else 0)
            if (cachedAtmosCx != cx || cachedAtmosCy != cy || cachedAtmosR != outerRadius || cachedAtmosMode != modeKey) {
                cachedAtmosCx = cx
                cachedAtmosCy = cy
                cachedAtmosR = outerRadius
                cachedAtmosMode = modeKey
                val innerStop = (globeRadius / outerRadius).coerceIn(0.82f, 0.95f)
                val glowPrimary = if (satellite) {
                    Color.argb(92, 76, 168, 245)
                } else if (dark) {
                    Color.argb(86, 56, 168, 196)
                } else {
                    Color.argb(82, 88, 182, 248)
                }
                val glowMid = if (dark && !satellite) {
                    Color.argb(26, 32, 118, 150)
                } else {
                    Color.argb(24, 48, 128, 210)
                }
                atmospherePaint.shader = RadialGradient(
                    cx, cy, outerRadius,
                    intArrayOf(glowPrimary, glowPrimary, glowMid, Color.TRANSPARENT),
                    floatArrayOf(0f, innerStop, (innerStop + 1f) * 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, outerRadius, atmospherePaint)
        }
    }

    private fun drawSphereShading(canvas: Canvas, cx: Float, cy: Float, globeRadius: Float) {
        if (globeRadius <= 8f) return
        val density = resources.displayMetrics.density
        // Subtle limb darkening at the outer edge of the sphere + crisp horizon rim
        val limbAlpha = if (solarTerminator) 75 else if (dark && !satellite) 125 else if (satellite) 105 else 65
        shadePaint.shader = RadialGradient(
            cx, cy, globeRadius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(limbAlpha, 1, 6, 15)
            ),
            floatArrayOf(0f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, globeRadius, shadePaint)

        rimPaint.strokeWidth = 1.15f * density
        rimPaint.color = if (satellite) {
            Color.argb(128, 130, 205, 255)
        } else if (dark) {
            Color.argb(132, 96, 198, 220)
        } else {
            Color.argb(118, 145, 215, 255)
        }
        canvas.drawCircle(cx, cy, globeRadius - 0.5f * density, rimPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cameraChanged()
    }

    private fun drawFlatFallback(canvas: Canvas, camera: MapLibreMap, bitmap: Bitmap) {
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
        drawMesh(canvas, bitmap, FLAT_CELLS, FLAT_CELLS + 1, wrapAround = false, useVertexColors = false)
    }

    private fun drawMesh(
        canvas: Canvas,
        bitmap: Bitmap,
        rows: Int,
        columns: Int,
        wrapAround: Boolean,
        useVertexColors: Boolean
    ) {
        var offset = 0
        var colorOffset = 0
        fun vertex(index: Int, wrapReference: Double) {
            vertices[offset] = x[index].toFloat()
            textureCoordinates[offset++] = ((u[index] - round(u[index] - wrapReference)) * bitmap.width).toFloat()
            vertices[offset] = y[index].toFloat()
            textureCoordinates[offset++] = (v[index] * bitmap.height).toFloat()
            if (useVertexColors) {
                vertexColors[colorOffset++] = nodeColor[index]
            }
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
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            offset,
            vertices,
            0,
            textureCoordinates,
            0,
            if (useVertexColors) vertexColors else null,
            0,
            null,
            0,
            0,
            paint
        )
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
