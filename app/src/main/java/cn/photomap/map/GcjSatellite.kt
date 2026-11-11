package cn.photomap.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Domestic (VPN-free) satellite imagery for mainland China.
 *
 * AutoNavi imagery is published in the GCJ-02 datum, which is shifted by up to ~700 m
 * from the WGS-84 coordinates used by photos, EXIF GPS and the rest of the map. Each
 * requested WGS-84 Web-Mercator tile is rebuilt on-device: the GCJ-02 offset is computed
 * at the tile centre (it is effectively constant within one tile), the 1-4 covering
 * AutoNavi tiles are fetched and the shifted 256 px window is cropped out.
 */
internal object GcjSatellite {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    data class Tile(val z: Int, val x: Int, val y: Int)

    private fun outOfChina(lat: Double, lon: Double) = lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    /** Only tiles whose centre lies inside China use the domestic source. */
    fun covers(z: Int, x: Int, y: Int, china: ChinaView? = null): Boolean {
        val (lat, lon) = center(z, x, y)
        return if (china != null) china.contains(lat, lon) else !outOfChina(lat, lon)
    }

    /**
     * AutoNavi returns a 4235-byte PNG watermark ("此区域无卫星图") for out-of-coverage or
     * over-zoomed tiles, whereas real satellite imagery is always a JPEG (starts with FF D8 FF).
     */
    fun isValidImagery(bytes: ByteArray?): Boolean =
        bytes != null && bytes.size > 512 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    private fun center(z: Int, x: Int, y: Int): Pair<Double, Double> {
        val n = (1 shl z).toDouble()
        val lon = (x + 0.5) / n * 360.0 - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (y + 0.5) / n))))
        return lat to lon
    }

    fun wgsToGcj(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return lat to lon
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = dLon * 180.0 / (A / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lon + dLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        r += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return r
    }

    private fun transformLon(x: Double, y: Double): Double {
        var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        r += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return r
    }

    private fun pixelX(lon: Double, world: Double) = (lon + 180.0) / 360.0 * world
    private fun pixelY(lat: Double, world: Double): Double {
        val r = Math.toRadians(lat)
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * world
    }

    /**
     * Builds the corrected WGS-84 tile. [fetch] downloads the listed GCJ-02 tiles
     * (in order) and returns null when any of them is unavailable.
     */
    fun build(z: Int, x: Int, y: Int, fetch: (List<Tile>) -> List<ByteArray>?): ByteArray? {
        val n = 1 shl z
        val world = n * 256.0
        val (lat, lon) = center(z, x, y)
        val (gLat, gLon) = wgsToGcj(lat, lon)
        val dx = pixelX(gLon, world) - pixelX(lon, world)
        val dy = pixelY(gLat, world) - pixelY(lat, world)
        val left = x * 256.0 + dx
        val top = y * 256.0 + dy
        val tx0 = floor(left / 256).toInt()
        val ty0 = floor(top / 256).toInt()
        val ox = left - tx0 * 256
        val oy = top - ty0 * 256
        val columns = if (ox > 0.5) 2 else 1
        val rows = if (oy > 0.5) 2 else 1
        val tiles = buildList {
            for (j in 0 until rows) for (i in 0 until columns) {
                val ty = ty0 + j
                if (ty !in 0 until n) return null
                add(Tile(z, Math.floorMod(tx0 + i, n), ty))
            }
        }
        val sources = fetch(tiles) ?: return null
        val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            sources.forEachIndexed { index, bytes ->
                val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                try {
                    val i = index % columns
                    val j = index / columns
                    canvas.drawBitmap(image, (i * 256 - ox).toFloat(), (j * 256 - oy).toFloat(), paint)
                } finally {
                    image.recycle()
                }
            }
            val stream = ByteArrayOutputStream(48 * 1024)
            output.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            return stream.toByteArray()
        } finally {
            output.recycle()
        }
    }
}
