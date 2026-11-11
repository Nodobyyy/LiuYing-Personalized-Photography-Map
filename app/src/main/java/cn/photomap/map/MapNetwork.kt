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
    private const val SATELLITE_WARMUP_1 = "https://webst01.is.autonavi.com/appmaptile?style=6&x=13&y=6&z=4"
    private const val SATELLITE_WARMUP_2 = "https://webst02.is.autonavi.com/appmaptile?style=6&x=13&y=6&z=4"
    /** Virtual host served on-device: WGS-84 tiles rebuilt from domestic imagery (see [GcjSatellite]). */
    const val LOCAL_SATELLITE_HOST = "satellite.photomap.local"
    private const val INDEX_MAX_AGE_MS = 60 * 60 * 1000L
    private val calls=AtomicInteger()
    private val denied=AtomicInteger()
    private val tileCalls = AtomicInteger()
    private val indexLock = Any()
    private var configured = false
    private val vectorPath = Regex("/planet/[A-Za-z0-9_-]+/[0-9]{1,2}/[0-9]+/[0-9]+[.]pbf")
    private val eoxSatellitePath = Regex("/wmts/1[.]0[.]0/s2cloudless(-2020)?_3857/default/g/[0-9]+/[0-9]+/[0-9]+[.]jpg")
    private val arcgisSatellitePath = Regex("/ArcGIS/rest/services/World_Imagery/MapServer/tile/[0-9]{1,2}/[0-9]+/[0-9]+")
    private val localSatellitePath = Regex("/([0-9]{1,2})/([0-9]+)/([0-9]+)[.]jpg")
    private val autonaviHosts = setOf("webst01.is.autonavi.com", "webst02.is.autonavi.com", "webst03.is.autonavi.com", "webst04.is.autonavi.com")
    private val autonaviQuery = Regex("style=6&x=[0-9]+&y=[0-9]+&z=[0-9]{1,2}")
    /** Raw domestic tiles are shared by up to four corrected tiles; keep recent ones in memory. */
    private val sourceTiles by lazy {
        object : android.util.LruCache<String, ByteArray>(12 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
    }
    fun allowed(method:String,scheme:String,host:String,path:String,hasQuery:Boolean,hasBody:Boolean):Boolean =
        (method == "GET" || method == "HEAD") && scheme=="https" && !hasBody && !hasQuery && when(host) {
            "tiles.openfreemap.org"->path=="/planet" || path.matches(vectorPath)
            "services.arcgisonline.com", "server.arcgisonline.com" -> path.matches(arcgisSatellitePath)
            "tiles.maps.eox.at", "a.tiles.maps.eox.at", "b.tiles.maps.eox.at", "c.tiles.maps.eox.at" -> path.matches(eoxSatellitePath)
            else->false
        }
    /** Same policy, additionally permitting the fixed read-only AutoNavi satellite tile query. */
    fun allowed(method:String,scheme:String,host:String,path:String,query:String?,hasBody:Boolean):Boolean =
        if (host in autonaviHosts) (method == "GET" || method == "HEAD") && scheme == "https" && !hasBody &&
            path == "/appmaptile" && query?.matches(autonaviQuery) == true
        else allowed(method, scheme, host, path, query != null, hasBody)
    @Synchronized
    fun configure(context: Context, china: ChinaView? = null) {
        if (configured) return
        val chinaBoundary = china ?: runCatching { ChinaView(context) }.getOrNull()
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
            if(!allowed(r.method,u.scheme,u.host,u.encodedPath,u.encodedQuery,r.body!=null)){denied.incrementAndGet();throw IOException("Only approved map GET/HEAD requests are permitted")}
            calls.incrementAndGet()
            if (r.method == "GET" && u.encodedPath != "/planet") tileCalls.incrementAndGet()
            chain.proceed(r)
        }
        // Raw client: its own dispatcher so nested source-tile downloads can never starve
        // the dispatcher that MapLibre's outer tile requests are waiting on.
        val raw = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addNetworkInterceptor(check)
            .build()
        fun fetchSources(tiles: List<GcjSatellite.Tile>): List<ByteArray>? {
            val results = arrayOfNulls<ByteArray>(tiles.size)
            val pending = java.util.concurrent.CountDownLatch(tiles.size)
            tiles.forEachIndexed { index, tile ->
                val key = "${tile.z}/${tile.x}/${tile.y}"
                val cached = sourceTiles.get(key)
                if (cached != null) { results[index] = cached; pending.countDown(); return@forEachIndexed }
                val host = "webst0${(tile.x + tile.y) % 4 + 1}.is.autonavi.com"
                val url = "https://$host/appmaptile?style=6&x=${tile.x}&y=${tile.y}&z=${tile.z}"
                raw.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, error: IOException) = pending.countDown()
                    override fun onResponse(call: okhttp3.Call, response: Response) {
                        try {
                            val bytes = response.use { if (it.isSuccessful) it.body?.bytes() else null }
                            if (GcjSatellite.isValidImagery(bytes)) { sourceTiles.put(key, bytes!!); results[index] = bytes }
                        } catch (_: IOException) {
                        } finally { pending.countDown() }
                    }
                })
            }
            if (!pending.await(15, TimeUnit.SECONDS)) return null
            return results.toList().takeIf { list -> list.all { it != null } }?.map { it!! }
        }
        val satellite = okhttp3.Interceptor { chain ->
            val request = chain.request()
            if (request.url.host != LOCAL_SATELLITE_HOST) return@Interceptor chain.proceed(request)
            val match = localSatellitePath.matchEntire(request.url.encodedPath)
                ?: throw IOException("Invalid satellite tile")
            val (z, x, y) = match.destructured.toList().map { it.toInt() }
            val corrected = if (GcjSatellite.covers(z, x, y, chinaBoundary)) {
                try { GcjSatellite.build(z, x, y, ::fetchSources) } catch (_: RuntimeException) { null }
            } else null
            if (corrected != null) {
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Cache-Control", "max-age=2592000")
                    .body(corrected.toResponseBody("image/jpeg".toMediaType())).build()
            } else {
                // Outside mainland China (or if the domestic source fails) use global WGS-84 imagery.
                chain.proceed(request.newBuilder()
                    .url("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x").build())
            }
        }
        val client = raw.newBuilder()
            // Allow higher HTTP/2 tile concurrency per host while keeping total queue responsive.
            .dispatcher(Dispatcher().apply { maxRequests = 48; maxRequestsPerHost = 24 })
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(satellite)
            .addInterceptor(metadataCache)
            .build()
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        HttpRequestUtil.setOkHttpClient(client)
        configured = true
        if (connected(context)) {
            val noop = object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: IOException) = Unit
                override fun onResponse(call: okhttp3.Call, response: Response) = response.close()
            }
            client.newCall(Request.Builder().url(PLANET).build()).enqueue(noop)
            // Pre-warm DNS + TLS to the domestic satellite hosts using HEAD so tileCalls is untouched.
            client.newCall(Request.Builder().head().url(SATELLITE_WARMUP_1).build()).enqueue(noop)
            client.newCall(Request.Builder().head().url(SATELLITE_WARMUP_2).build()).enqueue(noop)
        }
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
