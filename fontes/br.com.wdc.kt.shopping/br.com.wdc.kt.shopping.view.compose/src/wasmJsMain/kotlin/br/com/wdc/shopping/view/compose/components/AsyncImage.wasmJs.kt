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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image as SkiaImage
import kotlin.coroutines.resume

@JsFun("""(url, onSuccess, onError) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', url, true);
    xhr.responseType = 'arraybuffer';
    xhr.onload = function() {
        if (xhr.status === 200 && xhr.response) {
            onSuccess(new Uint8Array(xhr.response));
        } else {
            onError();
        }
    };
    xhr.onerror = function() { onError(); };
    xhr.send(null);
}""")
private external fun xhrFetchImageAsync(url: JsString, onSuccess: (JsAny) -> Unit, onError: () -> Unit)

@JsFun("(a) => a.length")
private external fun jsArrayLength(a: JsAny): Int

@JsFun("(a, i) => a[i]")
private external fun jsArrayGet(a: JsAny, i: Int): Int

private suspend fun fetchImageBytes(url: String): ByteArray? = suspendCancellableCoroutine { cont ->
    xhrFetchImageAsync(
        url.toJsString(),
        onSuccess = { data ->
            val len = jsArrayLength(data)
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                bytes[i] = jsArrayGet(data, i).toByte()
            }
            cont.resume(bytes)
        },
        onError = { cont.resume(null) }
    )
}

// Cache to avoid re-fetching
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
                val bytes = fetchImageBytes(url)
                val img = if (bytes != null && bytes.isNotEmpty()) {
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                } else null
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
            // Loaded but no image — show placeholder or nothing
            if (placeholder != null) placeholder()
        }
    }
}
