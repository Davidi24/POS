@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuPage
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuWaiterView
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreenModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuScreenModelMenuLoadingTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun deleteOnlyPermissionLoadsManagerMenuList() {
        Dispatchers.setMain(dispatcher)
        var managerLoads = 0
        var waiterLoads = 0
        val repository = repository { method, _ ->
            when (method) {
                "getMenus" -> {
                    managerLoads++
                    MenuPage(emptyList(), 0, 20, 0, 1, false, false)
                }
                "getWaiterView" -> MenuWaiterView()
                "getMenusAvailableNow" -> {
                    waiterLoads++
                    emptyList<Menu>()
                }
                else -> defaultValue(method)
            }
        }
        val model = MenuScreenModel(repository, session("MENUS_DELETE"))

        scheduler.runCurrent()

        assertEquals(1, managerLoads)
        assertEquals(0, waiterLoads)
        assertNull(model.state.value.errorMessage)
    }

    @Test
    fun waiterLoadFailureShowsErrorAndRetryRecovers() {
        Dispatchers.setMain(dispatcher)
        var calls = 0
        val expected = menu()
        val repository = repository { method, _ ->
            when (method) {
                "getMenusAvailableNow" -> {
                    calls++
                    if (calls == 1) throw IllegalStateException("event lookup unavailable")
                    listOf(expected)
                }
                else -> defaultValue(method)
            }
        }
        val model = MenuScreenModel(repository, session("MENUS_READ"))

        scheduler.runCurrent()
        assertTrue(model.state.value.errorMessage.orEmpty().contains("event lookup unavailable"))
        assertTrue(model.state.value.menus.isEmpty(), "A failed load must not guess a menu list")

        model.loadMenus()
        scheduler.runCurrent()

        assertEquals(2, calls)
        assertNull(model.state.value.errorMessage)
        assertEquals(listOf(expected), model.state.value.menus)
    }

    private fun session(vararg permissions: String) = SessionManager().apply {
        signIn(CurrentUserResponse(
            id = "user", restaurantId = "restaurant", email = "menu@example.invalid", username = "menu",
            firstName = "Menu", lastName = "Test", isActive = true, emailVerified = true,
            phoneVerified = false, roles = listOf("CUSTOM"), permissions = permissions.toList()
        ))
    }

    private fun menu() = Menu(
        id = "menu", restaurant = null, code = "DINNER", name = "Dinner", description = null,
        active = true, displayOrder = 0, availableFrom = null, availableUntil = null,
        availableFromDate = null, availableUntilDate = null, color = null, itemCount = 0,
        createdBy = null, updatedBy = null, createdAt = null, updatedAt = null, sections = emptyList()
    )

    private fun repository(block: (String, Array<out Any?>?) -> Any?): MenuRepository =
        Proxy.newProxyInstance(MenuRepository::class.java.classLoader, arrayOf(MenuRepository::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> "MenuScreenModelTestRepository"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> block(method.name, args)
            }
        } as MenuRepository

    private fun defaultValue(method: String): Any? = when (method) {
        "getWaiterView" -> MenuWaiterView()
        "getMenusAvailableNow" -> emptyList<Menu>()
        "toString" -> "MenuScreenModelTestRepository"
        else -> error("Unexpected repository call: $method")
    }
}
