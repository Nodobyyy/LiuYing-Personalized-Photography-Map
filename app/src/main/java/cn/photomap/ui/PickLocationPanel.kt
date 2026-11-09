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
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Address>()) }
    var status by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
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
                onResult(latitude, longitude, "")
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
                query, { query = it.take(160); results = emptyList(); status = ""; submitted = "" },
                Modifier.fillMaxWidth(),
                label = { Text("搜索地名或经纬度") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = { TextButton(onClick = submit, enabled = query.isNotBlank()) { Text("搜索", fontWeight = FontWeight.SemiBold) } }
            )
            AnimatedVisibility(
                visible = status.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically()
            ) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            AnimatedVisibility(
                visible = results.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyColumn(Modifier.heightIn(max = 152.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(results) { address ->
                            val label = address.getAddressLine(0) ?: address.featureName ?: query
                            Text(
                                label,
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface.copy(alpha = 0.55f))
                                    .clickable {
                                        query = label
                                        results = emptyList()
                                        onResult(address.latitude, address.longitude, label)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Text(
                        "地点搜索 · Android / Photon · © OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
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

