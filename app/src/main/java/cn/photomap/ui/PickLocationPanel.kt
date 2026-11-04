package cn.photomap.ui

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.photomap.map.PlaceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import org.json.JSONException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun PickLocationPanel(
    thumbnail: File?,
    modifier: Modifier = Modifier,
    onResult: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Address>()) }
    var status by remember { mutableStateOf("") }
    val submit = {
        focus.clearFocus()
        keyboard?.hide()
        submitted = query.trim()
    }
    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        results = emptyList()
        val coordinates = submitted.split(',', '，').map { it.trim().toDoubleOrNull() }
        if (coordinates.size == 2) {
            val (latitude, longitude) = coordinates
            if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                onResult(latitude, longitude)
                status = ""
                submitted = ""
                return@LaunchedEffect
            }
        }
        status = "正在搜索…"
        try {
            val system = try {
                withTimeoutOrNull(2_500) { searchAddresses(Geocoder(context), submitted) }.orEmpty()
            } catch (_: IOException) {
                emptyList()
            }
            val valid = system.filter { it.hasLatitude() && it.hasLongitude() }
            results = valid.ifEmpty { PlaceSearch.find(submitted) }
            status = if (results.isEmpty()) "没有找到地点，试试加上城市名" else ""
        } catch (_: IOException) {
            status = "地点搜索暂不可用，可输入经纬度或拖动地图"
        } catch (_: IllegalArgumentException) {
            status = "请输入有效的地名或经纬度"
        } catch (_: JSONException) {
            status = "地点搜索暂不可用，可输入经纬度或拖动地图"
        }
        submitted = ""
    }
    FrostedPanel(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            thumbnail?.let {
                AsyncImage(it, "当前导入的照片", Modifier.fillMaxWidth().height(74.dp), contentScale = ContentScale.Fit)
            }
            OutlinedTextField(
                query, { query = it.take(160); results = emptyList(); status = ""; submitted = "" },
                Modifier.fillMaxWidth(), label = { Text("搜索地名或经纬度") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = { TextButton(onClick = submit, enabled = query.isNotBlank()) { Text("搜索") } }
            )
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            if (results.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 140.dp)) {
                items(results) { address ->
                    val label = address.getAddressLine(0) ?: address.featureName ?: query
                    Text(label, Modifier.fillMaxWidth().clickable {
                        query = label
                        results = emptyList()
                        onResult(address.latitude, address.longitude)
                    }.padding(vertical = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (results.isNotEmpty()) Text("地点搜索 · Android / Photon · © OpenStreetMap", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Suppress("DEPRECATION")
private suspend fun searchAddresses(geocoder: Geocoder, query: String): List<Address> {
    if (!Geocoder.isPresent()) throw IOException("Geocoding service unavailable")
    if (Build.VERSION.SDK_INT < 33) return withContext(Dispatchers.IO) {
        geocoder.getFromLocationName(query, 6).orEmpty()
    }
    return suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocationName(query, 6, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)
            override fun onError(errorMessage: String?) = continuation.resumeWithException(IOException(errorMessage))
        })
    }
}
