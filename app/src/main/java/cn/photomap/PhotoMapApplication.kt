package cn.photomap

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import cn.photomap.data.Library
import cn.photomap.map.MapPackages

class PhotoMapApplication : Application() {
    val startupDiagnostics by lazy {StartupDiagnostics(this)}
    val library by lazy {Library(this)}
    val mapPackages by lazy {MapPackages(this)}
    override fun onCreate() {
        super.onCreate()
        startupDiagnostics.installCrashRecorder()
        Coil.setImageLoader(ImageLoader.Builder(this)
            .memoryCache {MemoryCache.Builder(this).maxSizeBytes(32*1024*1024).build()}
            .diskCache {DiskCache.Builder().directory(cacheDir.resolve("photo-previews")).maxSizeBytes(96L*1024*1024).build()}
            .crossfade(true).build())
    }
}
