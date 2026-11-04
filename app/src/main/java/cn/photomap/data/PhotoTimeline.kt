package cn.photomap.data

import cn.photomap.core.MapViewport
import cn.photomap.BuildConfig
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class PhotoPoint(val latitude: Double, val longitude: Double) {
    val inCoverage: Boolean get() = if (BuildConfig.ONLINE_MAPS) latitude in -90.0..90.0 && longitude in -180.0..180.0
        else MapViewport.inCoverage(latitude, longitude)
}

data class PhotoPlacement(val photo: Photo, val point: PhotoPoint, val placeName: String = "")

fun Photo.exifPoint(): PhotoPoint? {
    val metadata = JSONObject(exif)
    return PhotoPoint(metadata.optDouble("gps_latitude"), metadata.optDouble("gps_longitude"))
        .takeIf { it.inCoverage }
}

data class DatedPhoto(val photo: Photo, val record: Record, val placeName: String)
data class PhotoDay(val date: LocalDate, val photos: List<DatedPhoto>)

fun anniversaryPage(days: List<PhotoDay>, anniversary: LocalDate): Int =
    days.indices.minWithOrNull(compareBy({ abs(ChronoUnit.DAYS.between(anniversary, days[it].date)) }, { days[it].date })) ?: 0
