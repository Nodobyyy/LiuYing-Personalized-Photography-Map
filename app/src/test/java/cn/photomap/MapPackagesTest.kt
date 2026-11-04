package cn.photomap

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import cn.photomap.core.Archive
import cn.photomap.map.MapPackages
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MapPackagesTest {
    private lateinit var context: Context
    @Before fun setup() {context=ApplicationProvider.getApplicationContext();File(context.filesDir,"regional-maps").deleteRecursively()}
    @Test fun validRegionalZipWithDirectoryEntriesImportsAndCanBeRemoved()=runBlocking {
        val source=File(context.cacheDir,"region-source").apply {mkdirs()}
        javaClass.getResourceAsStream("/region.pmtiles")!!.use {input->File(source,"map.pmtiles").outputStream().use {input.copyTo(it)}}
        File(source,"LICENSE.txt").writeText("TEST FIXTURE: Protomaps / OpenStreetMap ODbL Produced Work; see app assets maps/LICENSE.txt")
        File(source,"REVIEW.txt").writeText("TEST ONLY, not official map approval")
        File(source,"style.json").writeText("""{"version":8,"sources":{"base":{"type":"vector","url":"pmtiles://local://map.pmtiles"}},"layers":[]}""")
        val names=listOf("map.pmtiles","LICENSE.txt","REVIEW.txt","style.json")
        val files=JSONObject();names.forEach {files.put(it,Archive.hash(File(source,it)))}
        File(source,"bundle.json").writeText(JSONObject().put("version",1).put("name","地区测试").put("coordinateSystem","WGS84").put("attribution","Protomaps / OpenStreetMap").put("bounds",JSONArray(listOf(116.3,39.8,116.5,40.0))).put("minZoom",10).put("maxZoom",10).put("licenseEvidence","LICENSE.txt").put("boundaryReview","REVIEW.txt").put("files",files).toString())
        val zip=File(context.cacheDir,"region.zip")
        ZipOutputStream(zip.outputStream()).use {out->
            out.putNextEntry(ZipEntry("fonts/"));out.closeEntry()
            (names+"bundle.json").forEach {name->out.putNextEntry(ZipEntry(name));File(source,name).inputStream().use {it.copyTo(out)};out.closeEntry()}
        }
        val manager=MapPackages(context);val map=manager.import(Uri.fromFile(zip))
        assertEquals("地区测试",map.name);assertEquals(1,manager.list().size)
        assertEquals("Repeated import must reuse the verified map",map.directory,manager.import(Uri.fromFile(zip)).directory)
        assertEquals(1,manager.list().size)
        assertTrue(JSONObject(LocalMapBundle.loadDirectory(map.directory).style).getJSONObject("sources").getJSONObject("base").getString("url").startsWith("pmtiles://file://"))
        manager.remove(map);assertTrue(manager.list().isEmpty())
    }
    @Test fun traversalMapPackageIsRejectedWithoutWritingOutsideStaging()=runBlocking {
        val zip=File(context.cacheDir,"evil-map.zip")
        ZipOutputStream(zip.outputStream()).use {out->out.putNextEntry(ZipEntry("../../escape-map"));out.write("bad".toByteArray());out.closeEntry()}
        val manager=MapPackages(context)
        assertTrue(runCatching {manager.import(Uri.fromFile(zip))}.isFailure)
        assertTrue(manager.list().isEmpty());assertFalse(File(context.filesDir,"escape-map").exists())
    }
}
