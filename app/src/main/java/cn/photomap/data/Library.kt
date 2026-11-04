package cn.photomap.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import cn.photomap.core.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class Place(val id: String, val name: String, val latitude: Double, val longitude: Double, val cover: Photo, val photoCount: Int)
data class Record(val id: String, val locationId: String, val title: String, val note: String, val date: String, val photos: List<Photo>)
data class Photo(val id: String, val recordId: String, val original: String, val thumbnail: String, val filename: String,
    val width: Int, val height: Int, val shotAt: String, val exif: String, val hash: String,
    val title: String = "", val note: String = "", val date: String = "")
data class Imported(val photos: List<Photo>, val failed: Int, val duplicates: Int)

class Library(private val context: Context) {
    private val latitudeLimit = if (cn.photomap.BuildConfig.ONLINE_MAPS) 90.0 else 85.0
    private val lock = Mutex()
    private val root = File(context.filesDir, "library")
    private val old = File(context.filesDir, "library-previous")
    private var helper: Db
    private var cleaned=false
    init {
        if (!root.exists() && old.exists()) check(old.renameTo(root))
        root.mkdirs()
        helper = Db(context, File(root, "photomap.db").absolutePath)
    }
    private class Db(context: Context, path: String) : SQLiteOpenHelper(context, path, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE locations(id TEXT PRIMARY KEY,name TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,coordinate_system TEXT NOT NULL DEFAULT 'WGS84',cover_photo_id TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE records(id TEXT PRIMARY KEY,location_id TEXT NOT NULL REFERENCES locations(id) ON DELETE CASCADE,title TEXT NOT NULL,note TEXT NOT NULL,shot_date TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE photos(id TEXT PRIMARY KEY,record_id TEXT NOT NULL REFERENCES records(id) ON DELETE CASCADE,original_path TEXT NOT NULL,thumbnail_path TEXT NOT NULL,original_filename TEXT NOT NULL,width INTEGER NOT NULL,height INTEGER NOT NULL,shot_at TEXT NOT NULL,exif_json TEXT NOT NULL,hash TEXT NOT NULL,title TEXT NOT NULL DEFAULT '',note TEXT NOT NULL DEFAULT '',shot_date TEXT NOT NULL DEFAULT '',sort_order INTEGER NOT NULL,created_at INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX location_coordinates ON locations(latitude,longitude)")
            db.execSQL("CREATE INDEX record_location ON records(location_id)")
            db.execSQL("CREATE INDEX record_date ON records(shot_date)")
            db.execSQL("CREATE INDEX photo_record ON photos(record_id,sort_order)")
            db.execSQL("CREATE INDEX photo_date ON photos(shot_at)")
            db.execSQL("CREATE INDEX photo_hash ON photos(hash)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("不支持的数据库版本") }
    }
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }
    fun file(relative: String): File {
        require(relative.matches(Regex("(originals|thumbnails)/[a-zA-Z0-9.-]+")))
        return File(root, relative)
    }
    private fun values(vararg fields: Pair<String, Any?>) = ContentValues().apply {
        fields.forEach { (k,v) -> when(v) { null -> putNull(k); is Int -> put(k,v); is Long -> put(k,v); is Double -> put(k,v); else -> put(k,v.toString()) } }
    }
    private fun photo(cursor: android.database.Cursor): Photo {
        fun s(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
        return Photo(s("id"),s("record_id"),s("original_path"),s("thumbnail_path"),s("original_filename"),cursor.getInt(cursor.getColumnIndexOrThrow("width")),cursor.getInt(cursor.getColumnIndexOrThrow("height")),s("shot_at"),s("exif_json"),s("hash"),s("title"),s("note"),s("shot_date"))
    }
    private fun photos(db: SQLiteDatabase, record: String? = null): List<Photo> {
        val result = mutableListOf<Photo>()
        db.query("photos",null,if(record==null) null else "record_id=?",record?.let { arrayOf(it) },null,null,"sort_order,created_at").use { while(it.moveToNext()) result.add(photo(it)) }
        return result
    }
    suspend fun places(): List<Place> = io {
        val db = helper.readableDatabase
        val result = mutableListOf<Place>()
        db.rawQuery("""SELECT l.*, (SELECT p.id FROM photos p JOIN records r ON r.id=p.record_id WHERE r.location_id=l.id ORDER BY p.created_at,p.id LIMIT 1) cover, (SELECT count(*) FROM photos p JOIN records r ON r.id=p.record_id WHERE r.location_id=l.id) photo_count FROM locations l ORDER BY l.created_at""",null).use { c ->
            while(c.moveToNext()) {
                val cover = c.getString(c.getColumnIndexOrThrow("cover")) ?: continue
                db.rawQuery("SELECT * FROM photos WHERE id=?",arrayOf(cover)).use { p -> if(p.moveToFirst()) result.add(Place(c.getString(0),c.getString(1),c.getDouble(2),c.getDouble(3),photo(p),c.getInt(c.getColumnIndexOrThrow("photo_count")))) }
            }
        }
        result
    }
    suspend fun records(location: String): List<Record> = io {
        val db = helper.readableDatabase
        val result = mutableListOf<Record>()
        db.rawQuery("SELECT id,title,note,shot_date FROM records WHERE location_id=? ORDER BY shot_date DESC,created_at DESC",arrayOf(location)).use { c -> while(c.moveToNext()) result.add(Record(c.getString(0),location,c.getString(1),c.getString(2),c.getString(3),photos(db,c.getString(0)))) }
        result
    }
    suspend fun photoDays(): List<PhotoDay> = io {
        val days = sortedMapOf<LocalDate, MutableList<DatedPhoto>>(reverseOrder())
        val sql = """SELECT p.*, r.location_id, r.title AS record_title, r.note AS record_note,
            r.shot_date AS record_date, l.name AS place_name
            FROM photos p JOIN records r ON p.record_id=r.id JOIN locations l ON r.location_id=l.id
            ORDER BY p.shot_at, p.created_at, p.sort_order, p.id"""
        helper.readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val photo = photo(cursor)
                val record = Record(photo.recordId, cursor.getString(cursor.getColumnIndexOrThrow("location_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("record_title")),
                    cursor.getString(cursor.getColumnIndexOrThrow("record_note")),
                    cursor.getString(cursor.getColumnIndexOrThrow("record_date")), emptyList())
                val date = runCatching { LocalDate.parse(photo.date.ifBlank { record.date }) }.getOrNull() ?: continue
                val placeName = cursor.getString(cursor.getColumnIndexOrThrow("place_name"))
                days.getOrPut(date) { mutableListOf() }.add(DatedPhoto(photo, record, placeName))
            }
        }
        days.map { (date, photos) -> PhotoDay(date, photos) }
    }
    suspend fun cleanupAbandonedFiles() = io {
        if(cleaned) return@io
        val used = photos(helper.readableDatabase).flatMap { listOf(it.original,it.thumbnail) }.toSet()
        listOf("originals","thumbnails").forEach { folder -> File(root,folder).listFiles()?.filter { "$folder/${it.name}" !in used }?.forEach { it.delete() } }
        File(context.filesDir,"restore-staging").deleteRecursively()
        old.deleteRecursively()
        cleaned=true
    }
    suspend fun close() = io {helper.close()}
    suspend fun import(uris: List<Uri>, onProgress: (Int,Int) -> Unit): Imported = io {
        val db = helper.readableDatabase
        val result = mutableListOf<Photo>(); var failures = 0; var duplicates = 0
        uris.forEachIndexed { index, uri ->
            val id = UUID.randomUUID().toString()
            val original = "originals/$id.photo"; val thumbnail = "thumbnails/$id.jpg"
            val originalFile = file(original); originalFile.parentFile!!.mkdirs()
            try {
                var name = if (cn.photomap.BuildConfig.ONLINE_MAPS && uri.scheme == "file") uri.lastPathSegment ?: "photo" else "photo"
                context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) name=it.getString(0) ?: name }
                var size = 0L
                context.contentResolver.openInputStream(uri)!!.use { input -> originalFile.outputStream().use { output ->
                    val bytes = ByteArray(65536)
                    while(true) { val n=input.read(bytes); if(n<0) break; size+=n; require(size<=512L*1024*1024 && root.usableSpace>32L*1024*1024); output.write(bytes,0,n) }
                } }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(originalFile.path,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<2_000_000_000L)
                val exif = runCatching { ExifInterface(originalFile) }.getOrNull()
                val tags = linkedMapOf("camera_make" to ExifInterface.TAG_MAKE,"camera_model" to ExifInterface.TAG_MODEL,"lens_model" to ExifInterface.TAG_LENS_MODEL,"focal_length" to ExifInterface.TAG_FOCAL_LENGTH,"aperture" to ExifInterface.TAG_F_NUMBER,"shutter_speed" to ExifInterface.TAG_EXPOSURE_TIME,"iso" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,"orientation" to ExifInterface.TAG_ORIENTATION)
                val meta = JSONObject()
                tags.forEach { (key, tag) -> exif?.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { meta.put(key,it) } }
                exif?.latLong?.let { meta.put("gps_latitude",it[0]); meta.put("gps_longitude",it[1]); meta.put("gps_coordinate_system","WGS84") }
                val shot = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif?.getAttribute(ExifInterface.TAG_DATETIME) ?: ""
                val hash = Archive.hash(originalFile)
                db.rawQuery("SELECT 1 FROM photos WHERE hash=? LIMIT 1",arrayOf(hash)).use { if(it.moveToFirst() || result.any { p->p.hash==hash }) duplicates++ }
                createThumbnail(originalFile,file(thumbnail),exif)
                val date=shot.take(10).replace(':','-').takeIf {runCatching {LocalDate.parse(it)}.isSuccess} ?: ""
                result.add(Photo(id,"",original,thumbnail,name,bounds.outWidth,bounds.outHeight,shot,meta.toString(),hash,date=date))
            } catch (_: Exception) { originalFile.delete(); file(thumbnail).delete(); failures++ }
            onProgress(index+1,uris.size)
        }
        Imported(result,failures,duplicates)
    }
    private fun createThumbnail(source: File, target: File, exif: ExifInterface? = null) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(source.path,bounds)
        var sample=1; while(maxOf(bounds.outWidth,bounds.outHeight)/sample>512) sample*=2
        val bitmap=BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("无法解码图片")
        val info = exif ?: runCatching { ExifInterface(source) }.getOrNull()
        val matrix=Matrix().apply { if(info?.isFlipped==true) postScale(-1f,1f); postRotate((info?.rotationDegrees ?: 0).toFloat()) }
        val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val scale = 256f/maxOf(oriented.width,oriented.height)
        val small=Bitmap.createScaledBitmap(oriented,maxOf(1,(oriented.width*scale).toInt()),maxOf(1,(oriented.height*scale).toInt()),true)
        target.parentFile!!.mkdirs(); target.outputStream().use { check(small.compress(Bitmap.CompressFormat.JPEG,85,it)) }
        if(small!==oriented) small.recycle(); if(oriented!==bitmap) oriented.recycle(); bitmap.recycle()
    }
    suspend fun discard(items: List<Photo>) = io { val saved=photos(helper.readableDatabase).map { it.id }.toSet(); items.filter { it.id !in saved }.forEach { file(it.original).delete(); file(it.thumbnail).delete() } }
    suspend fun saveLocatedPhotos(photos: List<PhotoPlacement>) = io {
        if (photos.isEmpty()) return@io
        require(photos.map { it.photo.id }.distinct().size == photos.size)
        photos.forEach { (photo, point) ->
            require(point.inCoverage && file(photo.original).isFile && file(photo.thumbnail).isFile)
            LocalDate.parse(photo.date)
        }
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val records = linkedMapOf<Pair<String, String>, MutableList<Photo>>()
            for ((photo, point, placeName) in photos) {
                // Only consider a small coordinate window, then compare actual distances in metres.
                val location = db.rawQuery("""SELECT id, latitude, longitude FROM locations
                    WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?""",
                    arrayOf((point.latitude - .0004).toString(), (point.latitude + .0004).toString(),
                        (point.longitude - .0005).toString(), (point.longitude + .0005).toString())).use { cursor ->
                    val metres = FloatArray(1)
                    var nearest = 30f
                    var match: String? = null
                    while (cursor.moveToNext()) {
                        android.location.Location.distanceBetween(point.latitude, point.longitude,
                            cursor.getDouble(1), cursor.getDouble(2), metres)
                        if (metres[0] < nearest) { nearest = metres[0]; match = cursor.getString(0) }
                    }
                    match
                } ?: UUID.randomUUID().toString().also { id ->
                    val name = placeName.trim().take(200).ifBlank {
                        "拍摄地点 %.4f, %.4f".format(java.util.Locale.ROOT, point.latitude, point.longitude)
                    }
                    db.insertOrThrow("locations", null, values("id" to id, "name" to name, "latitude" to point.latitude,
                        "longitude" to point.longitude, "created_at" to now, "updated_at" to now))
                }
                records.getOrPut(location to photo.date) { mutableListOf() }.add(photo)
            }
            records.forEach { (key, group) ->
                val (location, date) = key
                val record = UUID.randomUUID().toString()
                db.insertOrThrow("records", null, values("id" to record, "location_id" to location, "title" to "照片导入",
                    "note" to "", "shot_date" to date, "created_at" to now, "updated_at" to now))
                insertPhotos(db, group, record, 0, now)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    private fun insertPhotos(db: SQLiteDatabase, added: List<Photo>, record: String, offset: Int, now: Long) {
        added.forEachIndexed { index, p ->
            db.insertOrThrow("photos", null, values("id" to p.id, "record_id" to record, "original_path" to p.original,
                "thumbnail_path" to p.thumbnail, "original_filename" to p.filename, "width" to p.width, "height" to p.height,
                "shot_at" to p.shotAt, "exif_json" to p.exif, "hash" to p.hash, "title" to p.title, "note" to p.note,
                "shot_date" to p.date, "sort_order" to offset + index, "created_at" to now))
        }
    }
    suspend fun save(placeId: String?, name: String, latitude: Double, longitude: Double, recordId: String?, title: String, note: String, date: String, added: List<Photo>): String = io {
        require(latitude in -latitudeLimit..latitudeLimit && longitude in -180.0..180.0); LocalDate.parse(date)
        require(name.length<=200 && title.length<=500 && note.length<=100000)
        val db=helper.writableDatabase; val location=placeId ?: UUID.randomUUID().toString(); val record=recordId ?: UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        val existing=if(recordId==null) emptyList() else photos(db,recordId)
        require(existing.isNotEmpty() || added.isNotEmpty()) { "请至少导入一张照片" }
        require(added.all { file(it.original).isFile && file(it.thumbnail).isFile })
        db.beginTransaction()
        try {
            val locationValues=values("name" to name.ifBlank { "未命名地点" },"latitude" to latitude,"longitude" to longitude,"updated_at" to now)
            if(placeId==null) { locationValues.put("id",location); locationValues.put("created_at",now); db.insertOrThrow("locations",null,locationValues) }
            else check(db.update("locations",locationValues,"id=?",arrayOf(location))==1)
            val recordValues=values("title" to title,"note" to note,"shot_date" to date,"updated_at" to now)
            if(recordId==null) { recordValues.put("id",record); recordValues.put("location_id",location); recordValues.put("created_at",now); db.insertOrThrow("records",null,recordValues) }
            else check(db.update("records",recordValues,"id=? AND location_id=?",arrayOf(record,location))==1)
            insertPhotos(db, added, record, existing.size, now)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        location
    }
    suspend fun editPhoto(p: Photo, title: String, note: String, date: String, manualParameters: String?=null,
                          cameraOverrides: Map<String, String>? = null) = io {
        if(date.isNotBlank()) LocalDate.parse(date)
        require(title.length<=500 && note.length<=100000 && (manualParameters?.length ?: 0)<=2500)
        val content=values("title" to title,"note" to note,"shot_date" to date)
        if (manualParameters != null || cameraOverrides != null) {
            val metadata = JSONObject(p.exif)
            if (manualParameters != null) metadata.put("manual_parameters", manualParameters)
            if (cameraOverrides != null) metadata.withCameraOverrides(cameraOverrides)
            content.put("exif_json", metadata.toString())
        }
        helper.writableDatabase.update("photos",content,"id=?",arrayOf(p.id))
    }
    suspend fun removePhoto(id: String) = io {
        val db=helper.writableDatabase
        val target=photos(db).first { it.id==id }
        db.beginTransaction(); try {
            db.delete("photos","id=?",arrayOf(id)); db.execSQL("DELETE FROM records WHERE NOT EXISTS(SELECT 1 FROM photos WHERE record_id=records.id)"); db.execSQL("DELETE FROM locations WHERE NOT EXISTS(SELECT 1 FROM records WHERE location_id=locations.id)"); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        file(target.original).delete(); file(target.thumbnail).delete()
    }
    suspend fun removePlace(id: String) = io {
        val db=helper.writableDatabase
        val toDelete=mutableListOf<Photo>(); db.rawQuery("SELECT p.* FROM photos p JOIN records r ON r.id=p.record_id WHERE r.location_id=?",arrayOf(id)).use { c->while(c.moveToNext()) toDelete.add(photo(c)) }
        db.delete("locations","id=?",arrayOf(id)); toDelete.forEach { file(it.original).delete(); file(it.thumbnail).delete() }
    }
    suspend fun removeRecord(id: String) = io {
        val db=helper.writableDatabase; val toDelete=photos(db,id)
        db.beginTransaction(); try {
            db.delete("records","id=?",arrayOf(id)); db.execSQL("DELETE FROM locations WHERE NOT EXISTS(SELECT 1 FROM records WHERE location_id=locations.id)"); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        toDelete.forEach { file(it.original).delete(); file(it.thumbnail).delete() }
    }
    suspend fun backup(uri: Uri) = io {
        val db=helper.writableDatabase
        db.rawQuery("PRAGMA wal_checkpoint(FULL)",null).close()
        val originals=photos(db).map { it.original }.distinct()
        helper.close()
        try { context.contentResolver.openOutputStream(uri,"wt")!!.use { Archive.write(root,listOf("photomap.db")+originals,it) } }
        finally { helper=Db(context,File(root,"photomap.db").absolutePath) }
    }
    suspend fun restore(uri: Uri) = io {
        val staging=File(context.filesDir,"restore-staging"); staging.deleteRecursively()
        require(context.filesDir.usableSpace>64L*1024*1024) {"恢复所需可用空间不足"}
        try {
            val names=context.contentResolver.openInputStream(uri)!!.use { Archive.extract(it,staging) }
            require(names.all { it=="photomap.db" || it.matches(Regex("originals/[a-zA-Z0-9-]+\\.photo")) })
            val dbFile=File(staging,"photomap.db"); require(dbFile.isFile && dbFile.length()<=256L*1024*1024)
            val staged=SQLiteDatabase.openDatabase(dbFile.path,null,SQLiteDatabase.OPEN_READWRITE)
            try {
                require(staged.version==1)
                // Validate every column used by the app before replacing the live directory.
                listOf(
                    "SELECT id,name,latitude,longitude,coordinate_system,cover_photo_id,created_at,updated_at FROM locations LIMIT 0",
                    "SELECT id,location_id,title,note,shot_date,created_at,updated_at FROM records LIMIT 0",
                    "SELECT id,record_id,original_path,thumbnail_path,original_filename,width,height,shot_at,exif_json,hash,title,note,shot_date,sort_order,created_at FROM photos LIMIT 0"
                ).forEach { staged.rawQuery(it,null).use { c->c.count } }
                staged.rawQuery("PRAGMA quick_check",null).use { require(it.moveToFirst() && it.getString(0)=="ok") }
                staged.rawQuery("PRAGMA foreign_key_check",null).use { require(!it.moveToFirst()) }
                listOf(
                    "SELECT 1 FROM photos p LEFT JOIN records r ON r.id=p.record_id WHERE r.id IS NULL LIMIT 1",
                    "SELECT 1 FROM records r LEFT JOIN locations l ON l.id=r.location_id WHERE l.id IS NULL LIMIT 1"
                ).forEach { sql->staged.rawQuery(sql,null).use {require(!it.moveToFirst())} }
                staged.rawQuery("SELECT shot_date FROM records",null).use { c->while(c.moveToNext()) LocalDate.parse(c.getString(0)) }
                val imported=photos(staged)
                require(imported.map { it.id }.distinct().size==imported.size)
                val expected=imported.map { it.original }.toSet()+"photomap.db"
                require(names.toSet()==expected)
                staged.rawQuery("SELECT latitude,longitude,coordinate_system FROM locations",null).use { c->while(c.moveToNext()) require(c.getDouble(0) in -latitudeLimit..latitudeLimit && c.getDouble(1) in -180.0..180.0 && c.getString(2)=="WGS84") }
                imported.forEach { p->
                    require(p.exif.length<=32768);JSONObject(p.exif)
                    if(p.date.isNotBlank()) LocalDate.parse(p.date)
                    require(p.original.matches(Regex("originals/[a-zA-Z0-9-]+\\.photo")) && p.thumbnail.matches(Regex("thumbnails/[a-zA-Z0-9-]+\\.jpg")))
                    val original=File(staging,p.original); require(Archive.hash(original)==p.hash)
                    createThumbnail(original,File(staging,p.thumbnail))
                }
            } finally { staged.close() }
            File(staging,"manifest.tsv").delete()
            helper.close(); old.deleteRecursively()
            check(root.renameTo(old))
            if(!staging.renameTo(root)) { check(old.renameTo(root)); helper=Db(context,File(root,"photomap.db").absolutePath); error("切换恢复数据失败") }
            helper=Db(context,File(root,"photomap.db").absolutePath); old.deleteRecursively()
        } catch(error: Exception) { staging.deleteRecursively(); throw error }
    }
}
