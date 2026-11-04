package cn.photomap.map

import android.content.Context
import android.net.Uri
import cn.photomap.LocalMapBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class RegionalMap(val directory: File, val name: String, val west: Double, val south: Double, val east: Double, val north: Double, val minZoom: Double, val maxZoom: Double)
class MapPackages(private val context: Context) {
    private val root=File(context.filesDir,"regional-maps").apply { mkdirs() }
    init {root.listFiles()?.filter {it.isDirectory && it.name.startsWith("staging-")}?.forEach {it.deleteRecursively()}}
    fun list(): List<RegionalMap> = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith("staging-") }?.mapNotNull { dir -> runCatching {
        val m=JSONObject(File(dir,"bundle.json").readText()); val b=m.getJSONArray("bounds")
        RegionalMap(dir,m.optString("name","地区离线地图"),b.getDouble(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),m.getDouble("minZoom"),m.getDouble("maxZoom"))
    }.getOrNull() } ?: emptyList()
    suspend fun import(uri: Uri,onProgress: (Long,Long,Boolean)->Unit={_,_,_->}): RegionalMap = withContext(Dispatchers.IO) {
        val id=UUID.randomUUID().toString(); val stage=File(root,"staging-$id").apply { mkdirs() }
        try {
            var total=0L;var expected=0L;var nextSpaceCheck=0L;var lastProgress=0L
            val names=hashSetOf<String>(); val fileNames=hashSetOf<String>(); val buffer=ByteArray(65536)
            context.contentResolver.openInputStream(uri)!!.use { stream -> ZipInputStream(stream).use { zip ->
                var entry=zip.nextEntry
                while(entry!=null) {
                    val name=entry.name.removeSuffix("/")
                    require(name.matches(Regex("[A-Za-z0-9_./ -]+")) && !name.startsWith('/') && name.split('/').none { it==".." || it=="." || it.isEmpty() } && names.add(name) && names.size<2000)
                    if(entry.isDirectory) {File(stage,name).mkdirs();zip.closeEntry();entry=zip.nextEntry;continue}
                    fileNames.add(name)
                    val file=File(stage,name); file.parentFile!!.mkdirs()
                    var fileBytes=0L
                    val maxFile=when {name=="bundle.json"->1024L*1024;name.startsWith("style") && name.endsWith(".json")->4L*1024*1024;name.endsWith(".pmtiles")->32L*1024*1024*1024;else->16L*1024*1024}
                    file.outputStream().use { out->while(true) {
                        val n=zip.read(buffer); if(n<0) break; total+=n;fileBytes+=n
                        require(fileBytes<=maxFile && total<=32L*1024*1024*1024)
                        if(total>=nextSpaceCheck) {require(stage.usableSpace>48L*1024*1024);nextSpaceCheck=total+16L*1024*1024}
                        out.write(buffer,0,n)
                        val now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress>=200) {onProgress(total,expected,false);lastProgress=now}
                    } }
                    if(name=="bundle.json") {
                        val incoming=JSONObject(file.readText());val incomingFiles=incoming.getJSONObject("files")
                        // Re-importing the same 17GB package should reuse the
                        // previously verified private copy, without duplicating it.
                        val duplicate=list().firstOrNull {existing->runCatching {
                            val previous=JSONObject(File(existing.directory,"bundle.json").readText()).getJSONObject("files")
                            val keys=incomingFiles.keys().asSequence().toSet()
                            keys==previous.keys().asSequence().toSet() && keys.all {key->incomingFiles.getString(key)==previous.getString(key) && File(existing.directory,key).isFile && (incoming.optJSONObject("fileSizes")?.takeIf {it.has(key)}?.getLong(key)?.let {File(existing.directory,key).length()==it} ?: true)}
                        }.getOrDefault(false)}
                        if(duplicate!=null) {stage.deleteRecursively();return@withContext duplicate}
                        val sizes=incoming.optJSONObject("fileSizes")
                        if(sizes!=null) {expected=0; sizes.keys().forEach {key->val size=sizes.getLong(key);require(size>=0);expected=Math.addExact(expected,size)};require(expected<=32L*1024*1024*1024 && stage.usableSpace>expected+64L*1024*1024)}
                    }
                    zip.closeEntry(); entry=zip.nextEntry
                }
            } }
            val manifest=JSONObject(File(stage,"bundle.json").readText()); val bounds=manifest.getJSONArray("bounds")
            require(fileNames==manifest.getJSONObject("files").keys().asSequence().toSet()+"bundle.json")
            require(bounds.length()==4 && bounds.getDouble(0)>=-180 && bounds.getDouble(2)<=180 && bounds.getDouble(0)<bounds.getDouble(2) && bounds.getDouble(1)>=-85 && bounds.getDouble(3)<=85 && bounds.getDouble(1)<bounds.getDouble(3))
            require(manifest.getDouble("minZoom") in 0.0..17.0 && manifest.getDouble("maxZoom") in manifest.getDouble("minZoom")..17.0)
            require(manifest.getJSONObject("files").has(manifest.getString("licenseEvidence")) && manifest.getJSONObject("files").has(manifest.getString("boundaryReview")))
            onProgress(total,expected,true)
            LocalMapBundle.loadDirectory(stage)
            if(manifest.getJSONObject("files").has("style-satellite.json")) LocalMapBundle.loadDirectory(stage,verifyFiles=false,styleName="style-satellite.json")
            val target=File(root,id); check(stage.renameTo(target))
            list().first { it.directory==target }
        } catch(error: Exception) { stage.deleteRecursively(); throw error }
    }
    suspend fun remove(map: RegionalMap) = withContext(Dispatchers.IO) { require(map.directory.parentFile==root); check(map.directory.deleteRecursively()) }
}
