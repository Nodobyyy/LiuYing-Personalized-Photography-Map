package cn.photomap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OfflineBundleTest {
    @Test fun declaredVectorAndRasterStylesRewriteEveryRuntimeResourceLocally() {
        org.junit.Assume.assumeFalse(BuildConfig.ONLINE_MAPS)
        val context=ApplicationProvider.getApplicationContext<Context>()
        // Robolectric materialises each AssetInputStream in a byte array; a
        // 1.25GB archive is not representative of Android's streaming asset API.
        // Use valid small headers here; native tests copy/use the real archives.
        val manifest=JSONObject(context.assets.open("maps/bundle.json").bufferedReader().use {it.readText()})
        val root=java.io.File(context.cacheDir,"runtime-resource-test").apply {mkdirs()}
        val files=manifest.getJSONObject("files")
        files.keys().forEach {name->
            val target=java.io.File(root,name);target.parentFile!!.mkdirs()
            if(name.endsWith(".pmtiles")) {
                val header=ByteArray(127);"PMTiles".toByteArray().copyInto(header);header[7]=3;header[99]=if(name.startsWith("satellite")) 2 else 1;target.writeBytes(header)
            } else context.assets.open("maps/$name").use {input->target.outputStream().use {input.copyTo(it)}}
            files.put(name,cn.photomap.core.Archive.hash(target))
            manifest.getJSONObject("fileSizes").put(name,target.length())
        }
        java.io.File(root,"bundle.json").writeText(manifest.toString())
        val loaded=LocalMapBundle.loadDirectory(root)
        assertNotNull(loaded)
        val json=JSONObject(loaded!!.style)
        assertTrue(json.getJSONObject("sources").getJSONObject("base").getString("url").startsWith("pmtiles://file://"))
        assertTrue(json.getString("glyphs").startsWith("file://"))
        assertFalse(loaded.style.contains("https://"));assertFalse(loaded.style.contains("http://"))
        assertEquals(10,json.getJSONObject("sources").getJSONObject("base").getInt("maxzoom"))
        assertTrue(loaded.attribution.contains("OpenStreetMap"))
        val satellite=JSONObject(LocalMapBundle.loadDirectory(loaded.directory!!,verifyFiles=false,styleName="style-satellite.json").style)
        assertEquals("raster",satellite.getJSONObject("sources").getJSONObject("satellite-overview").getString("type"))
        assertTrue(satellite.getJSONObject("sources").getJSONObject("satellite-overview").getString("url").startsWith("pmtiles://file://"))
    }
}
