package cn.photomap.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/** The same boundary coordinates used by the native map, projected on the globe. */
internal class GlobeBorders(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val country = mutableListOf<DoubleArray>()
    private val uncertain = mutableListOf<DoubleArray>()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val dashed = DashPathEffect(floatArrayOf(2.5f * density, 1.7f * density), 0f)

    init {
        val json = context.assets.open("maps/world-boundaries.geojson").bufferedReader().use { JSONObject(it.readText()) }
        val features = json.getJSONArray("features")
        for (index in 0 until features.length()) {
            val feature = features.getJSONObject(index)
            val target = when (feature.getJSONObject("properties").getString("kind")) {
                "country" -> country
                "uncertain" -> uncertain
                else -> continue
            }
            val lines = feature.getJSONObject("geometry").getJSONArray("coordinates")
            for (lineIndex in 0 until lines.length()) {
                val line = lines.getJSONArray(lineIndex)
                val vertices = DoubleArray(line.length() * 3)
                for (pointIndex in 0 until line.length()) {
                    val point = line.getJSONArray(pointIndex)
                    val latitude = Math.toRadians(point.getDouble(1))
                    val longitude = Math.toRadians(point.getDouble(0))
                    vertices[pointIndex * 3] = cos(latitude) * cos(longitude)
                    vertices[pointIndex * 3 + 1] = cos(latitude) * sin(longitude)
                    vertices[pointIndex * 3 + 2] = sin(latitude)
                }
                target.add(vertices)
            }
        }
    }

    fun draw(canvas: Canvas, centerX: Double, centerY: Double, latitude: Double, longitude: Double,
             distance: Double, focal: Double, zoom: Double, color: Int) {
        val sinLatitude = sin(latitude)
        val cosLatitude = cos(latitude)
        val sinLongitude = sin(longitude)
        val cosLongitude = cos(longitude)
        val horizon = 1 / distance
        paint.color = color

        fun drawLines(lines: List<DoubleArray>, width: Float, opacity: Int, dash: DashPathEffect?) {
            path.rewind()
            for (line in lines) {
                var continuing = false
                for (index in line.indices step 3) {
                    val x = line[index]
                    val y = line[index + 1]
                    val z = line[index + 2]
                    val forward = x * cosLongitude + y * sinLongitude
                    val depth = forward * cosLatitude + z * sinLatitude
                    if (depth < horizon) {
                        continuing = false
                        continue
                    }
                    val scale = focal / (distance - depth)
                    val px = (centerX + (-x * sinLongitude + y * cosLongitude) * scale).toFloat()
                    val py = (centerY - (z * cosLatitude - forward * sinLatitude) * scale).toFloat()
                    if (continuing) path.lineTo(px, py) else path.moveTo(px, py)
                    continuing = true
                }
            }
            paint.strokeWidth = (width + .35f * ((zoom - 1) / 6).coerceIn(0.0, 1.0).toFloat()) * density
            paint.alpha = opacity
            paint.pathEffect = dash
            canvas.drawPath(path, paint)
        }

        drawLines(country, .9f, 209, null)
        drawLines(uncertain, .75f, 166, dashed)
    }
}
