package cn.photomap
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.photomap.map.OnlineMapAssets
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OnlineAssetsTest {
    @Test fun onlineStyleUsesLocalFontsAndApprovedDetailEndpoint() {
        org.junit.Assume.assumeTrue(BuildConfig.ONLINE_MAPS)
        val c=ApplicationProvider.getApplicationContext<Context>();val loaded=OnlineMapAssets.load(c);val j=JSONObject(loaded.streets.style)
        assertEquals("https://tiles.openfreemap.org/planet",j.getJSONObject("sources").getJSONObject("base").getString("url"))
        assertTrue(j.getString("glyphs").startsWith("file://"))
        val packaged = c.assets.list("maps/overview").orEmpty().contains("archives.json")
        assertEquals(packaged, loaded.hasOverview)
        if (packaged) {
            val sources = j.getJSONObject("sources")
            assertTrue(sources.getJSONObject("overview").getString("url").startsWith("pmtiles://file://"))
            assertEquals(10, sources.getJSONObject("overview").getInt("maxzoom"))
            assertEquals(10, sources.getJSONObject("base").getInt("minzoom"))
            assertTrue(loaded.satellite.getJSONObject("sources").has("satellite-overview"))
        }
        assertEquals("cn.photomap.online",c.packageName)
    }
}
