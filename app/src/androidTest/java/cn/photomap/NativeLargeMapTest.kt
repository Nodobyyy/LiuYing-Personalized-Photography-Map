package cn.photomap

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.VectorSource
import java.io.File

/** Explicit opt-in: a supplied QA archive is imported/removed; photographs are never touched. */
@RunWith(AndroidJUnit4::class)
class NativeLargeMapTest {
    @Test fun importRealNationalArchiveAndRenderChengduStreets()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val file=File(context.filesDir,"qa-map-archive.zip")
        assumeTrue("Stage the 4GB QA map archive explicitly",file.isFile)
        val manager=(context.applicationContext as PhotoMapApplication).mapPackages
        val started=SystemClock.elapsedRealtime();val imported=manager.import(Uri.fromFile(file))
        try {
            assertTrue(File(imported.directory,"china-streets.pmtiles").length()>4_000_000_000L)
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                fun map(activity: MainActivity): MapLibreMap? {
                    val engine=MainActivity::class.java.getDeclaredField("engine").apply {isAccessible=true}.get(activity)
                    return engine.javaClass.getDeclaredField("map").apply {isAccessible=true}.get(engine) as? MapLibreMap
                }
                var ready=false
                repeat(300) {if(!ready) {scenario.onActivity {ready=map(it)?.style!=null};SystemClock.sleep(100)}}
                assertTrue(ready)
                scenario.onActivity {map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.662,104.066),14.0))}
                var selected=false
                repeat(600) {if(!selected) {scenario.onActivity {selected=map(it)?.style?.getSourceAs<VectorSource>("base")?.url?.contains(imported.directory.name)==true};SystemClock.sleep(100)}}
                assertTrue("National enhanced map not selected",selected);SystemClock.sleep(3000)
                scenario.onActivity {activity->
                    val native=map(activity)!!;val box=RectF(0f,0f,1080f,1920f)
                    val roads=native.queryRenderedFeatures(box,"road-labels");val pois=native.queryRenderedFeatures(box,"poi-labels")
                    assertTrue("No Chengdu road names",roads.isNotEmpty());assertTrue("No Chengdu POIs",pois.isNotEmpty())
                    Log.i("PhotoMapQA","National 4GB package import/render passed; roads=${roads.size}; pois=${pois.size}; elapsedMs=${SystemClock.elapsedRealtime()-started}")
                }
                val screenshot=instrumentation.uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null),"qa-chengdu-enhanced.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
            }
        } finally {manager.remove(imported)}
        Unit
    }
}
