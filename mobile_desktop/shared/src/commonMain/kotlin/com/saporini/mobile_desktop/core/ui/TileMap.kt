package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

// A real map from OpenStreetMap's tiles (user, 2026-11-23: "I can set the coordinates on the map"). Their tile server is
// for light use and asks for its credit on the map; a production map provider is a later decision (decide-later, step 4).

private const val TILE = 256
private const val MIN_ZOOM = 3
private const val MAX_ZOOM = 19
private const val TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

/** A plain client for map tiles: no token, no restaurant headers, a neutral name for the tile server. */
private object MapTileClient {
    val client: HttpClient by lazy { HttpClient { install(UserAgent) { agent = "POS-app/1.0 (restaurant point of sale)" } } }
}

/** Tiles already loaded, newest last; the screen redraws when one arrives. */
private object MapTiles {
    private const val MAX_TILES = 300
    val tiles = mutableStateMapOf<String, ImageBitmap>()
    private val order = ArrayDeque<String>()
    val loading = mutableSetOf<String>()

    fun put(key: String, image: ImageBitmap) {
        tiles[key] = image
        order.addLast(key)
        while (order.size > MAX_TILES) tiles.remove(order.removeFirst())
    }
}

private fun worldSize(zoom: Int) = TILE.toDouble() * (1 shl zoom)
private fun lngToX(lng: Double, zoom: Int) = (lng + 180.0) / 360.0 * worldSize(zoom)
private fun latToY(lat: Double, zoom: Int): Double {
    val r = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * worldSize(zoom)
}
private fun xToLng(x: Double, zoom: Int) = x / worldSize(zoom) * 360.0 - 180.0
private fun yToLat(y: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * y / worldSize(zoom)
    return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
}

/**
 * A map that can be moved (drag) and zoomed (+ / −, double click), with [pin] on it. With [onPick], a click puts the pin
 * there (latitude, longitude). Without a pin it opens on [start].
 */
@Composable
internal fun TileMap(
    pin: Pair<Double, Double>?,
    start: Pair<Double, Double>,
    modifier: Modifier = Modifier,
    onPick: ((Pair<Double, Double>) -> Unit)? = null
) {
    var zoom by remember { mutableStateOf(if (pin != null) 16 else 12) }
    var center by remember { mutableStateOf(pin ?: start) }
    var picked by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // A pin typed or saved elsewhere brings the map to it; one put by a click leaves the map where it is.
    LaunchedEffect(pin) {
        if (pin != null && pin != picked) {
            center = pin
            if (zoom < 15) zoom = 16
        }
    }
    val scope = rememberCoroutineScope()
    val pick by androidx.compose.runtime.rememberUpdatedState(onPick)
    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFFF2EFE9))) {
        val scale = LocalDensity.current.density
        val width = constraints.maxWidth.toDouble() / scale
        val height = constraints.maxHeight.toDouble() / scale
        val cx = lngToX(center.second, zoom)
        val cy = latToY(center.first, zoom)
        val left = cx - width / 2
        val top = cy - height / 2
        val count = 1 shl zoom
        val needed = buildList {
            for (ty in floor(top / TILE).toInt()..floor((top + height) / TILE).toInt()) {
                if (ty !in 0 until count) continue
                for (tx in floor(left / TILE).toInt()..floor((left + width) / TILE).toInt()) add(Triple(tx, ty, zoom))
            }
        }
        LaunchedEffect(needed) {
            needed.forEach { (tx, ty, z) ->
                val key = "$z/${((tx % count) + count) % count}/$ty"
                if (key in MapTiles.tiles || key in MapTiles.loading) return@forEach
                MapTiles.loading += key
                scope.launch {
                    try {
                        val url = TILE_URL.replace("{z}", "$z").replace("{x}", key.split('/')[1]).replace("{y}", "$ty")
                        val bytes = MapTileClient.client.get(url).body<ByteArray>()
                        val image = withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
                        MapTiles.put(key, image)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Throwable) {
                        // Offline or refused: that square stays plain.
                    } finally {
                        MapTiles.loading -= key
                    }
                }
            }
        }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val x = lngToX(center.second, zoom) - drag.x / scale
                        val y = (latToY(center.first, zoom) - drag.y / scale).coerceIn(0.0, worldSize(zoom))
                        center = yToLat(y, zoom) to xToLng(((x % worldSize(zoom)) + worldSize(zoom)) % worldSize(zoom), zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { zoom = (zoom + 1).coerceAtMost(MAX_ZOOM) },
                        onTap = { offset ->
                            pick?.let { onPicked ->
                                // From the map as it is now (this handler outlives the frame it was made in).
                                val z = zoom
                                val wx = lngToX(center.second, z) - size.width / scale / 2.0 + offset.x / scale
                                val wy = latToY(center.first, z) - size.height / scale / 2.0 + offset.y / scale
                                val point = yToLat(wy, z) to xToLng(((wx % worldSize(z)) + worldSize(z)) % worldSize(z), z)
                                picked = point
                                onPicked(point)
                            }
                        }
                    )
                }
        ) {
            val size = (TILE * scale).roundToInt()
            needed.forEach { (tx, ty, z) ->
                val key = "$z/${((tx % count) + count) % count}/$ty"
                val image = MapTiles.tiles[key] ?: return@forEach
                drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(((tx * TILE - left) * scale).roundToInt(), ((ty * TILE - top) * scale).roundToInt()),
                    dstSize = IntSize(size, size), filterQuality = FilterQuality.Medium)
            }
        }
        // The pin, its tip on the spot.
        if (pin != null) {
            val px = ((lngToX(pin.second, zoom) - left) * scale).roundToInt()
            val py = ((latToY(pin.first, zoom) - top) * scale).roundToInt()
            val pinSize = (44 * scale).roundToInt()
            Icon(Icons.Filled.LocationOn, null, Modifier.offset { IntOffset(px - pinSize / 2, py - (pinSize * 0.92f).roundToInt()) }.size(44.dp),
                tint = Color(0xFFEA4335))
        }
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp).shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
            .background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))) {
            ZoomButton(Icons.Outlined.Add, zoom < MAX_ZOOM) { zoom += 1 }
            Box(Modifier.size(34.dp, 1.dp).background(Kit.Border))
            ZoomButton(Icons.Outlined.Remove, zoom > MIN_ZOOM) { zoom -= 1 }
        }
        Text("© OpenStreetMap", Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 5.dp, vertical = 1.dp),
            fontFamily = Inter(), fontSize = 9.5.sp, color = Kit.Muted, translate = false)
    }
}

@Composable
private fun ZoomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}

/** Where a map opens without a pin: the restaurant's main city by its time zone, else Tirana. */
internal fun mapStartFor(timezone: String?): Pair<Double, Double> = when (timezone) {
    "Europe/Rome" -> 41.9028 to 12.4964
    "Europe/Madrid" -> 40.4168 to -3.7038
    "Europe/London" -> 51.5074 to -0.1278
    "America/New_York" -> 40.7128 to -74.0060
    "America/Santo_Domingo" -> 18.4861 to -69.9312
    else -> 41.3275 to 19.8187
}
