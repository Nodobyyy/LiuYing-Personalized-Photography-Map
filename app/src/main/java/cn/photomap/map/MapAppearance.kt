package cn.photomap.map

import org.json.JSONArray
import org.json.JSONObject

enum class BasemapMode(val title: String,val description: String) {
    SIMPLE("简洁底图","突出照片，保留主要道路、水域和地名"),
    STREETS("街道底图","显示道路、建筑、景点和生活设施"),
    SATELLITE("真实卫星图","离线 Sentinel-2 2016 影像；全国城区约 10 米，城区外为概览")
}

/** Also repairs previously imported 0.2.0 styles, without modifying their files. */
object MapAppearance {
    fun apply(json: JSONObject,mode: BasemapMode,satellite: JSONObject?,context: JSONObject?=null) {
        val original=json.getJSONArray("layers")
        val result=JSONArray()
        if(mode!=BasemapMode.SATELLITE && context!=null) {
            val sources=context.getJSONObject("sources");sources.keys().forEach {json.getJSONObject("sources").put(it,sources.get(it))}
            val layers=context.getJSONArray("layers");for(i in 0 until layers.length())result.put(layers.getJSONObject(i))
        }
        for(i in 0 until original.length()) {
            val layer=original.getJSONObject(i)
            if(context!=null && mode!=BasemapMode.SATELLITE && layer.optString("type")=="background") continue
            // Only polygon geometry may be filled. Open canal/river lines were
            // otherwise closed into large false water polygons by the renderer.
            if(layer.optString("type")=="fill" && layer.optString("source-layer")=="water") {
                val polygon=JSONArray("""["==",["geometry-type"],"Polygon"]""")
                val filter=layer.optJSONArray("filter")
                layer.put("filter",if(filter==null) polygon else JSONArray().put("all").put(ChinaMapLabels.normalize(filter)).put(polygon))
                layer.getJSONObject("paint").put("fill-color","#a9d4e9")
            }
            if(mode==BasemapMode.SATELLITE && layer.optString("type") in listOf("fill","background")) {
                if(layer.optString("type")=="background") result.put(JSONObject("""{"id":"background","type":"background","paint":{"background-color":"#19303d"}}"""))
                continue
            }
            val basicId=layer.optString("id").removePrefix("urban-").removePrefix("embedded-")
            if(mode==BasemapMode.SIMPLE && (basicId in listOf("poi-labels","buildings","landcover","landuse","railways","locality-labels"))) continue
            if(mode==BasemapMode.SIMPLE && basicId=="roads") continue
            if(mode==BasemapMode.SIMPLE && basicId=="road-labels") {
                val major=if(layer.optString("source-layer")=="transportation_name") JSONArray("""["in",["get","class"],["literal",["motorway","trunk","primary","secondary"]]]""") else JSONArray("""["in",["get","kind"],["literal",["highway","major_road"]]]""")
                layer.put("filter",JSONArray().put("all").put(ChinaMapLabels.normalize(layer.getJSONArray("filter"))).put(major))
            }
            if(mode==BasemapMode.SATELLITE) {
                val paint=layer.optJSONObject("paint")
                if(layer.optString("type")=="symbol") {
                    paint?.put("text-color","#ffffff")?.put("text-halo-color","#172b32")?.put("text-halo-width",1.8)
                } else if(layer.optString("source-layer") in listOf("roads","transportation","transportation_name")) {
                    paint?.put("line-opacity",0.45)?.put("line-color","#f2ead2")
                }
            }
            result.put(layer)
        }
        if(mode==BasemapMode.SATELLITE && satellite!=null) {
            val sources=json.getJSONObject("sources");val extra=satellite.getJSONObject("sources")
            extra.keys().forEach {sources.put(it,extra.get(it))}
            val layers=satellite.getJSONArray("layers");val combined=JSONArray()
            for(i in 0 until result.length()) if(result.getJSONObject(i).optString("type")=="background") combined.put(result.getJSONObject(i))
            for(i in 0 until layers.length()) combined.put(layers.getJSONObject(i))
            for(i in 0 until result.length()) if(result.getJSONObject(i).optString("type")!="background") combined.put(result.getJSONObject(i))
            json.put("layers",combined)
        } else json.put("layers",result)
    }
}
