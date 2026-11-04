package cn.photomap

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.photomap.map.MapPackages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeNationalImportTest {
    @Test fun importActualNationalZip64ArchiveAndReuseItWithoutDuplication()=runBlocking {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val zip=File(context.getExternalFilesDir(null),"qa-national.zip")
        assertTrue("Stage the actual delivered national ZIP",zip.length()>16_000_000_000L)
        val manager=MapPackages(context)
        val map=manager.import(Uri.fromFile(zip))
        assertTrue(File(map.directory,"china-fine.pmtiles").length()>15_000_000_000L)
        assertTrue(File(map.directory,"satellite-urban.pmtiles").length()>100_000_000L)
        val style=cn.photomap.LocalMapBundle.loadDirectory(map.directory,verifyFiles=false,styleName="style-satellite.json")
        val imageryUrl = org.json.JSONObject(style.style).getJSONObject("sources")
            .getJSONObject("satellite-urban").getString("url")
        assertEquals("pmtiles://" + Uri.fromFile(File(map.directory, "satellite-urban.pmtiles")), imageryUrl)
        val before=manager.list().size
        assertEquals(map.directory,manager.import(Uri.fromFile(zip)).directory)
        assertEquals(before,manager.list().size)
        // Keep the verified package for the following native render/workflow tests.
        assertTrue(zip.delete())
        }
    }
}
