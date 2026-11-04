package cn.photomap

import android.graphics.*
import android.os.SystemClock
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.photomap.map.*
import cn.photomap.data.Library
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeOnlineMapTest {
    @Test fun realNetworkMapsSatelliteCacheAndLocalPhotoRetention()=runBlocking {
        assertTrue(BuildConfig.ONLINE_MAPS)
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        fun shell(command:String){instrumentation.uiAutomation.executeShellCommand(command).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {stream->stream.readBytes()}}}
        fun screenshot(name:String):Int {val b=instrumentation.uiAutomation.takeScreenshot()!!;val colors=hashSetOf<Int>();for(x in 280..800 step 24)for(y in 550..1250 step 24)colors.add(b.getPixel(x,y));File(context.getExternalFilesDir(null),"$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return colors.size}
        shell("settings put global airplane_mode_on 0");shell("svc wifi enable")
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            fun map(a:MainActivity):MapLibreMap? {val engine=MainActivity::class.java.getDeclaredField("engine").apply {isAccessible=true}.get(a) as PhotoMapEngine;return engine.javaClass.getDeclaredField("map").apply {isAccessible=true}.get(engine) as? MapLibreMap}
            fun waitFor(test:(MapLibreMap)->Boolean){var ready=false;repeat(600){if(!ready){scenario.onActivity {ready=map(it)?.let(test)==true};SystemClock.sleep(100)}};assertTrue("Online resources did not load",ready)}
            waitFor{it.style?.getSource("base")!=null}
            scenario.onActivity {it.chooseBasemap(BasemapMode.STREETS);map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.882,116.412),14.0))}
            waitFor{it.queryRenderedFeatures(RectF(0f,0f,1080f,1920f),"road-labels").isNotEmpty()}
            scenario.onActivity {a->val m=map(a)!!;assertTrue(m.queryRenderedFeatures(RectF(0f,0f,1080f,1920f),"water").all {it.geometry()?.type() in listOf("Polygon","MultiPolygon")});assertEquals(0,m.queryRenderedFeatures(m.projection.toScreenLocation(LatLng(39.882,116.412)),"water").size)}
            screenshot("online-streets")
            scenario.onActivity {it.chooseBasemap(BasemapMode.SATELLITE)}
            waitFor{it.style?.getSource("satellite-online")!=null};SystemClock.sleep(9000)
            assertTrue("Real satellite imagery must have colour detail, not a blank background",screenshot("online-satellite")>50)
            scenario.onActivity {it.chooseBasemap(BasemapMode.STREETS)}
            waitFor{it.queryRenderedFeatures(RectF(0f,0f,1080f,1920f),"road-labels").isNotEmpty()}
            shell("svc wifi disable");shell("svc data disable");SystemClock.sleep(2000)
            scenario.onActivity {it.chooseBasemap(BasemapMode.SIMPLE)}
            waitFor{it.queryRenderedFeatures(RectF(0f,0f,1080f,1920f),"road-labels").isNotEmpty()};screenshot("online-cached-without-network")
            val sandbox=File(context.filesDir,"cache-retention-${UUID.randomUUID()}").apply {mkdirs()}
            val isolated=object:ContextWrapper(context){override fun getFilesDir()=sandbox};val library=Library(isolated)
            try {
                val album=File(sandbox,"local-photo.jpg");val bitmap=Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)};album.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
                val imported=library.import(listOf(Uri.fromFile(album))){_,_->};val id=library.save(null,"本地保留测试",30.6,104.06,null,"照片不上网","","2026-11-03",imported.photos)
                val original=library.file(library.records(id).single().photos.single().original);val digest=cn.photomap.core.Archive.hash(original)
                var cleared=false;scenario.onActivity {MapNetwork.clearCache(it){cleared=true}};repeat(300){if(!cleared)SystemClock.sleep(100)};assertTrue(cleared)
                assertEquals(digest,cn.photomap.core.Archive.hash(original));assertEquals(1,library.places().single().photoCount);assertEquals("No unexpected host, upload method or body may leave the map client",0,MapNetwork.audit().second)
                assertTrue("Map GET requests must actually have occurred",MapNetwork.audit().first>0)
            }finally{library.close();sandbox.deleteRecursively()}
            shell("svc wifi enable")
        }
    }
}
