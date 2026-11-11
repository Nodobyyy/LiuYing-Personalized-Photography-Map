package cn.photomap.map

import android.location.Address
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only text entered in the place search field reaches Photon. */
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
        val university = query.endsWith("大学") || query.endsWith("学院")
        val url = endpoint.newBuilder().addQueryParameter("q", query).addQueryParameter("limit", "12").apply {
            if (university) addQueryParameter("osm_tag", "amenity:university")
        }.build()
        val request = Request.Builder().url(url).header("User-Agent", "LiuYing-Android/0.5").build()
        val matches = suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use { parseResults(it, query, university) } })
                }
            })
        }
        cache.put(query, matches)
        matches
    }

    private fun parseResults(response: Response, query: String, university: Boolean): List<Address> {
        if (!response.isSuccessful) throw IOException("Place search unavailable")
        val json = JSONObject(response.body?.string() ?: throw IOException("Empty place search response"))
        val features = json.getJSONArray("features")
        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.getJSONObject(index)
                val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                val properties = feature.getJSONObject("properties")
                val name = properties.optString("name")
                if (university && !name.contains(query, ignoreCase = true)) continue
                val parts = listOf("name", "street", "district", "city", "state", "country")
                    .map { properties.optString(it) }.filter { it.isNotBlank() }.distinct()
                if (parts.isEmpty()) continue
                add(Address(Locale.getDefault()).apply {
                    latitude = coordinates.getDouble(1)
                    longitude = coordinates.getDouble(0)
                    featureName = name
                    setAddressLine(0, parts.joinToString(" · "))
                })
            }
        }
    }
}
