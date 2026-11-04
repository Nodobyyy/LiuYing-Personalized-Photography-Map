package cn.photomap.map

import org.json.JSONArray
import org.json.JSONObject

/** Apply to the built-in style and imported styles, independently of upstream classifications. */
object ChinaMapLabels {
    fun apply(style: JSONObject,china: ChinaView, global: Boolean = false) {
        val taiwan = JSONArray("""["any",["in",["get","name"],["literal",["Taiwan","台湾","臺灣","台灣","台湾省","臺灣省"]]],["in",["get","name:en"],["literal",["Taiwan","Republic of China"]]],["in",["get","name:zh-Hans"],["literal",["台湾","台湾省"]]],["in",["get","name:zh-Hant"],["literal",["臺灣","台灣","臺灣省"]]],["all",["any",["==",["get","kind"],"country"],["==",["get","class"],"country"]],["any",["in",["get","country_code"],["literal",["TW","HK","MO"]]],["in",["get","iso_a2"],["literal",["TW","HK","MO"]]]]]]""")
        val layers=style.getJSONArray("layers")
        for(i in 0 until layers.length()) {
            val layer=layers.getJSONObject(i)
            if(layer.optString("type")=="symbol" && layer.optJSONObject("layout")?.has("text-field")==true && layer.optString("id")!="china-taiwan-province") {
                // Normalize legacy filters before combining with expression syntax.
                val filter=layer.optJSONArray("filter")?.let {normalize(it)}
                val combined=JSONArray().put("all")
                if(filter!=null) combined.put(filter)
                combined.put(JSONArray().put("!").put(taiwan))
                if(!global && layer.optJSONObject("layout")?.optString("symbol-placement")!="line" && layer.optString("id")!="context-country-labels") combined.put(JSONArray().put("within").put(china.data.getJSONObject("labelOutline")))
                layer.put("filter",combined)
            }
        }
        style.getJSONObject("sources").put("china-province-label",JSONObject().put("type","geojson").put("data",china.data.getJSONObject("provinces")))
        val province=JSONObject("""{"id":"region-labels","type":"symbol","source":"china-province-label","minzoom":3.25,"maxzoom":8,"filter":["!=",["get","name"],"台湾省"],"layout":{"text-field":["get","name"],"text-font":["Noto Sans Regular"],"text-size":13,"text-padding":4},"paint":{"text-color":"#566156","text-halo-color":"#f4f2ec","text-halo-width":1.3}}""")
        val taiwanProvince=JSONObject(province.toString()).put("id","china-taiwan-province").put("filter",JSONArray("""["==",["get","name"],"台湾省"]"""))
        if (global) {
            val globalLayers = JSONArray()
            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                if (layer.optString("source-layer") !in listOf("boundary", "boundaries") && layer.optString("id") != "region-labels") {
                    globalLayers.put(layer)
                }
            }
            style.put("layers", globalLayers.put(province).put(taiwanProvince))
            return
        }
        style.getJSONObject("sources").put("china-display-mask",JSONObject().put("type","geojson").put("data",JSONObject().put("type","Feature").put("properties",JSONObject()).put("geometry",china.data.getJSONObject("outside"))))
        style.getJSONObject("sources").put("china-reference-outline",JSONObject().put("type","geojson").put("data",JSONObject().put("type","Feature").put("properties",JSONObject()).put("geometry",china.data.getJSONObject("outline"))))
        style.getJSONObject("sources").put("china-reference-provinces",JSONObject().put("type","geojson").put("data",china.data.getJSONObject("provinceBoundaries")))
        style.getJSONObject("sources").put("china-reference-lines",JSONObject().put("type","geojson").put("data",china.data.getJSONObject("boundaryLines")))
        style.getJSONObject("sources").put("china-sea-label",JSONObject("""{"type":"geojson","data":{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[114.5,12.5]}}}"""))
        // Line labels render before the mask; point labels use the cheap point-in-polygon filter.
        val reordered=JSONArray()
        for(i in 0 until layers.length()) if(layers.getJSONObject(i).optString("type")!="symbol") reordered.put(layers.getJSONObject(i))
        for(i in 0 until layers.length()) {val layer=layers.getJSONObject(i);if(layer.optString("type")=="symbol" && layer.optJSONObject("layout")?.optString("symbol-placement")=="line") reordered.put(layer)}
        for(i in 0 until layers.length()) {val layer=layers.getJSONObject(i);if(layer.optString("type")=="symbol" && layer.optJSONObject("layout")?.optString("symbol-placement")!="line") reordered.put(layer)}
        val ordered=JSONArray();var inserted=false;var masked=false
        for(i in 0 until reordered.length()) {
            val layer=reordered.getJSONObject(i)
            if(layer.optString("id")=="region-labels") continue
            if(!masked && layer.optString("type")=="symbol" && layer.optJSONObject("layout")?.optString("symbol-placement")!="line") {
                ordered.put(JSONObject("""{"id":"china-display-mask","type":"fill","source":"china-display-mask","paint":{"fill-color":"#edf0e8","fill-opacity":0.25,"fill-antialias":false}}"""));masked=true
                ordered.put(JSONObject("""{"id":"china-province-boundaries","type":"line","source":"china-reference-provinces","minzoom":3.25,"maxzoom":12,"paint":{"line-color":"#8a978b","line-width":0.8}}"""))
                ordered.put(JSONObject("""{"id":"china-reference-outline","type":"line","source":"china-reference-outline","maxzoom":10,"paint":{"line-color":"#7b897e","line-width":1}}"""))
                ordered.put(JSONObject("""{"id":"china-reference-lines","type":"line","source":"china-reference-lines","maxzoom":10,"paint":{"line-color":"#7b897e","line-width":1.2}}"""))
                ordered.put(JSONObject("""{"id":"china-sea-label","type":"symbol","source":"china-sea-label","minzoom":1,"maxzoom":7,"layout":{"text-field":"南海诸岛","text-font":["Noto Sans Regular"],"text-size":12,"text-padding":5},"paint":{"text-color":"#668491","text-halo-color":"#cbdde3","text-halo-width":1}}"""))
            }
            if(!inserted && layer.optString("id")=="place-labels") {ordered.put(province);ordered.put(taiwanProvince);inserted=true}
            ordered.put(layer)
        }
        if(!inserted) {ordered.put(province);ordered.put(taiwanProvince)}
        if(!masked) ordered.put(JSONObject("""{"id":"china-display-mask","type":"fill","source":"china-display-mask","paint":{"fill-color":"#edf0e8","fill-opacity":0.25,"fill-antialias":false}}"""))
        style.put("layers",ordered)
    }
    internal fun normalize(filter: JSONArray): JSONArray {
        val op=filter.optString(0)
        if(op in listOf("all","any","none")) {
            val result=JSONArray().put(if(op=="none") "any" else op)
            for(i in 1 until filter.length()) result.put(normalize(filter.getJSONArray(i)))
            return if(op=="none") JSONArray().put("!").put(result) else result
        }
        if(filter.opt(1) is String) {
            val key=filter.getString(1)
            val get=JSONArray().put(when(key) {"\$type"->"geometry-type";"\$id"->"id";else->"get"}).apply {if(key !in listOf("\$type","\$id")) put(key)}
            if(op=="has" || op=="!has") {
                val has=JSONArray().put("has").put(key)
                return if(op=="!has") JSONArray().put("!").put(has) else has
            }
            if(op=="in" || op=="!in") {
                if((2 until filter.length()).any {filter.opt(it) is JSONArray || filter.opt(it) is JSONObject}) return filter
                val values=JSONArray();for(i in 2 until filter.length()) values.put(filter.get(i))
                val contains=JSONArray().put("in").put(get).put(JSONArray().put("literal").put(values))
                return if(op=="!in") JSONArray().put("!").put(contains) else contains
            }
            if(op in listOf("==","!=",">",">=","<","<=")) return JSONArray().put(op).put(get).put(filter.get(2))
        }
        return filter
    }
}
