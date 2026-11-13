package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.koinInject

/** A plain client for pictures that aren't on our backend: no token, no restaurant headers. */
private object PublicImageClient {
    val client: HttpClient by lazy { HttpClient() }
}

// Photos already shown, by URL. Stored photos never change behind a URL, so a small in-memory cache is enough.
private object RemoteImageCache {
    private const val MAX_IMAGES = 80
    private val images = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)

    fun get(url: String): ImageBitmap? = images[url]

    fun put(url: String, image: ImageBitmap) {
        images[url] = image
        while (images.size > MAX_IMAGES) images.remove(images.keys.first())
    }
}

// "/public/menu-images/…" from the backend, or a full URL (a public R2 domain).
internal fun absoluteImageUrl(url: String): String =
    if (url.startsWith("http://") || url.startsWith("https://")) url
    else ApiConfig.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')

/**
 * A picture loaded from a URL, with [placeholder] while it loads, when there's none, or when it can't be loaded
 * (a missing photo never breaks the screen).
 */
@Composable
internal fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable BoxScope.() -> Unit
) {
    if (url.isNullOrBlank()) {
        Box(modifier) { placeholder() }
        return
    }
    val absolute = remember(url) { absoluteImageUrl(url) }
    // Only our own backend gets the signed-in client (it adds the access token); pictures on other hosts (a public
    // bucket domain, sample photos) are fetched without it, so the token never leaves for someone else's server.
    val signedIn = koinInject<HttpClient>()
    val client = if (absolute.startsWith(ApiConfig.BASE_URL.trimEnd('/') + "/")) signedIn else PublicImageClient.client
    var image by remember(absolute) { mutableStateOf(RemoteImageCache.get(absolute)) }
    LaunchedEffect(absolute) {
        if (image != null) return@LaunchedEffect
        try {
            val bytes = client.get(absolute).body<ByteArray>()
            val decoded = withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
            RemoteImageCache.put(absolute, decoded)
            image = decoded
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            // Not reachable or not a picture: the placeholder stays.
        }
    }
    Box(modifier) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription, Modifier.matchParentSize(), contentScale = contentScale)
        } else {
            placeholder()
        }
    }
}
