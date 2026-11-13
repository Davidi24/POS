@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.saporini.mobile_desktop.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.lang.reflect.Proxy
import kotlin.test.assertTrue

// Helpers for the screen tests that render a page off-screen and read or click what's on it.

@Composable
internal fun ScenePage(content: @Composable () -> Unit) {
    SaporiniTheme { Surface(Modifier.fillMaxSize(), color = Color(0xFFFCFCFB)) { content() } }
}

internal fun ImageComposeScene.settle(scheduler: TestCoroutineScheduler) {
    repeat(6) {
        scheduler.runCurrent()
        render(scheduler.currentTime * 1_000_000L).close()
    }
}

internal fun ImageComposeScene.nodes(): List<SemanticsNode> {
    fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
    return semanticsOwners.flatMap { all(it.unmergedRootSemanticsNode) }
}

private fun label(node: SemanticsNode): String =
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
        node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty() +
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
        node.children.joinToString { label(it) }

internal fun ImageComposeScene.labels(): String = nodes().joinToString { label(it) }

internal fun ImageComposeScene.click(text: String) {
    val node = nodes().last { label(it).contains(text) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
    assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
}

/**
 * A repository whose calls are answered by [call] (method name and arguments; suspend calls get the continuation last).
 * A failure reaches a suspend caller as itself (a proxy would wrap an ApiException), a moment later on its dispatcher.
 */
internal inline fun <reified T> fakeOf(crossinline call: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        when (method.name) {
            "toString" -> "Test repository"
            "hashCode" -> 1
            "equals" -> false
            else -> try {
                call(method.name, args)
            } catch (failure: Exception) {
                failSuspended(args, failure)
            }
        }
    } as T

@PublishedApi
internal fun failSuspended(args: Array<out Any?>?, failure: Exception): Any? {
    @Suppress("UNCHECKED_CAST")
    val continuation = args?.lastOrNull() as? kotlin.coroutines.Continuation<Any?> ?: throw failure
    val dispatcher = continuation.context[kotlin.coroutines.ContinuationInterceptor] as? kotlinx.coroutines.CoroutineDispatcher
    if (dispatcher == null) {
        continuation.resumeWith(Result.failure(failure))
    } else {
        dispatcher.dispatch(continuation.context, Runnable { continuation.resumeWith(Result.failure(failure)) })
    }
    return kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
}
