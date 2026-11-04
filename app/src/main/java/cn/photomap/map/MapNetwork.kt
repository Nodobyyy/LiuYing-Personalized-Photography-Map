package cn.photomap.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Tile transport has no upload path. Photos/records are local GeoJSON and files. */
object MapNetwork {
    private val calls=AtomicInteger()
    private val denied=AtomicInteger()
    fun allowed(method:String,scheme:String,host:String,path:String,hasQuery:Boolean,hasBody:Boolean):Boolean =
        method in listOf("GET","HEAD") && scheme=="https" && !hasBody && !hasQuery && when(host) {
            "tiles.openfreemap.org"->path=="/planet" || path.matches(Regex("/planet/[A-Za-z0-9_-]+/[0-9]{1,2}/[0-9]+/[0-9]+[.]pbf"))
            "tiles.maps.eox.at"->path.matches(Regex("/wmts/1[.]0[.]0/s2cloudless_3857/default/g/[0-9]+/[0-9]+/[0-9]+[.]jpg"))
            else->false
        }
    fun configure(context:Context) {
        val check=okhttp3.Interceptor {chain->val r=chain.request();val u=r.url
            if(!allowed(r.method,u.scheme,u.host,u.encodedPath,u.query!=null,r.body!=null)){denied.incrementAndGet();throw IOException("Only approved map GET/HEAD requests are permitted")}
            calls.incrementAndGet();chain.proceed(r)
        }
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().addNetworkInterceptor(check).build())
    }
    fun configureCache(context:Context) {
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(128L*1024*1024,object:OfflineManager.FileSourceCallback {override fun onSuccess(){};override fun onError(message:String){}})
    }
    fun clearCache(context:Context,done:(String)->Unit) {
        val manager=OfflineManager.getInstance(context)
        manager.clearAmbientCache(object:OfflineManager.FileSourceCallback {
            override fun onSuccess(){manager.packDatabase(object:OfflineManager.FileSourceCallback {override fun onSuccess(){done("地图缓存已清理，照片和作品不受影响")};override fun onError(message:String){done("地图缓存已清理")}})}
            override fun onError(message:String){done("地图缓存清理失败，请稍后重试")}
        })
    }
    fun connected(context:Context):Boolean {val cm=context.getSystemService(ConnectivityManager::class.java);return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}
    fun audit()=calls.get() to denied.get()
}
