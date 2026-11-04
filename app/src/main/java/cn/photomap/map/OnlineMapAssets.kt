package cn.photomap.map

import android.content.Context
import cn.photomap.LocalMapBundle
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object OnlineMapAssets {
    fun load(context:Context):LocalMapBundle.Loaded {
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
        return LocalMapBundle.Loaded(style.toString(),"OpenFreeMap · © OpenStreetMap contributors",root)
    }
}
