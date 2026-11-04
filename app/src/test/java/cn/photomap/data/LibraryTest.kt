package cn.photomap.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import androidx.exifinterface.media.ExifInterface
import cn.photomap.core.Archive
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryTest {
    private lateinit var context: Context
    private lateinit var library: Library
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        listOf("library","library-previous","restore-staging").forEach {File(context.filesDir,it).deleteRecursively()}
        library=Library(context)
    }
    @After fun close() = runBlocking {library.close()}
    private fun image(name: String): Uri {
        val file=File(context.cacheDir,name)
        val bitmap=Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(65,115,142))}
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}; bitmap.recycle()
        return Uri.fromFile(file)
    }
    @Test fun createMultipleRecordsBackupRestoreAndDeleteNeverDeletesAlbum()=runBlocking {
        val album=image("album-original.jpg")
        val first=library.import(listOf(album)) {_,_->}
        assertEquals(1,first.photos.size)
        val place=library.save(null,"湖边",30.0,120.0,null,"晨光","逆光", "2026-11-02",first.photos)
        val second=library.import(listOf(album)) {_,_->}
        assertEquals(1,second.duplicates)
        library.save(place,"湖边",30.0,120.0,null,"晚霞","", "2026-11-03",second.photos)
        assertEquals(2,library.places().single().photoCount)
        assertEquals(2,library.records(place).size)
        val backup=File(context.cacheDir,"backup.zip"); library.backup(Uri.fromFile(backup))
        File("build/reports/emulator-fixture.zip").apply {parentFile.mkdirs();backup.copyTo(this,overwrite=true)}
        library.removePlace(place); assertTrue(library.places().isEmpty()); assertTrue(File(album.path!!).isFile)
        library.restore(Uri.fromFile(backup))
        assertEquals(2,library.places().single().photoCount)
        assertTrue(library.file(library.records(place).first().photos.first().thumbnail).isFile)
        library.removePhoto(library.records(place).first().photos.first().id)
        assertEquals(1,library.places().single().photoCount)
        assertTrue(File(album.path!!).isFile)
    }
    @Test fun corruptRestoreLeavesExistingLibraryUntouched()=runBlocking {
        val photo=library.import(listOf(image("keep.jpg"))) {_,_->}.photos
        library.save(null,"保留",20.0,100.0,null,"","","2026-11-02",photo)
        val invalid=File(context.cacheDir,"invalid.zip").apply {writeText("not a zip")}
        assertTrue(runCatching {library.restore(Uri.fromFile(invalid))}.isFailure)
        assertEquals("保留",library.places().single().name)
        assertTrue(library.file(photo.single().original).isFile)
    }
    @Test fun corruptImageDoesNotAbortBatchAndEmptyRecordIsRejected()=runBlocking {
        val bad=File(context.cacheDir,"broken.jpg").apply {writeText("broken")}
        val imported=library.import(listOf(Uri.fromFile(bad),image("good.jpg"))) {_,_->}
        assertEquals(1,imported.failed);assertEquals(1,imported.photos.size)
        assertTrue(runCatching {library.save(null,"",30.0,110.0,null,"","","2026-11-02",emptyList())}.isFailure)
        assertTrue(library.places().isEmpty())
        library.discard(imported.photos);assertFalse(library.file(imported.photos.single().original).exists())
    }
    @Test fun editCoordinatesRecordAndPhotoThenDeleteLastRecord()=runBlocking {
        val imported=library.import(listOf(image("edit.jpg"))) {_,_->}
        val id=library.save(null,"原地点",30.0,110.0,null,"原标题","", "2026-11-02",imported.photos)
        val record=library.records(id).single()
        library.save(id,"新地点",31.123456,111.654321,record.id,"新标题","新心得","2025-01-01",emptyList())
        val place=library.places().single();assertEquals("新地点",place.name);assertEquals(31.123456,place.latitude,0.0000001)
        val updated=library.records(id).single();assertEquals("新标题",updated.title);assertEquals("2025-01-01",updated.date)
        library.editPhoto(updated.photos.single(),"照片标题","照片心得","2024-02-03","ISO 200 / f2.8")
        assertEquals("照片标题",library.records(id).single().photos.single().title)
        assertTrue(library.records(id).single().photos.single().exif.contains("ISO 200"))
        library.removeRecord(record.id);assertTrue(library.places().isEmpty());assertFalse(library.file(imported.photos.single().original).exists())
    }
    @Test fun exifDateGpsAndRotationArePreserved()=runBlocking {
        val uri=image("exif.jpg");val source=File(uri.path!!)
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2024:05:06 07:08:09")
            setAttribute(ExifInterface.TAG_MODEL,"PhotoMap Test Camera")
            setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(30.25,120.75);saveAttributes()
        }
        val originalHash=Archive.hash(source)
        val photo=library.import(listOf(uri)) {_,_->}.photos.single()
        assertEquals("2024-05-06",photo.date);assertTrue(photo.exif.contains("PhotoMap Test Camera"));assertTrue(photo.exif.contains("30.25"))
        val preview=android.graphics.BitmapFactory.decodeFile(library.file(photo.thumbnail).path)
        assertTrue(preview.height>preview.width);preview.recycle()
        assertEquals(originalHash,Archive.hash(source));assertEquals(originalHash,Archive.hash(library.file(photo.original)))
    }
    @Test fun validZipWithInvalidDatabaseCannotReplaceWorks()=runBlocking {
        val photo=library.import(listOf(image("safe.jpg"))) {_,_->}.photos
        library.save(null,"安全记录",30.0,110.0,null,"","","2026-11-02",photo)
        val fake=File(context.cacheDir,"fake-db").apply {mkdirs()}
        SQLiteDatabase.openOrCreateDatabase(File(fake,"photomap.db"),null).use {it.version=1}
        val zip=File(context.cacheDir,"fake-db.zip");zip.outputStream().use {Archive.write(fake,listOf("photomap.db"),it)}
        assertTrue(runCatching {library.restore(Uri.fromFile(zip))}.isFailure)
        assertEquals("安全记录",library.places().single().name)
    }
    @Test fun restartAfterInterruptedDirectorySwitchRecoversOriginalData()=runBlocking {
        val photo=library.import(listOf(image("restart.jpg"))) {_,_->}.photos
        library.save(null,"恢复原数据",30.0,110.0,null,"","","2026-11-02",photo)
        library.close()
        assertTrue(File(context.filesDir,"library").renameTo(File(context.filesDir,"library-previous")))
        library=Library(context)
        assertEquals("恢复原数据",library.places().single().name)
    }
}
