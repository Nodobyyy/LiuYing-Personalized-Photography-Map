package cn.photomap

import cn.photomap.map.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MapAppearanceTest {
    @Test fun repairsOldImportedWaterStylesAndRetainsPolygonRestriction() {
        val json=JSONObject("""{"version":8,"sources":{},"layers":[{"id":"old-water","type":"fill","source-layer":"water","filter":["==","kind","water"],"paint":{"fill-color":"#cbdde3"}}]}""")
        MapAppearance.apply(json,BasemapMode.STREETS,null)
        val filter=json.getJSONArray("layers").getJSONObject(0).getJSONArray("filter").toString()
        assertTrue(filter.contains("geometry-type"));assertTrue(filter.contains("Polygon"));assertTrue(filter.contains("get"))
    }
    @Test fun satelliteUsesRealRasterSourcesWithoutOpaqueLandFill() {
        val json=JSONObject("""{"sources":{},"layers":[{"id":"land","type":"fill","source-layer":"earth","paint":{}},{"id":"roads","type":"line","source-layer":"roads","paint":{}}]}""")
        val satellite=JSONObject("""{"sources":{"satellite":{"type":"raster","url":"pmtiles://file:///test.pmtiles"}},"layers":[{"id":"satellite","type":"raster","source":"satellite"}]}""")
        MapAppearance.apply(json,BasemapMode.SATELLITE,satellite)
        assertEquals("raster",json.getJSONObject("sources").getJSONObject("satellite").getString("type"))
        assertEquals(2,json.getJSONArray("layers").length())
        assertEquals("satellite",json.getJSONArray("layers").getJSONObject(0).getString("id"))
    }
}
