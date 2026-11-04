package cn.photomap.map

import android.location.Address
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Only explicit place-name searches reach Photon. Photos and EXIF never enter this client. */
object PlaceSearch {
    private val endpoint = "https://photon.komoot.io/api/".toHttpUrl()
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val cache = LruCache<String, List<Address>>(40)
    private val requests = Mutex()
    private var lastRequest = 0L

    suspend fun find(query: String): List<Address> = requests.withLock {
        cache.get(query)?.let { return@withLock it }
        delay((lastRequest + 1_000 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        lastRequest = SystemClock.elapsedRealtime()
        val matches = withContext(Dispatchers.IO) {
            val url = endpoint.newBuilder().addQueryParameter("q", query).addQueryParameter("limit", "6").build()
            val request = Request.Builder().url(url).header("User-Agent", "LiuYing-Android/0.5").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Place search unavailable")
                val json = JSONObject(response.body?.string() ?: throw IOException("Empty place search response"))
                val features = json.getJSONArray("features")
                buildList {
                    for (index in 0 until features.length()) {
                        val feature = features.getJSONObject(index)
                        val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                        val properties = feature.getJSONObject("properties")
                        val parts = listOf("name", "street", "city", "state", "country")
                            .map { properties.optString(it) }.filter { it.isNotBlank() }.distinct()
                        if (parts.isEmpty()) continue
                        add(Address(Locale.getDefault()).apply {
                            latitude = coordinates.getDouble(1)
                            longitude = coordinates.getDouble(0)
                            setAddressLine(0, parts.joinToString(" · "))
                        })
                    }
                }
            }
        }
        cache.put(query, matches)
        matches
    }
}
