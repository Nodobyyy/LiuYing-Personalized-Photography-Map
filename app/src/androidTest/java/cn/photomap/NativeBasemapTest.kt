package cn.photomap

import android.graphics.RectF
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.photomap.map.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.RasterSource
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeBasemapTest {
    @Test fun realOfflineImageryWaterGeometryAndContinuousCamera() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            fun engine(a: MainActivity)=MainActivity::class.java.getDeclaredField("engine").apply {isAccessible=true}.get(a) as PhotoMapEngine
            fun map(a: MainActivity)=engine(a).javaClass.getDeclaredField("map").apply {isAccessible=true}.get(engine(a)) as? MapLibreMap
            fun waitFor(test:(MapLibreMap)->Boolean) {var ok=false;repeat(400){if(!ok){scenario.onActivity {a->ok=map(a)?.let(test)==true};SystemClock.sleep(100)}};assertTrue("Expected native style/resources",ok);SystemClock.sleep(2000)}
            fun shot(name:String){val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();assertNotNull(bitmap);val context=InstrumentationRegistry.getInstrumentation().targetContext;File(context.getExternalFilesDir(null),"$name.png").outputStream().use {bitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap?.recycle()}
            waitFor{it.style?.getLayer("place-labels")!=null}
            scenario.onActivity {it.chooseBasemap(BasemapMode.STREETS);map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.882,116.412),12.5))}
            waitFor{it.style?.getSourceAs<org.maplibre.android.style.sources.VectorSource>("base")?.url?.endsWith("china-fine.pmtiles")==true}
            scenario.onActivity {a->
                val m=map(a)!!;val box=RectF(0f,0f,1080f,1920f)
                val water=m.queryRenderedFeatures(box,"water")
                assertTrue("Water fill must never include open rivers or label points",water.all {it.geometry()?.type() in listOf("Polygon","MultiPolygon")})
                val center=m.projection.toScreenLocation(LatLng(39.882,116.412))
                assertEquals("Temple of Heaven must not be a false blue lake",0,m.queryRenderedFeatures(center,"water").size)
                assertTrue("Real water remains rendered",water.isNotEmpty())
                assertTrue("Canals and rivers remain line features",m.queryRenderedFeatures(box,"waterways").all {it.geometry()?.type() in listOf("LineString","MultiLineString")})
            }
            shot("map03-water-fixed")
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.899,116.416),15.0))}
            SystemClock.sleep(2500);shot("map03-streets")
            scenario.onActivity {it.chooseBasemap(BasemapMode.SIMPLE)}
            waitFor{it.style?.getLayer("poi-labels")==null && it.style?.getLayer("user-photo-covers")!=null};shot("map03-simple")
            scenario.onActivity {it.chooseBasemap(BasemapMode.SATELLITE)}
            waitFor{it.style?.getSourceAs<RasterSource>("satellite-urban")?.url?.contains("satellite-urban.pmtiles")==true}
            scenario.onActivity {a->assertEquals("No opaque earth fill over genuine imagery",null,map(a)!!.style!!.getLayer("land"));assertNotNull(map(a)!!.style!!.getLayer("user-photo-covers"))}
            shot("map03-satellite")
            scenario.recreate();waitFor{it.style?.getSourceAs<RasterSource>("satellite-urban")!=null}
            scenario.onActivity {assertEquals(BasemapMode.SATELLITE,engine(it).basemapMode);it.chooseBasemap(BasemapMode.STREETS);engine(it).national()}
            waitFor{it.style?.getSource("context")!=null};SystemClock.sleep(2500);shot("map03-context")
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(58.0,68.0),6.0))}
            SystemClock.sleep(600);var at:LatLng?=null
            scenario.onActivity {at=map(it)!!.cameraPosition.target}
            SystemClock.sleep(1200)
            scenario.onActivity {a->val target=map(a)!!.cameraPosition.target!!;assertEquals("No idle-time snap back latitude",at!!.latitude,target.latitude,0.00001);assertEquals("No idle-time snap back longitude",at!!.longitude,target.longitude,0.00001);assertTrue("Extended context permits gentle exploration outside China",target.latitude>54 && target.longitude<73)}
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.662,104.066),14.0))}
            waitFor{it.style?.getSourceAs<org.maplibre.android.style.sources.VectorSource>("base")?.url?.endsWith("china-fine.pmtiles")==true}
            scenario.onActivity {a->val m=map(a)!!;assertEquals(15,org.json.JSONObject(m.style!!.json).getJSONObject("sources").getJSONObject("base").getInt("maxzoom"));assertTrue("National fine streets must also render in Chengdu",m.queryRenderedFeatures(RectF(0f,0f,1080f,1920f),"road-labels").isNotEmpty())}
            shot("map03-chengdu-national")
            scenario.onActivity {it.chooseBasemap(BasemapMode.SATELLITE)}
            waitFor{it.style?.getSource("satellite-urban")!=null};shot("map03-chengdu-satellite")
            scenario.onActivity {it.chooseBasemap(BasemapMode.STREETS)}
            for ((name, centre) in listOf(
                "shanghai" to LatLng(31.23, 121.48),
                "taipei" to LatLng(25.03, 121.56)
            )) {
                scenario.onActivity { map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 15.0)) }
                waitFor { current ->
                    val viewport = RectF(0f, 0f, 1080f, 1920f)
                    current.queryRenderedFeatures(viewport, "road-labels").isNotEmpty() &&
                        current.queryRenderedFeatures(viewport, "poi-labels").isNotEmpty()
                }
                shot("map03-$name-national")
            }
        }
    }
}
