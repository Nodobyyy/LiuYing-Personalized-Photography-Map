package cn.photomap.map

import android.content.Context
import org.json.JSONObject
import kotlin.math.*

/** Exact finest-level imagery tile coverage, including edge cells, parks and
 * river banks. The GHSL polygons select data, not administrative boundaries. */
class UrbanCoverage(context: Context) {
    private val data=JSONObject(context.assets.open("maps/urban-coverage.json").bufferedReader().use {it.readText()})
    val count=data.getInt("urbanCentres")
    private val width=1 shl data.getInt("zoom")
    private val tiles=data.getJSONArray("tiles").let {a->(0 until a.length()).map {a.getInt(it)}.toHashSet()}
    fun contains(latitude:Double,longitude:Double):Boolean {
        if(!latitude.isFinite() || !longitude.isFinite() || latitude !in -85.0..85.0 || longitude !in -180.0..180.0)return false
        val x=floor((longitude+180)/360*width).toInt()
        val y=floor((1-asinh(tan(Math.toRadians(latitude)))/Math.PI)/2*width).toInt()
        return y*width+x in tiles
    }
}
