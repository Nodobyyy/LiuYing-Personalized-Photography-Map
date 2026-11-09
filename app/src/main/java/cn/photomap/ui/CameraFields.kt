package cn.photomap.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.photomap.data.Photo
import cn.photomap.data.cameraFields
import org.json.JSONObject

@Composable
fun CameraFields(photo: Photo, overrides: Map<String, String>, enabled: Boolean, onChange: (Map<String, String>) -> Unit) {
    val original = remember(photo.exif) { JSONObject(photo.exif) }
    val shape = RoundedCornerShape(16.dp)
    cameraFields.forEach { (key, label) ->
        OutlinedTextField(
            value = overrides[key].orEmpty(),
            onValueChange = { onChange(overrides + (key to it.take(160))) },
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            label = { Text(label) },
            placeholder = { Text(original.optString(key).ifBlank { "未读取到" }) },
            singleLine = true,
            enabled = enabled
        )
    }
}

