package cn.photomap

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.photomap.map.BasemapMode
import cn.photomap.map.PhotoMapEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

@RunWith(AndroidJUnit4::class)
class NativeOverviewTest {
    @Test fun apkAloneIsExplicitAboutMissingFineDataAndDoesNotSnapAfterIdle() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            fun engine(a:MainActivity)=MainActivity::class.java.getDeclaredField("engine").apply {isAccessible=true}.get(a) as PhotoMapEngine
            fun map(a:MainActivity)=engine(a).javaClass.getDeclaredField("map").apply {isAccessible=true}.get(engine(a)) as? MapLibreMap
            var ready=false;repeat(400){if(!ready){scenario.onActivity {ready=map(it)?.style?.getLayer("place-labels")!=null};SystemClock.sleep(100)}};assertTrue(ready)
            scenario.onActivity {it.chooseBasemap(BasemapMode.SATELLITE);map(it)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.662,104.066),14.0))}
            SystemClock.sleep(3000)
            scenario.onActivity {a->
                assertNotNull(map(a)!!.style!!.getSource("satellite-overview"))
                val info=MainActivity::class.java.getDeclaredField("mapInfo\$delegate").apply {isAccessible=true}.get(a) as androidx.compose.runtime.State<*>
                assertTrue(info.value.toString().contains("尚未导入精细包"))
                assertNull(map(a)!!.style!!.getSource("satellite-beijing"))
                a.chooseBasemap(BasemapMode.STREETS)
                map(a)!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(58.0,68.0),6.0))
            }
            SystemClock.sleep(800);var target:LatLng?=null;scenario.onActivity {target=map(it)!!.cameraPosition.target}
            SystemClock.sleep(1400)
            scenario.onActivity {val after=map(it)!!.cameraPosition.target!!;assertEquals(target!!.latitude,after.latitude,0.00001);assertEquals(target!!.longitude,after.longitude,0.00001);assertTrue(after.latitude>54&&after.longitude<73)}
        }
    }
}
