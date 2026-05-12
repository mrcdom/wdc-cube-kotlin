package br.com.wdc.shopping.view.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Loads and displays an image asynchronously from a URL.
 * Platform-specific implementation handles the actual HTTP fetching and decoding.
 */
@Composable
expect fun AsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable (() -> Unit)? = null
)
