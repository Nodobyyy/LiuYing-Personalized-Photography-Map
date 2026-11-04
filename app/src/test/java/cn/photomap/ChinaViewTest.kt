package cn.photomap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.photomap.map.ChinaView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ChinaViewTest {
    @Test fun displayIncludesMainlandTaiwanHongKongMacaoAndAllProvinceNames() {
        val view=ChinaView(ApplicationProvider.getApplicationContext<Context>())
        for((lat,lon) in listOf(39.916 to 116.397,25.03 to 121.56,22.4 to 114.1,22.1987 to 113.5439)) assertTrue("Missing Chinese location $lat,$lon",view.contains(lat,lon))
        assertFalse(view.contains(35.68,139.76));assertFalse(view.contains(47.92,106.92))
        val provinces=view.data.getJSONObject("provinces").getJSONArray("features")
        val names=(0 until provinces.length()).map {provinces.getJSONObject(it).getJSONObject("properties").getString("name")}
        assertEquals(34,names.toSet().size);assertTrue(names.containsAll(listOf("台湾省","湖北省","青海省","香港特别行政区","澳门特别行政区")))
        assertTrue("Reference must retain Tawang",view.contains(27.59,91.87))
        assertTrue("Reference must retain Aksai Chin",view.contains(35.2,79.9))
        assertTrue("Reference must retain Diaoyu",view.contains(25.75,123.47))
        assertEquals(8,view.data.getJSONObject("boundaryLines").getJSONArray("features").length())
    }
}
