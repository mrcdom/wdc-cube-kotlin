package br.com.wdc.shopping.view.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val imageCache = mutableMapOf<String, ImageBitmap?>()

@Composable
actual fun AsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    placeholder: @Composable (() -> Unit)?
) {
    var bitmap by remember(url) { mutableStateOf(imageCache[url]) }
    var loaded by remember(url) { mutableStateOf(url in imageCache) }

    LaunchedEffect(url) {
        if (!loaded) {
            try {
                val img = withContext(Dispatchers.IO) {
                    val bytes = URL(url).readBytes()
                    val androidBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    androidBitmap?.asImageBitmap()
                }
                imageCache[url] = img
                bitmap = img
            } catch (_: Exception) {
                imageCache[url] = null
                bitmap = null
            }
            loaded = true
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        !loaded -> {
            if (placeholder != null) {
                placeholder()
            } else {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        else -> {
            if (placeholder != null) placeholder()
        }
    }
}
