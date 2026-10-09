@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Renders a screen with real screen models and fake data into build/reports/gallery/<name>.png, and checks the
// texts that must (and must not) be on it. Used to look at the new screens without running the app.

internal class Gallery {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)

    init {
        Dispatchers.setMain(dispatcher)
    }

    /** Lets the models load (their work runs on the test Main dispatcher). */
    fun settle() {
        repeat(4) { scheduler.advanceTimeBy(500); scheduler.runCurrent() }
    }

    fun render(
        name: String,
        width: Int = 1440,
        height: Int = 960,
        required: List<String> = emptyList(),
        absent: List<String> = emptyList(),
        content: @Composable () -> Unit
    ) {
        val scene = ImageComposeScene(width, height, coroutineContext = dispatcher) { SaporiniTheme { content() } }
        try {
            repeat(8) {
                scheduler.advanceTimeBy(120); scheduler.runCurrent()
                scene.render(scheduler.currentTime * 1_000_000L).close()
                Thread.sleep(15)
            }
            fun labels(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty().joinToString(" | ") { it.text } + " | " + node.children.joinToString(" | ") { labels(it) }
            val text = scene.semanticsOwners.joinToString { labels(it.unmergedRootSemanticsNode) }
            val output = File("build/reports/gallery").apply { mkdirs() }
            val image = scene.render(scheduler.currentTime * 1_000_000L)
            try {
                val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { File(output, "$name.png").writeBytes(png.bytes) } finally { png.close() }
            } finally { image.close() }
            required.forEach { assertTrue(text.contains(it), "$name is missing: $it") }
            absent.forEach { assertFalse(text.contains(it), "$name should not show: $it") }
        } finally {
            scene.close()
        }
    }

    fun close() = Dispatchers.resetMain()
}

internal fun gallery(block: Gallery.() -> Unit) {
    val gallery = Gallery()
    try { gallery.block() } finally { gallery.close() }
}
