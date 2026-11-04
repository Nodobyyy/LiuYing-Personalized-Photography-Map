package cn.photomap.core

import kotlin.math.*

/** Display constraints, separate from political-boundary certification. */
object MapViewport {
    const val WEST=73.0; const val EAST=136.0; const val SOUTH=3.0; const val NORTH=54.0
    fun inCoverage(latitude: Double,longitude: Double)=latitude in SOUTH..NORTH && longitude in WEST..EAST
    private fun y(latitude: Double)=(1-asinh(tan(Math.toRadians(latitude)))/Math.PI)/2
    fun minimumZoom(widthDp: Double,heightDp: Double): Double {
        val xFit=max(1.0,widthDp-28)/(512*(EAST-WEST)/360)
        val yFit=max(1.0,heightDp-28)/(512*(y(SOUTH)-y(NORTH)))
        return log2(min(xFit,yFit)).coerceIn(0.0,17.0)
    }
    fun markerDp(zoom: Double): Double = when {
        zoom<=3->28.0
        zoom<8->28+(zoom-3)*0.8
        zoom<13->32+(zoom-8)*1.6
        zoom<16->40+(zoom-13)*8/3
        else->48.0
    }
    fun clusterRadiusDp(zoom: Double)=markerDp(zoom).let {hypot(it,it*144/128)+8}
}
