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
    @Test fun onlineBuildUsesOnlyMapEndpointsAndLocalFontsWithoutBundledTiles() {
        org.junit.Assume.assumeTrue(BuildConfig.ONLINE_MAPS)
        val c=ApplicationProvider.getApplicationContext<Context>();val loaded=OnlineMapAssets.load(c);val j=JSONObject(loaded.style)
        assertEquals("https://tiles.openfreemap.org/planet",j.getJSONObject("sources").getJSONObject("base").getString("url"))
        assertTrue(j.getString("glyphs").startsWith("file://"))
        assertFalse(c.assets.list("maps")!!.any {it.endsWith(".pmtiles")})
        assertEquals("cn.photomap.online",c.packageName)
    }
}
