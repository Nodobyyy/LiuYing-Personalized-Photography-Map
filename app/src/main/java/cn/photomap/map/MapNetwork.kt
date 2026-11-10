package cn.photomap.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Tile transport has no upload path. Photos/records are local GeoJSON and files. */
object MapNetwork {
    const val CACHE_MIB = 256L
    private const val PLANET = "https://tiles.openfreemap.org/planet"
    private const val INDEX_MAX_AGE_MS = 60 * 60 * 1000L
    private val calls=AtomicInteger()
    private val denied=AtomicInteger()
    private val tileCalls = AtomicInteger()
    private val indexLock = Any()
    private var configured = false
    private val vectorPath = Regex("/planet/[A-Za-z0-9_-]+/[0-9]{1,2}/[0-9]+/[0-9]+[.]pbf")
    private val satellitePath = Regex("/wmts/1[.]0[.]0/s2cloudless_3857/default/g/[0-9]+/[0-9]+/[0-9]+[.]jpg")
    fun allowed(method:String,scheme:String,host:String,path:String,hasQuery:Boolean,hasBody:Boolean):Boolean =
        (method == "GET" || method == "HEAD") && scheme=="https" && !hasBody && !hasQuery && when(host) {
            "tiles.openfreemap.org"->path=="/planet" || path.matches(vectorPath)
            "tiles.maps.eox.at"->path.matches(satellitePath)
            else->false
        }
    @Synchronized
    fun configure(context: Context) {
        if (configured) return
        val index = context.getSharedPreferences("online-tile-index", Context.MODE_PRIVATE)
        val metadataCache = okhttp3.Interceptor { chain ->
            val request = chain.request()
            if (request.url.toString() != PLANET || request.method != "GET" || request.body != null) {
                chain.proceed(request)
            } else synchronized(indexLock) {
                val saved = index.getString("json", null)
                val age = System.currentTimeMillis() - index.getLong("time", 0)
                fun cached(json: String) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").header("Cache-Control", "no-cache")
                    .body(json.toResponseBody("application/json".toMediaType())).build()
                if (saved != null && age in 0 until INDEX_MAX_AGE_MS) cached(saved)
                else {
                    try {
                        val response = chain.proceed(request.newBuilder().removeHeader("If-None-Match").removeHeader("If-Modified-Since").build())
                        if (response.isSuccessful) {
                            val json = try {
                                response.peekBody(512 * 1024).string()
                            } catch (error: IOException) {
                                response.close()
                                throw error
                            }
                            if (validTileIndex(json)) index.edit().putString("json", json).putLong("time", System.currentTimeMillis()).apply()
                            response
                        } else if (saved != null) {
                            response.close()
                            cached(saved)
                        } else response
                    } catch (error: IOException) {
                        if (saved != null) cached(saved) else throw error
                    }
                }
            }
        }
        val check=okhttp3.Interceptor {chain->val r=chain.request();val u=r.url
            if(!allowed(r.method,u.scheme,u.host,u.encodedPath,u.query!=null,r.body!=null)){denied.incrementAndGet();throw IOException("Only approved map GET/HEAD requests are permitted")}
            calls.incrementAndGet()
            if (u.encodedPath != "/planet") tileCalls.incrementAndGet()
            chain.proceed(r)
        }
        val client = OkHttpClient.Builder()
            // Avoid having too many tile transfers compete on a slow connection.
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 6 })
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(metadataCache)
            .addNetworkInterceptor(check)
            .build()
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        HttpRequestUtil.setOkHttpClient(client)
        configured = true
        if (connected(context)) client.newCall(Request.Builder().url(PLANET).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = Unit
            override fun onResponse(call: okhttp3.Call, response: Response) = response.close()
        })
    }
    private fun validTileIndex(text: String): Boolean = runCatching {
        val tiles = JSONObject(text).getJSONArray("tiles")
        tiles.length() > 0 && (0 until tiles.length()).all { index ->
            val tile = tiles.getString(index)
            tile.matches(Regex("https://tiles[.]openfreemap[.]org/planet/[A-Za-z0-9_-]+/\\{z}/\\{x}/\\{y}[.]pbf"))
        }
    }.getOrDefault(false)
    fun configureCache(context:Context) {
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(CACHE_MIB*1024*1024,object:OfflineManager.FileSourceCallback {override fun onSuccess(){};override fun onError(message:String){}})
    }
    fun clearCache(context:Context,done:(String)->Unit) {
        context.getSharedPreferences("online-tile-index", Context.MODE_PRIVATE).edit().clear().apply()
        val manager=OfflineManager.getInstance(context)
        manager.clearAmbientCache(object:OfflineManager.FileSourceCallback {
            override fun onSuccess(){manager.packDatabase(object:OfflineManager.FileSourceCallback {override fun onSuccess(){done("地图缓存已清理，照片和作品不受影响")};override fun onError(message:String){done("地图缓存已清理")}})}
            override fun onError(message:String){done("地图缓存清理失败，请稍后重试")}
        })
    }
    fun connected(context:Context):Boolean {val cm=context.getSystemService(ConnectivityManager::class.java);return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}
    fun audit()=calls.get() to denied.get()
    fun tileRequests() = tileCalls.get()
}
