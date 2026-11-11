package cn.photomap.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow

internal object GlobeCamera {
    const val MAX_MERCATOR_LATITUDE = 85.0511287798

    private fun latitudeScale(latitude: Double) =
        cos(Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)))

    // MapLibre measures camera zoom in local Mercator pixels. Equatorial zoom
    // describes the globe's actual size regardless of the latitude in view.
    fun equatorialZoom(zoom: Double, latitude: Double) = zoom - log2(latitudeScale(latitude))

    fun zoomAtLatitude(equatorialZoom: Double, latitude: Double) =
        equatorialZoom + log2(latitudeScale(latitude))

    fun surfaceRadius(equatorialZoom: Double, density: Float) =
        512 * 2.0.pow(equatorialZoom) * density / (2 * PI)
}
