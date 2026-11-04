package cn.photomap.data

import org.json.JSONObject

val cameraFields = listOf(
    "camera_make" to "品牌", "camera_model" to "相机", "lens_model" to "镜头",
    "focal_length" to "焦距", "aperture" to "光圈", "shutter_speed" to "快门", "iso" to "ISO"
)

fun Photo.cameraOverrides(): Map<String, String> {
    val fields = JSONObject(exif).optJSONObject("manual_fields") ?: return emptyMap()
    return cameraFields.mapNotNull { (key, _) ->
        fields.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
    }.toMap()
}

internal fun JSONObject.withCameraOverrides(overrides: Map<String, String>): JSONObject {
    require(overrides.all { (key, value) -> cameraFields.any { it.first == key } && value.length <= 160 })
    return put("manual_fields", JSONObject(overrides.filterValues { it.isNotBlank() }))
}
