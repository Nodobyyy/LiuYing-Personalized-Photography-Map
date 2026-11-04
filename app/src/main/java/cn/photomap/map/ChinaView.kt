package cn.photomap.map

import android.content.Context
import org.json.JSONObject

class ChinaView(context: Context) {
    val data=JSONObject(context.assets.open("maps/china-view.json").bufferedReader().use {it.readText()})
    private data class Ring(val points: List<Pair<Double,Double>>,val west: Double,val east: Double,val south: Double,val north: Double)
    private val rings=data.getJSONObject("outline").getJSONArray("coordinates").let {polygons->(0 until polygons.length()).map {i->
        val raw=polygons.getJSONArray(i).getJSONArray(0)
        val points=(0 until raw.length()).map {j->raw.getJSONArray(j).let {it.getDouble(0) to it.getDouble(1)}}
        Ring(points,points.minOf {it.first},points.maxOf {it.first},points.minOf {it.second},points.maxOf {it.second})
    }}
    fun contains(latitude: Double,longitude: Double)=rings.any {ring->
        if(longitude !in ring.west..ring.east || latitude !in ring.south..ring.north) false else {
            var inside=false;var previous=ring.points.last()
            for(point in ring.points) {
                if((point.second>latitude)!=(previous.second>latitude) && longitude<(previous.first-point.first)*(latitude-point.second)/(previous.second-point.second)+point.first) inside=!inside
                previous=point
            };inside
        }
    }
}
