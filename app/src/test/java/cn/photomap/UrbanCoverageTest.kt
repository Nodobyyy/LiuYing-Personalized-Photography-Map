package cn.photomap
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.photomap.map.UrbanCoverage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class UrbanCoverageTest {
    @Test fun nationalCityIndexIncludesDifferentRegionsAndTaiwan() {
        org.junit.Assume.assumeFalse(BuildConfig.ONLINE_MAPS)
        val coverage=UrbanCoverage(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(1995,coverage.count)
        for((lat,lon) in listOf(39.9 to 116.4,30.662 to 104.066,31.23 to 121.47,23.13 to 113.26,43.82 to 87.62,45.8 to 126.53,25.03 to 121.56))assertTrue("Urban coverage missing $lat,$lon",coverage.contains(lat,lon))
        assertFalse(coverage.contains(12.0,114.0))
    }
}
