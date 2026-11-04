package cn.photomap

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.photomap.map.BasemapMode
import cn.photomap.map.MapNetwork
import cn.photomap.map.PhotoMapEngine
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.RasterSource
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeOnlineOverviewTest {
    @Test fun overviewUsesBundledTilesWhileDetailedZoomUsesTheNetwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(BuildConfig.ONLINE_MAPS && context.assets.list("maps/overview").orEmpty().contains("archives.json"))
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        fun map(activity: MainActivity): MapLibreMap? {
            val engine = MainActivity::class.java.getDeclaredField("engine").apply { isAccessible = true }.get(activity)
            return PhotoMapEngine::class.java.getDeclaredField("map").apply { isAccessible = true }.get(engine) as? MapLibreMap
        }
        fun screenshot(name: String): Int {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val colors = mutableSetOf<Int>()
            for (x in 280..800 step 30) for (y in 440..1250 step 30) colors.add(bitmap.getPixel(x, y))
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            return colors.size
        }
        val viewport = RectF(0f, 0f, 1080f, 1920f)
        shell("svc wifi disable")
        shell("svc data disable")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun await(reason: String, predicate: (MapLibreMap) -> Boolean) {
                    repeat(450) {
                        var ready = false
                        scenario.onActivity { ready = map(it)?.let(predicate) == true }
                        if (ready) return
                        SystemClock.sleep(100)
                    }
                    fail(reason)
                }
                await("Bundled map style did not load") { it.style?.getSource("overview") != null }
                scenario.onActivity { activity ->
                    val globe = map(activity)!!
                    globe.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(22.0, 104.0), 0.0))
                    assertNull(globe.style?.getLayer("china-reference-outline"))
                    assertNull(globe.style?.getLayer("china-display-mask"))
                }
                SystemClock.sleep(900)
                var minimum = Double.NaN
                scenario.onActivity { activity ->
                    val globe = map(activity)!!
                    minimum = globe.cameraPosition.zoom
                    globe.moveCamera(CameraUpdateFactory.zoomOut())
                }
                SystemClock.sleep(900)
                scenario.onActivity { activity ->
                    val globe = map(activity)!!
                    assertEquals("Zooming out again must keep the same globe scale", minimum, globe.cameraPosition.zoom, 0.02)
                    val projection = globe.projection
                    val occluded = projection.javaClass.getMethod("isLocationOccluded", LatLng::class.java)
                    assertEquals(true, occluded.invoke(projection, LatLng(-22.0, -76.0)))
                }
                SystemClock.sleep(1200)
                screenshot("online05-globe")
                scenario.onActivity { activity ->
                    activity.chooseBasemap(BasemapMode.STREETS)
                    map(activity)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.92, 116.41), 8.5))
                }
                await("No local place labels without a connection") {
                    it.queryRenderedFeatures(viewport, "embedded-place-labels").isNotEmpty()
                }
                screenshot("online041-local-streets")
                assertEquals("Fresh offline overview must not request network tiles", 0, MapNetwork.tileRequests())

                shell("svc wifi enable")
                shell("svc data enable")
                repeat(50) { if (!MapNetwork.connected(context)) SystemClock.sleep(100) }
                assertTrue(MapNetwork.connected(context))
                SystemClock.sleep(1800)
                assertEquals("Overview zoom should not fetch network street tiles", 0, MapNetwork.tileRequests())

                shell("svc wifi disable")
                shell("svc data disable")
                scenario.onActivity { activity ->
                    map(activity)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.1, 108.7), 5.0))
                    activity.chooseBasemap(BasemapMode.SATELLITE)
                }
                await("Embedded satellite source missing") {
                    it.style?.getSourceAs<RasterSource>("satellite-overview")?.url?.startsWith("pmtiles://file://") == true
                }
                SystemClock.sleep(2500)
                assertTrue("Expected real local satellite image colours", screenshot("online041-local-satellite") > 45)

                shell("svc wifi enable")
                shell("svc data enable")
                scenario.onActivity { activity ->
                    activity.chooseBasemap(BasemapMode.STREETS)
                    map(activity)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(48.8566, 2.3522), 14.0))
                }
                await("Online road labels should appear at detail zoom") {
                    it.queryRenderedFeatures(viewport, "road-labels").isNotEmpty()
                }
                assertTrue("Detail tiles must actually come through the approved client", MapNetwork.tileRequests() > 0)
            }
        } finally {
            shell("svc wifi enable")
            shell("svc data enable")
        }
    }
}
