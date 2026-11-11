package cn.photomap.map

import org.json.JSONArray
import org.json.JSONObject

internal object OnlineBoundaries {
    fun color(dark: Boolean, satellite: Boolean) =
        if (satellite) "#f7f0da" else if (dark) "#9eaaa0" else "#787f72"

    fun apply(style: JSONObject, dark: Boolean, satellite: Boolean) {
        val sources = style.getJSONObject("sources")
        sources.put("world-admin", JSONObject("""{"type":"geojson","data":"asset://maps/world-boundaries.geojson","maxzoom":8}"""))
        val color = color(dark, satellite)
        val layers = style.getJSONArray("layers")
        fun line(id: String, source: String, minZoom: Double, opacity: Double, width: Double): JSONObject =
            JSONObject("""{"id":"$id","type":"line","source":"$source","minzoom":$minZoom,
                "layout":{"line-join":"round"},"paint":{"line-color":"$color","line-opacity":$opacity,
                "line-width":["interpolate",["linear"],["zoom"],1,$width,7,${width + 0.35},13,${width + 0.65}]}}""")
        fun kind(name: String) = JSONArray("""["==",["get","kind"],"$name"]""")
        val globeHandoff = OnlineMapAssets.GLOBE_TILE_ZOOM
        layers.put(line("world-country-borders", "world-admin", globeHandoff, .82, .9).put("filter", kind("country")))
        val uncertain = line("world-uncertain-borders", "world-admin", globeHandoff, .65, .75).put("filter", kind("uncertain"))
        uncertain.getJSONObject("paint").put("line-dasharray", JSONArray("[3,2]"))
        layers.put(uncertain)
        layers.put(line("world-region-borders", "world-admin", 3.3, .55, .55).put("filter", kind("region")))
        layers.put(line("china-province-borders", "world-admin", 3.3, .55, .55).put("filter", kind("china-region")))
        if (sources.has("base")) {
            val local = line("online-local-borders", "base", 9.5, .48, .48).put("source-layer", "boundary")
                .put("filter", JSONArray("""[">=",["coalesce",["get","admin_level"],0],5]"""))
            local.getJSONObject("paint").put("line-dasharray", JSONArray("[3,2]"))
            layers.put(local)
        }
    }
}
