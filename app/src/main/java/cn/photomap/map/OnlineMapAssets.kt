package cn.photomap.map

import android.content.Context
import android.net.Uri
import cn.photomap.LocalMapBundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

object OnlineMapAssets {
    const val STREET_DETAIL_ZOOM = 10.5
    const val SATELLITE_DETAIL_ZOOM = 8
    private const val WORLD_SATELLITE_TRANSITION = 4
    internal const val GLOBE_TILE_ZOOM = 4.8
    private const val OVERVIEW = "maps/overview"
    private val archives = listOf("china.pmtiles", "context.pmtiles", "satellite-overview.pmtiles", "world-satellite.pmtiles", "world-roads.pmtiles")

    data class Loaded(val streets: LocalMapBundle.Loaded, val satellite: JSONObject, val hasOverview: Boolean)

    internal fun prepareGlobeOverview(style: JSONObject, mode: BasemapMode, dark: Boolean): String {
        val satellite = mode == BasemapMode.SATELLITE
        val image = when {
            satellite -> "globe-satellite.png"
            dark -> "globe-streets-night.png"
            else -> "globe-streets-day.png"
        }
        val baseIds = setOf(
            "context-land", "context-water", "embedded-land", "embedded-water",
            "satellite-world", "satellite-overview", "satellite-online-world"
        )
        for (layer in style.layers()) {
            val id = layer.optString("id")
            val type = layer.optString("type")
            val minimum = when {
                type == "background" && satellite -> {
                    layer.put("layout", JSONObject().put("visibility", "none"))
                    continue
                }
                type == "background" || id in baseIds -> GLOBE_TILE_ZOOM
                type == "symbol" -> 2.0 // Avoid crowded country labels at the rim of the whole globe.
                else -> continue
            }
            layer.put("minzoom", maxOf(layer.optDouble("minzoom", 0.0), minimum))
        }
        return "maps/$image"
    }

    fun load(context:Context): Loaded {
        val manifest=JSONObject(context.assets.open("maps/online-files.json").bufferedReader().use {it.readText()}).getJSONObject("files")
        val root=File(context.filesDir,"online-fonts").apply {mkdirs()}
        manifest.keys().asSequence().filter {it.startsWith("fonts/")}.forEach {name->
            require(name.split('/').none {it==".." || it=="."})
            val file=File(root,name);val bytes=context.assets.open("maps/$name").use {it.readBytes()}
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)};require(hash==manifest.getString(name))
            if(!file.exists() || file.length()!=bytes.size.toLong()){file.parentFile!!.mkdirs();val part=File(file.path+".part");part.writeBytes(bytes);check(part.renameTo(file))}
        }
        val style=JSONObject(context.assets.open("maps/style-online.json").bufferedReader().use {it.readText()})
        style.put("glyphs","file://${root.absolutePath}/fonts/{fontstack}/{range}.pbf")
        // Hand off at the point where the globe already fills the viewport.
        // Keeping the experimental globe tile renderer at regional zoom can
        // clip entire tiles and stack labels in a corner.
        style.put("projection", JSONObject().put("type", JSONArray(
            """["step",["zoom"],"vertical-perspective",$GLOBE_TILE_ZOOM,"mercator"]"""
        )))
        val satellite = readStyle(context, "maps/satellite-online.json")
        val overview = try {
            prepareOverview(context)
        } catch (error: Exception) {
            android.util.Log.w("OnlineMapAssets", "Embedded overview unavailable", error)
            null
        }
        val streets = overview?.let { combineStreets(style, it.streets, it.context) } ?: style
        if (overview != null) {
            satellite.getJSONObject("sources").getJSONObject("satellite-online")
                .put("minzoom", WORLD_SATELLITE_TRANSITION)
            satellite.getJSONObject("sources").put("satellite-overview",
                overview.satellite.getJSONObject("sources").getJSONObject("satellite-overview"))
            satellite.getJSONObject("sources").put("satellite-world", JSONObject()
                .put("type", "raster").put("url", overview.worldSatelliteUrl).put("tileSize", 256).put("maxzoom", 2))
            val remoteLayer = satellite.getJSONArray("layers").getJSONObject(0)
                .put("minzoom", SATELLITE_DETAIL_ZOOM)
                .put("paint", JSONObject().put("raster-fade-duration", 200))
            val regionalWorldLayer = JSONObject(remoteLayer.toString())
                .put("id", "satellite-online-world").put("minzoom", WORLD_SATELLITE_TRANSITION)
                .apply { remove("maxzoom") }
                .put("paint", JSONObject().put("raster-fade-duration", 150))
            val localLayer = overview.satellite.getJSONArray("layers").getJSONObject(0)
                .put("minzoom", WORLD_SATELLITE_TRANSITION)
                .apply { remove("maxzoom") }
            // Retain the bundled global Z0-2, regional online Z4-7 and China Z0-7 overview imagery
            // underneath detailed satellite tiles at all zoom levels so zooming never shows a blank screen while loading.
            satellite.put("layers", JSONArray()
                .put(JSONObject("""{"id":"satellite-world","source":"satellite-world","type":"raster","paint":{"raster-fade-duration":0}}"""))
                .put(regionalWorldLayer)
                .put(localLayer)
                .put(remoteLayer))
        }
        val attribution = if (overview != null) "Protomaps · OpenFreeMap · © OpenStreetMap contributors"
            else "OpenFreeMap · © OpenStreetMap contributors"
        return Loaded(LocalMapBundle.Loaded(streets.toString(), attribution, root), satellite, overview != null)
    }

    private data class Overview(val streets: JSONObject, val context: JSONObject, val satellite: JSONObject, val worldSatelliteUrl: String)

    @Synchronized
    private fun prepareOverview(context: Context): Overview {
        val manifest = readStyle(context, "$OVERVIEW/archives.json")
        val identifier = hash(manifest.toString().toByteArray()).take(20)
        val parent = File(context.filesDir, "online-overview")
        val folder = File(parent, identifier).apply { mkdirs() }
        for (name in archives) {
            val entry = manifest.getJSONObject(name)
            val size = entry.getLong("size")
            val file = File(folder, name)
            if (file.isFile && file.length() == size) continue
            val pending = File(folder, "$name.part")
            try {
                require(folder.usableSpace > size + 32L * 1024 * 1024) { "Insufficient room for embedded map" }
                val digest = MessageDigest.getInstance("SHA-256")
                val copied = context.assets.open("$OVERVIEW/$name").use { source ->
                    DigestInputStream(source, digest).use { input ->
                        pending.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                    }
                }
                check(copied == size && hex(digest.digest()) == entry.getString("sha256"))
                check(pending.renameTo(file))
            } finally {
                pending.delete()
            }
        }
        fun localStyle(name: String): JSONObject {
            val style = readStyle(context, "$OVERVIEW/$name")
            val sources = style.getJSONObject("sources")
            sources.keys().forEach { id ->
                val source = sources.getJSONObject(id)
                val name = source.getString("url").removePrefix("pmtiles://local://")
                require(name in archives)
                source.put("url", "pmtiles://" + Uri.fromFile(File(folder, name)))
            }
            return style
        }
        val overview = Overview(localStyle("style.json"), localStyle("style-context.json"), localStyle("style-satellite.json"),
            "pmtiles://" + Uri.fromFile(File(folder, "world-satellite.pmtiles")))
        parent.listFiles()?.filter { it != folder && it.name.matches(Regex("[a-f0-9]{20}")) }?.forEach { it.deleteRecursively() }
        return overview
    }

    private fun combineStreets(online: JSONObject, overview: JSONObject, context: JSONObject): JSONObject {
        val sources = online.getJSONObject("sources")
        sources.getJSONObject("base").put("minzoom", STREET_DETAIL_ZOOM.toInt())
        sources.put("overview", overview.getJSONObject("sources").getJSONObject("base"))
        val contextSources = context.getJSONObject("sources")
        contextSources.keys().forEach { sources.put(it, contextSources.getJSONObject(it)) }
        val layers = JSONArray()
        val contextLayers = context.layers()
        for (layer in contextLayers) {
            if (layer.getString("type") == "background") layer.put("id", "background")
            layers.put(layer)
        }
        for (layer in overview.layers()) {
            val id = layer.getString("id")
            if (id == "background" || id == "region-labels") continue
            if (id == "place-labels" && contextLayers.none { it.optString("id") == "context-place-labels" }) {
                val outside = JSONObject(layer.toString()).put("id", "global-overview-place-labels")
                    .put("source", "context").put("maxzoom", STREET_DETAIL_ZOOM)
                val localExtent = JSONObject("""{"type":"Polygon","coordinates":[[[73,3],[136,3],[136,54],[73,54],[73,3]]]}""")
                val filter = JSONArray().put("all").put(ChinaMapLabels.normalize(layer.getJSONArray("filter")))
                    .put(JSONArray().put("!").put(JSONArray().put("within").put(localExtent)))
                layers.put(outside.put("filter", filter))
            }
            layer.put("id", "embedded-$id").put("source", "overview")
            // Keep a coarse street network and place/road labels visible while detailed tiles arrive (or on satellite).
            val roads = layer.optString("source-layer") == "roads"
            val limit = if (roads) 18.0 else if (layer.getString("type") == "symbol") 17.5 else 13.0
            if (roads && layer.getString("type") == "line") {
                layer.getJSONObject("paint").put("line-opacity", JSONArray("""["interpolate",["linear"],["zoom"],11,1,14,0.48]"""))
            }
            if (id != "land") {
                val max = minOf(layer.optDouble("maxzoom", 24.0), limit)
                if (layer.optDouble("minzoom", 0.0) >= max) continue
                layer.put("maxzoom", max)
            }
            layers.put(layer)
        }
        for (layer in online.layers()) {
            if (layer.getString("type") == "background") continue
            if (layer.optString("id") == "context-country-labels") layer.put("id", "online-country-labels")
            layer.put("minzoom", maxOf(layer.optDouble("minzoom", 0.0), STREET_DETAIL_ZOOM))
            layers.put(layer)
        }
        return online.put("layers", layers)
    }

    private fun JSONObject.layers(): List<JSONObject> = getJSONArray("layers").let { layers ->
        List(layers.length()) { layers.getJSONObject(it) }
    }

    private fun readStyle(context: Context, name: String): JSONObject =
        context.assets.open(name).bufferedReader().use { JSONObject(it.readText()) }

    private fun hash(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
