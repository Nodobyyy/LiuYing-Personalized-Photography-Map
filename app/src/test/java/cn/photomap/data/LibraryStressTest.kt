package cn.photomap.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real JPEG files and SQLite rows in the test sandbox, never application user data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryStressTest {
    @Test fun realFileDatasets100_500_1000Locations()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val sources=(0 until 10).map {i->
            val image=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(i*20,90,140))}
            val source=File(context.cacheDir,"legal-generated-test-$i.jpg")
            source.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,85,it)};image.recycle();Uri.fromFile(source)
        }
        val results=JSONArray()
        for(size in listOf(100,500,1000)) {
            File(context.filesDir,"library").deleteRecursively()
            val library=Library(context)
            try {
                val started=System.nanoTime()
                repeat(size) {i->
                    val imported=library.import(sources) {_,_->}
                    assertEquals(0,imported.failed);assertEquals(10,imported.photos.size)
                    library.save(null,"测试地点 $i",20.0+(i%100)*0.25,90.0+(i/100)*2.0,null,"仅用于测试","自主生成测试 JPEG", "2026-11-02",imported.photos)
                }
                val importMs=(System.nanoTime()-started)/1e6
                val queryStart=System.nanoTime();val places=library.places();val queryMs=(System.nanoTime()-queryStart)/1e6
                assertEquals(size,places.size);assertEquals(size*10,places.sumOf {it.photoCount})
                assertEquals(size*10,File(context.filesDir,"library/originals").listFiles()!!.size)
                assertEquals(size*10,File(context.filesDir,"library/thumbnails").listFiles()!!.size)
                val last=library.records(places.last().id).single();assertEquals(10,last.photos.size)
                results.put(JSONObject().put("locations",size).put("realJpegFiles",size*10).put("importAndSaveMs",importMs).put("queryPlacesMs",queryMs).put("libraryBytes",File(context.filesDir,"library").walkTopDown().filter {it.isFile}.sumOf {it.length()}))
            } finally {library.close()}
        }
        File("build/reports/photomap-stress.json").apply {parentFile!!.mkdirs();writeText(JSONObject().put("environment","Windows Robolectric API28 native image decoding; NOT Android device performance").put("results",results).toString(2))}
        Unit
    }
}
