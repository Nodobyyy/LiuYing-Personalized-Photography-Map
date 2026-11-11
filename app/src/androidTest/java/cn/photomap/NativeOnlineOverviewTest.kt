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
        fun engine(activity: MainActivity) =
            MainActivity::class.java.getDeclaredField("engine").apply { isAccessible = true }.get(activity) as PhotoMapEngine
        fun map(activity: MainActivity) =
            PhotoMapEngine::class.java.getDeclaredField("map").apply { isAccessible = true }.get(engine(activity)) as? MapLibreMap
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
                scenario.onActivity { it.chooseBasemap(BasemapMode.STREETS) }
                await("Street overview style did not load") { it.style?.getLayer("context-land") != null }
                scenario.onActivity { activity ->
                    val globe = map(activity)!!
                    assertFalse(globe.uiSettings.isRotateGesturesEnabled)
                    assertEquals(0.0, globe.cameraPosition.bearing, 0.01)
                    engine(activity).national()
                    assertNull(globe.style?.getLayer("china-reference-outline"))
                    assertNull(globe.style?.getLayer("china-display-mask"))
                }
                SystemClock.sleep(900)
                var minimum = Double.NaN
                scenario.onActivity { activity ->
                    map(activity)!!.moveCamera(CameraUpdateFactory.zoomOut())
                }
                SystemClock.sleep(300)
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
                // Checking the rendered pixels catches tile clipping failures that
                // feature queries alone miss (the tiles can still be queryable).
                var australia = android.graphics.PointF()
                var border = android.graphics.PointF()
                scenario.onActivity {
                    australia = map(it)!!.projection.toScreenLocation(LatLng(-19.0, 132.0))
                    border = map(it)!!.projection.toScreenLocation(LatLng(50.5443, 102.3364))
                    val offset = IntArray(2)
                    engine(it).view.getLocationOnScreen(offset)
                    australia.offset(offset[0].toFloat(), offset[1].toFloat())
                    border.offset(offset[0].toFloat(), offset[1].toFloat())
                }
                val rendered = (0..15).any {
                    SystemClock.sleep(300)
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    try {
                        val land = bitmap.getPixel(australia.x.toInt(), australia.y.toInt())
                        val borderDrawn = (-6..6).any { dx ->
                            (-6..6).any { dy ->
                                val color = bitmap.getPixel(border.x.toInt() + dx, border.y.toInt() + dy)
                                android.graphics.Color.red(color) in 130..195 &&
                                    android.graphics.Color.green(color) in 130..195
                            }
                        }
                        android.graphics.Color.red(land) > 220 && android.graphics.Color.green(land) > 220 && borderDrawn
                    } finally {
                        bitmap.recycle()
                    }
                }
                screenshot("online054-globe")
                assertTrue("The globe should show inland Australia and the Mongolia–Russia border", rendered)
                shell("input swipe 790 960 270 960 65")
                SystemClock.sleep(1300)
                var longitude = 0.0
                scenario.onActivity { longitude = map(it)!!.cameraPosition.target!!.longitude }
                repeat(16) {
                    val frame = instrumentation.uiAutomation.takeScreenshot()
                    try {
                        val globeVisible = (-2..2).any { dx ->
                            (-2..2).any { dy ->
                                val color = frame.getPixel(frame.width / 2 + dx * 12, frame.height / 2 + dy * 12)
                                color and 0x00ffffff != 0
                            }
                        }
                        assertTrue("The atlas must stay visible during globe spin", globeVisible)
                    } finally {
                        frame.recycle()
                    }
                    SystemClock.sleep(75)
                }
                scenario.onActivity {
                    val camera = map(it)!!.cameraPosition
                    assertTrue("A quick swipe should keep the globe moving", kotlin.math.abs(camera.target!!.longitude - longitude) > 15)
                    assertEquals("The globe easter egg must keep north up", 0.0, camera.bearing, 0.01)
                }
                shell("input tap 540 960")
                scenario.onActivity { longitude = map(it)!!.cameraPosition.target!!.longitude }
                SystemClock.sleep(800)
                scenario.onActivity {
                    assertEquals("Touching the map should stop the spin", longitude, map(it)!!.cameraPosition.target!!.longitude, 0.05)
                    map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 108.0), 5.0))
                }
                await("Chinese provincial boundaries must be visible offline") {
                    it.queryRenderedFeatures(viewport, "china-province-borders").isNotEmpty()
                }
                for ((name, location) in listOf("Paris" to LatLng(48.8566, 2.3522),
                    "Tokyo" to LatLng(35.6762, 139.6503), "New York" to LatLng(40.7128, -74.0060))) {
                    scenario.onActivity { activity ->
                        activity.chooseBasemap(BasemapMode.STREETS)
                        map(activity)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 12.0))
                    }
                    await("$name must show bundled roads while disconnected") {
                        it.queryRenderedFeatures(viewport, "context-detail-major-roads").isNotEmpty()
                    }
                }
                await("New York must retain the land layer when roads overzoom") {
                    it.queryRenderedFeatures(viewport, "context-land").isNotEmpty()
                }
                await("New York must show bundled road labels or route refs") {
                    it.queryRenderedFeatures(viewport, "context-detail-road-labels").isNotEmpty()
                }
                screenshot("online051-new-york-disconnected")
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
                assertTrue("Expected real local satellite image colours", (0..10).any {
                    SystemClock.sleep(1000)
                    screenshot("online041-local-satellite") > 45
                })

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
