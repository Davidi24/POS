@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuPage
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuWaiterView
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenu
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreen
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreenModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuRetryScreenTest {
    @Test
    fun waiterSeesEventLookupErrorAndRetryLoadsMenus() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        var availableNowCalls = 0
        val menu = Menu(
            id = "dinner", restaurant = null, code = "DINNER", name = "Dinner", description = null,
            active = true, displayOrder = 0, availableFrom = null, availableUntil = null,
            availableFromDate = null, availableUntilDate = null, color = null, itemCount = 0,
            createdBy = null, updatedBy = null, createdAt = null, updatedAt = null, sections = emptyList()
        )
        val repository = Proxy.newProxyInstance(MenuRepository::class.java.classLoader, arrayOf(MenuRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getMenusAvailableNow" -> {
                    availableNowCalls++
                    if (availableNowCalls == 1) throw IllegalStateException("event lookup unavailable")
                    listOf(menu)
                }
                "getOnlineMenu" -> OnlineMenu(emptyList())
                "getMenus" -> MenuPage(emptyList(), 0, 20, 0, 1, false, false)
                "getWaiterView" -> MenuWaiterView()
                "toString" -> "MenuRetryScreenTestRepository"
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as MenuRepository
        val session = SessionManager().apply {
            signIn(CurrentUserResponse(
                id = "waiter", restaurantId = "restaurant", email = "waiter@example.invalid", username = "waiter",
                firstName = "Menu", lastName = "Waiter", isActive = true, emailVerified = true,
                phoneVerified = false, roles = listOf("WAITER"), permissions = listOf("MENUS_READ")
            ))
        }
        val model = MenuScreenModel(repository, session)
        startKoin { modules(module { single { model }; single { session } }) }
        val scene = ImageComposeScene(1280, 800, coroutineContext = dispatcher) {
            SaporiniTheme { MenuScreen() }
        }

        fun renderAndRun() {
            repeat(4) {
                scheduler.runCurrent()
                scene.render(scheduler.currentTime * 1_000_000L).close()
            }
        }
        fun nodes(): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return scene.semanticsOwners.flatMap { all(it.unmergedRootSemanticsNode) }
        }
        fun label(node: SemanticsNode): String =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
                node.children.joinToString { label(it) }

        try {
            renderAndRun()
            assertTrue(nodes().any { label(it).contains("event lookup unavailable") })
            val retry = nodes().last { label(it).contains("Retry") && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
            assertTrue(retry.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            renderAndRun()
            assertEquals(2, availableNowCalls)
            assertEquals(listOf(menu), model.state.value.menus, "The retry must update the model from the server response")
            assertTrue(nodes().any { label(it).contains("DINNER") }, "Rendered labels were: ${nodes().map(::label)}")
        } finally {
            scene.close()
            stopKoin()
            Dispatchers.resetMain()
        }
    }
}
