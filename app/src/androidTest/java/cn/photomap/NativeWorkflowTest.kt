package cn.photomap

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.photomap.data.Library
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** All test writes are isolated under a fresh cache subdirectory; never delete user works. */
@RunWith(AndroidJUnit4::class)
class NativeWorkflowTest {
    @Test fun nativeImportExifEditRestartBackupAndRestore()=runBlocking {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox=File(target.cacheDir,"native-test-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object: ContextWrapper(target) {override fun getFilesDir(): File=sandbox}
        var library=Library(isolated)
        try {
            val album=File(sandbox,"album-original.jpg")
            val bitmap=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(60,120,155))}
            album.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,92,it)};bitmap.recycle()
            ExifInterface(album).apply {setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2024:01:02 03:04:05");setAttribute(ExifInterface.TAG_MODEL,"Native Test Camera");setAttribute(ExifInterface.TAG_ORIENTATION,"6");setLatLong(30.25,120.75);saveAttributes()}
            val imported=library.import(listOf(Uri.fromFile(album))) {_,_->}
            assertEquals(0,imported.failed);assertEquals("2024-01-02",imported.photos.single().date)
            assertTrue(imported.photos.single().exif.contains("Native Test Camera"))
            val thumbnail=android.graphics.BitmapFactory.decodeFile(library.file(imported.photos.single().thumbnail).path)
            assertTrue(thumbnail.height>thumbnail.width);thumbnail.recycle()
            val place=library.save(null,"原生测试地点",30.0,120.0,null,"晨光","本地测试","2024-01-02",imported.photos)
            val duplicate=library.import(listOf(Uri.fromFile(album))) {_,_->};assertEquals(1,duplicate.duplicates)
            library.save(place,"原生测试地点",30.0,120.0,null,"第二次拍摄","","2024-01-03",duplicate.photos)
            assertEquals(2,library.places().single().photoCount)
            val record=library.records(place).first()
            library.save(place,"更新地点",31.0,121.0,record.id,"更新标题","心得","2024-02-03",emptyList())
            library.editPhoto(record.photos.single(),"照片标题","照片心得","2024-03-04")
            library.close();library=Library(isolated)
            assertEquals("更新地点",library.places().single().name);assertEquals(2,library.records(place).size)
            val backup=File(sandbox,"backup.zip");library.backup(Uri.fromFile(backup))
            val bad=File(sandbox,"corrupt.zip").apply {writeText("broken zip")}
            assertTrue(runCatching {library.restore(Uri.fromFile(bad))}.isFailure)
            assertEquals(2,library.places().single().photoCount)
            library.removePlace(place);assertTrue(library.places().isEmpty());assertTrue(album.isFile)
            library.restore(Uri.fromFile(backup));assertEquals(2,library.places().single().photoCount)
            assertTrue(library.file(library.records(place).first().photos.first().original).isFile)
            assertTrue(album.isFile)
        } finally {library.close();sandbox.deleteRecursively()}
    }
}
