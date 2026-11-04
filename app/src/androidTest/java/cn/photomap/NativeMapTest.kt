package cn.photomap

import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.VectorSource
import cn.photomap.map.PhotoMapEngine

@RunWith(AndroidJUnit4::class)
class NativeMapTest {
    @Test fun localVectorMapAndLabelsRenderOnNativeAndroid() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            var ready=false
            fun map(activity: MainActivity): MapLibreMap? {
                val field=MainActivity::class.java.getDeclaredField("engine").apply {isAccessible=true};val engine=field.get(activity)
                return engine.javaClass.getDeclaredField("map").apply {isAccessible=true}.get(engine) as? MapLibreMap
            }
            repeat(300) {if(!ready) {scenario.onActivity {ready=map(it)?.style?.getLayer("place-labels")!=null};SystemClock.sleep(100)}}
            assertTrue("Offline style did not load",ready)
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.9,116.4),6.0))}
            SystemClock.sleep(2000)
            var labels=0
            scenario.onActivity {activity->
                val native=map(activity)!!
                val source=native.style!!.getSourceAs<VectorSource>("base")!!
                val features=source.querySourceFeatures(arrayOf("places"),null)
                Log.i("PhotoMapQA","sourcePlaces=${features.size}; sample=${features.take(3).map {it.properties()}}")
                val rendered=native.queryRenderedFeatures(RectF(0f,0f,1080f,1920f),"place-labels")
                labels=rendered.size
                Log.i("PhotoMapQA","renderedLabels=$labels; zoom=${native.cameraPosition.zoom}; scroll=${native.uiSettings.isScrollGesturesEnabled}")
                java.io.File(activity.cacheDir,"native-style.json").writeText(native.style!!.json)
            }
            assertTrue("No native place labels rendered",labels>0)
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(23.7,121.0),3.0))}
            SystemClock.sleep(2000)
            scenario.onActivity {activity->
                val native=map(activity)!!
                val box=RectF(0f,0f,activity.resources.displayMetrics.widthPixels.toFloat(),activity.resources.displayMetrics.heightPixels.toFloat())
                val countries=native.queryRenderedFeatures(box,"country-labels")
                assertFalse("Taiwan must not be displayed as a country",countries.any {feature->
                    listOf("name","name:en","name:zh-Hans","name:zh-Hant").any {key->feature.hasProperty(key) && feature.getStringProperty(key) in listOf("Taiwan","台湾","臺灣","台灣","Republic of China")}
                })
                assertEquals("Taiwan label must be hidden at country zoom",0,native.queryRenderedFeatures(box,"china-taiwan-province").size)
            }
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(26.0,117.0),4.6))}
            SystemClock.sleep(2000)
            scenario.onActivity {activity->
                val native=map(activity)!!
                val box=RectF(0f,0f,activity.resources.displayMetrics.widthPixels.toFloat(),activity.resources.displayMetrics.heightPixels.toFloat())
                assertEquals("Taiwan province appears with the other provinces",1,native.queryRenderedFeatures(box,"china-taiwan-province").size)
                val regions=native.queryRenderedFeatures(box,"region-labels")
                assertTrue("Mainland province labels must also be rendered",regions.any {it.hasProperty("name") && it.getStringProperty("name").endsWith("省")})
                Log.i("PhotoMapQA","Country zoom hides Taiwan label; province zoom renders Taiwan with ${regions.size} other regions; API=${android.os.Build.VERSION.SDK_INT}; pageBytes=${android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)}")
            }
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.9163,116.3972),14.0))}
            var streetsReady=false
            repeat(150) {if(!streetsReady) {scenario.onActivity {streetsReady=map(it)?.style?.getSourceAs<VectorSource>("base")?.url?.contains("china-fine.pmtiles")==true};SystemClock.sleep(100)}}
            assertTrue("Imported national fine street map did not activate",streetsReady)
            val labelViewport = RectF(0f, 0f, 1080f, 1920f)
            for (attempt in 1..200) {
                var labelsReady = false
                scenario.onActivity { activity ->
                    val native = map(activity)!!
                    labelsReady = native.queryRenderedFeatures(labelViewport, "road-labels").isNotEmpty() &&
                        native.queryRenderedFeatures(labelViewport, "poi-labels").isNotEmpty()
                }
                if (labelsReady) break
                SystemClock.sleep(100)
            }
            scenario.onActivity {activity->
                val native=map(activity)!!
                val roads=native.queryRenderedFeatures(labelViewport,"road-labels");val pois=native.queryRenderedFeatures(labelViewport,"poi-labels")
                val source=native.style!!.getSourceAs<VectorSource>("base")!!
                Log.i("PhotoMapQA","Detailed source roads=${source.querySourceFeatures(arrayOf("roads"),null).size}; pois=${source.querySourceFeatures(arrayOf("pois"),null).size}; rendered roads=${roads.size}; pois=${pois.size}")
                assertTrue("Street names must render from detailed data",roads.isNotEmpty())
                assertTrue("Landmarks must render from detailed data",pois.isNotEmpty())
                val field=MainActivity::class.java.getDeclaredField("engine").apply {isAccessible=true}
                val engine=field.get(activity) as PhotoMapEngine;engine.picking=true
                assertEquals("Photos must not cover the selection crosshair","none",native.style!!.getLayer("user-photo-covers")!!.visibility.value)
                assertTrue(engine.canPick);assertNotNull(native.style!!.getLayer("china-display-mask"))
                engine.picking=false
                Log.i("PhotoMapQA","Beijing street names=${roads.size}; POI labels=${pois.size}; sample=${pois.take(5).map {it.properties()?.get("name")}}")
            }
            scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(80.0,0.0),6.0))}
            SystemClock.sleep(800)
            scenario.onActivity {activity->
                val visible=map(activity)!!.projection.visibleRegion.latLngBounds
                Log.i("PhotoMapQA","Constrained viewport north=${visible.latitudeNorth}; south=${visible.latitudeSouth}; west=${visible.longitudeWest}; east=${visible.longitudeEast}")
                val target=map(activity)!!.cameraPosition.target!!
                assertTrue("Native target constraint escaped the extended context",target.latitude<=60.001 && target.longitude>=65.999)
            }
            scenario.recreate()
            SystemClock.sleep(2000)
            scenario.onActivity {assertNotNull("Map survives Activity recreation",map(it))}
        }
    }
}
