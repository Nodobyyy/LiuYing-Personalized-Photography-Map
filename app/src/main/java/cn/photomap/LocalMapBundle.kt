package cn.photomap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Only bundles staged by tools/stage-map.mjs are accepted; never fetch remote resources. */
object LocalMapBundle {
    data class Loaded(val style: String, val attribution: String,val directory: File?=null)
    fun load(context: Context): Loaded? {
        val manifestText = try { context.assets.open("maps/bundle.json").bufferedReader().use { it.readText() } }
        catch (_: java.io.FileNotFoundException) { return null }
        val manifest = JSONObject(manifestText)
        require(manifest.getInt("version") == 1)
        require(manifest.getString("coordinateSystem") == "WGS84")
        val files = manifest.getJSONObject("files")
        val bundleId = sha(manifestText.toByteArray()).take(24)
        val target = File(context.filesDir, "maps/$bundleId")
        target.mkdirs()
        files.keys().forEach { path ->
            require(path.matches(Regex("[A-Za-z0-9_./ -]+")) && !path.startsWith("/") && path.split('/').none { it == ".." || it.isEmpty() })
            val destination = File(target, path)
            if (!destination.isFile || digest(destination) != files.getString(path)) {
                if(path.endsWith(".pmtiles")) {
                    val bytes=manifest.optJSONObject("fileSizes")?.optLong(path,0) ?: 0
                    if(bytes>0) require(target.usableSpace>bytes+64L*1024*1024) {"地图释放所需空间不足"}
                }
                destination.parentFile!!.mkdirs()
                val temp = File(destination.parentFile, destination.name + ".part")
                try {
                    context.assets.open("maps/$path").use { input -> temp.outputStream().use { input.copyTo(it) } }
                    require(digest(temp) == files.getString(path))
                    check(temp.renameTo(destination))
                } finally { temp.delete() }
            }
        }
        File(target,"bundle.json").writeText(manifestText)
        val loaded=loadDirectory(target,verifyFiles=false)
        target.parentFile?.listFiles()?.filter {it.isDirectory && it.name!=bundleId && it.name.matches(Regex("[a-f0-9]{24}"))}?.forEach {it.deleteRecursively()}
        return loaded
    }
    fun loadDirectory(target: File, verifyFiles: Boolean=true,styleName: String="style.json"): Loaded {
        val manifest = JSONObject(File(target,"bundle.json").takeIf { it.exists() }?.readText() ?: error("缺少地图清单"))
        require(manifest.getInt("version")==1 && manifest.getString("coordinateSystem")=="WGS84")
        val files=manifest.getJSONObject("files")
        files.keys().forEach { path ->
            require(path.matches(Regex("[A-Za-z0-9_./ -]+")) && !path.startsWith("/") && path.split('/').none { it==".." || it=="." || it.isEmpty() })
            manifest.optJSONObject("fileSizes")?.takeIf {it.has(path)}?.let {require(File(target,path).length()==it.getLong(path)) {"地图文件大小不匹配"}}
            if(verifyFiles) require(digest(File(target,path))==files.getString(path)) { "地图校验失败" }
        }
        require(files.has(styleName))
        val style = JSONObject(File(target, styleName).readText())
        require(style.getInt("version")==8 && !style.has("sprite"))
        val sources=style.getJSONObject("sources")
        require(sources.keys().asSequence().none {it=="user-photos" || it.startsWith("china-")})
        sources.keys().forEach { key ->
            val source=sources.getJSONObject(key)
            val sourceType=source.getString("type")
            require(sourceType in listOf("vector","raster") && source.getString("url").startsWith("pmtiles://local://") && !source.has("tiles"))
            val name=source.getString("url").removePrefix("pmtiles://local://");require(files.has(name))
            val header=ByteArray(127);File(target,name).inputStream().use {require(it.read(header)==127)}
            require(String(header,0,7)=="PMTiles" && header[7].toInt()==3 && (if(sourceType=="vector") header[99].toInt()==1 else header[99].toInt() in 2..4))
        }
        val layers=style.getJSONArray("layers")
        for(i in 0 until layers.length()) {val id=layers.getJSONObject(i).getString("id");require(id!="user-photo-covers" && !id.startsWith("china-"))}
        fun rewrite(value: Any): Any = when (value) {
            is JSONObject -> value.apply { keys().asSequence().toList().forEach { key -> put(key, rewrite(get(key))) } }
            is JSONArray -> value.apply { for (i in 0 until length()) put(i, rewrite(get(i))) }
            is String -> {
                when {
                    value.startsWith("pmtiles://local://") -> {
                        val path=value.removePrefix("pmtiles://local://"); require(files.has(path))
                        "pmtiles://" + android.net.Uri.fromFile(File(target,path)).toString()
                    }
                    value.startsWith("local://") -> {
                        val path=value.removePrefix("local://")
                        require(!path.startsWith('/') && path.split('/').none { it==".." || it=="." })
                        "file://" + target.absolutePath + "/" + path
                    }
                    value.contains("://") -> error("Non-local resource")
                    else -> value
                }
            }
            else -> value
        }
        return Loaded(rewrite(style).toString(), manifest.getString("attribution"),target)
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
