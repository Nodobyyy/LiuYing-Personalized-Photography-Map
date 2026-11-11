package cn.photomap.ui

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.photomap.map.PlaceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    original: File? = thumbnail,
    onResult: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var selectedQuery by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<Address>()) }
    var status by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    val term = query.trim()
    val coordinate = remember(term) {
        val parts = term.split(',', '，', limit = 3)
        val latitude = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
        val longitude = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (parts.size == 2 && latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            Address(java.util.Locale.getDefault()).apply {
                this.latitude = latitude
                this.longitude = longitude
                setAddressLine(0, "定位到 $term")
            }
        } else null
    }
    val displayedResults = if (selectedQuery == term) emptyList() else coordinate?.let(::listOf) ?: results
    LaunchedEffect(term, selectedQuery) {
        if (term.length < 2 || selectedQuery == term || coordinate != null) return@LaunchedEffect
        // A partial coordinate should never leave the device as a place-name query.
        if (term.all { it.isDigit() || it in ".,，+- " }) return@LaunchedEffect
        delay(500)
        status = "正在搜索…"
        try {
            coroutineScope {
                val replies = Channel<List<Address>?>(2)
                launch { replies.send(lookup { PlaceSearch.find(term) }) }
                launch { replies.send(lookup { withTimeoutOrNull(2_500) { searchAddresses(Geocoder(context), term) } }) }
                val first = replies.receive()
                results = mergePlaces(first.orEmpty())
                if (results.isNotEmpty()) status = "正在补充其他地点…"
                val second = replies.receive()
                results = mergePlaces(first.orEmpty() + second.orEmpty())
                status = when {
                    results.isNotEmpty() -> ""
                    first == null && second == null -> "地点搜索暂不可用，可输入经纬度或拖动地图"
                    else -> "没有找到地点，试试加上城市或校区名"
                }
            }
        } catch (_: IllegalArgumentException) {
            status = "请输入有效的地名或经纬度"
        }
    }
    val colors = MaterialTheme.colorScheme
    FrostedPanel(modifier, shape = RoundedCornerShape(28.dp), elevation = 10.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            thumbnail?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.45f))
                        .border(0.8.dp, colors.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        it, "当前导入的照片",
                        Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showPreview = true },
                        contentScale = ContentScale.Crop
                    )
                    TextButton(onClick = { showPreview = true }) { Text("查看待定位照片", fontWeight = FontWeight.Medium) }
                }
            }
            OutlinedTextField(
                query, {
                    val next = it.take(160)
                    if (next.trim() != term) {
                        results = emptyList()
                        status = ""
                        selectedQuery = null
                    }
                    query = next
                },
                Modifier.fillMaxWidth(),
                label = { Text("搜索地名或经纬度") },
                leadingIcon = { AppIcon(AppIconType.SEARCH, tint = colors.primary, size = 18.dp) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = ""; results = emptyList(); status = ""; selectedQuery = null }) {
                            AppIcon(AppIconType.CLOSE, tint = colors.onSurfaceVariant, size = 16.dp)
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() })
            )
            AnimatedVisibility(
                visible = status.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically()
            ) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            AnimatedVisibility(
                visible = displayedResults.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyColumn(Modifier.heightIn(max = 152.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(displayedResults) { address ->
                            val label = address.getAddressLine(0) ?: address.featureName ?: query
                            Text(
                                label,
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface.copy(alpha = 0.55f))
                                    .clickable {
                                        val pickedLabel = if (address === coordinate) "" else label
                                        query = if (address === coordinate) term else label
                                        selectedQuery = query.trim()
                                        results = emptyList()
                                        status = ""
                                        focus.clearFocus()
                                        keyboard?.hide()
                                        onResult(address.latitude, address.longitude, pickedLabel)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (displayedResults.size > 1) {
                        Text("找到 ${displayedResults.size} 个地点，可滑动查看", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (showPreview && thumbnail != null) FullScreenDialog(onDismiss = { showPreview = false }, dark = true) {
        Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                FrostedPanel(shape = RoundedCornerShape(20.dp), elevation = 4.dp, tintColor = androidx.compose.ui.graphics.Color(0x991C2422)) {
                    TextButton(onClick = { showPreview = false }) { Text("返回地图", color = androidx.compose.ui.graphics.Color.White) }
                }
            }
            AsyncImage(original, "待定位照片预览", Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
        }
    }
}

private suspend fun lookup(search: suspend () -> List<Address>?): List<Address>? = try {
    search()
} catch (_: IOException) {
    null
} catch (_: JSONException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun mergePlaces(addresses: List<Address>): List<Address> = buildList {
    val distance = FloatArray(1)
    for (address in addresses) {
        if (!address.hasLatitude() || !address.hasLongitude() ||
            address.latitude !in -90.0..90.0 || address.longitude !in -180.0..180.0) continue
        val duplicate = any { other ->
            val name = address.featureName?.trim().orEmpty()
            val otherName = other.featureName?.trim().orEmpty()
            val sameName = name.isNotBlank() && name.equals(otherName, ignoreCase = true)
            val sameLabel = address.getAddressLine(0)?.equals(other.getAddressLine(0), ignoreCase = true) == true
            android.location.Location.distanceBetween(other.latitude, other.longitude, address.latitude, address.longitude, distance)
            (sameName || sameLabel) && distance[0] < 180f
        }
        if (!duplicate) add(address)
    }
}

@Suppress("DEPRECATION")
private suspend fun searchAddresses(geocoder: Geocoder, query: String): List<Address> {
    if (!Geocoder.isPresent()) throw IOException("Geocoding service unavailable")
    if (Build.VERSION.SDK_INT < 33) return withContext(Dispatchers.IO) {
        geocoder.getFromLocationName(query, 12).orEmpty()
    }
    return suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocationName(query, 12, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)
            override fun onError(errorMessage: String?) = continuation.resumeWithException(IOException(errorMessage))
        })
    }
}

