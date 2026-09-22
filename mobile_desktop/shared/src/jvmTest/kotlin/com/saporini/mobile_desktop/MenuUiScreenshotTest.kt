@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.pos.menu.domain.model.*
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreen
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreenModel
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises real Compose measurement and user actions, not just breakpoint arithmetic.
 * The repository is a strict fake: an unexpected write fails rather than touching live data.
 */
class MenuUiScreenshotTest {
    @Test fun phone360() = exercise(360, 800)
    @Test fun phone390() = exercise(390, 844)
    @Test fun narrowDesktop768() = exercise(768, 900)
    @Test fun desktop1280() = exercise(1280, 800)
    @Test fun desktop1920() = exercise(1920, 1080)
    @Test fun ultrawide2560() = exercise(2560, 1440)
    @Test fun enlargedPhoneText() = exercise(390, 844, 1.5f)

    @Test fun sectionDeletionRefreshesWithoutLeavingMenu() = exercise(1280, 800, deletionFlow = true)

    @Test fun sectionDeletionCreatesFallbackImmediately() = exercise(1280, 800, deletionFlow = true, existingFallback = false)

    @Test fun sectionSelectionAndAllCreationGuard() = exercise(1280, 800, selectionFlow = true)

    @Test fun permanentFallbackDeletion() = exercise(1280, 800, deletionFlow = true, permanentFlow = true)

    private fun exercise(width: Int, height: Int, fontScale: Float = 1f, deletionFlow: Boolean = false, existingFallback: Boolean = true, selectionFlow: Boolean = false, permanentFlow: Boolean = false) {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val menu = sampleMenu()
        var menus = (0..5).map { index -> menu.copy(id = "menu-$index", code = "MENU_$index",
            name = listOf("Breakfast", "Lunch", "Dinner", "Drinks", "Desserts", "Seasonal specials")[index],
            color = listOf("#D6C394", "#A5B58C", "#9DBCCC")[index % 3]) }
        if (deletionFlow) {
            menus = menus.mapIndexed { index, menu ->
                menu.copy(
                    code = if (index == 5) "UNCATEGORIZED" else menu.code,
                    name = if (index == 5) "Uncategorized" else menu.name,
                    sections = if (existingFallback) menu.sections else menu.sections.filterNot { it.name == "Uncategorized" }
                )
            }
        }
        val repository = Proxy.newProxyInstance(MenuRepository::class.java.classLoader,
            arrayOf(MenuRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getMenus" -> MenuPage(menus, 0, 20, menus.size.toLong(), 1, false, false)
                "getMenu" -> menus.first { it.id == args!![0] }
                "deleteSection" -> {
                    val menuId = args!![0]
                    val sectionId = args[1]
                    val deleteItems = args[2] as Boolean
                    menus = menus.map { current ->
                        if (current.id != menuId) current else {
                            val removed = current.sections.first { it.id == sectionId }
                            if (removed.name == "Uncategorized") assertTrue(deleteItems, "Fallback deletion must delete contents")
                            val remaining = current.sections.filterNot { it.id == sectionId }
                            val targets = if (!deleteItems && remaining.none { it.name == "Uncategorized" })
                                remaining + removed.copy(id = "fallback-new", name = "Uncategorized", items = emptyList())
                            else remaining
                            current.copy(sections = targets.map { section ->
                                if (!deleteItems && section.name == "Uncategorized") section.copy(items = section.items + removed.items)
                                else section
                            })
                        }
                    }
                    Unit
                }
                "deleteMenu" -> {
                    assertTrue(args!![1] as Boolean, "Uncategorized menu must delete contents")
                    menus = menus.filterNot { it.id == args[0] }
                    Unit
                }
                "toString" -> "ScreenshotMenuRepository"
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as MenuRepository
        val session = SessionManager().apply {
            signIn(CurrentUserResponse(id = "review-user", restaurantId = "review-restaurant",
                email = "review@example.invalid", username = "review", firstName = "Menu", lastName = "Review",
                isActive = true, emailVerified = true, phoneVerified = false, roles = listOf("OWNER"),
                permissions = listOf("MENUS_UPDATE", "MENUS_CREATE")))
        }
        val model = MenuScreenModel(repository, session)
        startKoin { modules(module { single { model }; single { session } }) }
        val scene = ImageComposeScene(width, height, density = Density(1f, fontScale), coroutineContext = dispatcher) {
            SaporiniTheme { MenuScreen() }
        }
        val output = File("build/reports/menu-ui/${width}x${height}-font$fontScale${if (deletionFlow) "-deletion-$existingFallback${if (permanentFlow) "-permanent" else ""}" else if (selectionFlow) "-selection" else ""}").apply { mkdirs() }
        fun capture(name: String) {
            repeat(if (deletionFlow || selectionFlow) 6 else 3) {
                if ((deletionFlow || selectionFlow) && name != "02-opening-menu") scheduler.advanceTimeBy(80)
                scheduler.runCurrent()
                scene.render(scheduler.currentTime * 1_000_000L).close()
                Thread.sleep(70)
            }
            val image = scene.render(scheduler.currentTime * 1_000_000L)
            try {
                val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { File(output, "$name.png").writeBytes(png.bytes) } finally { png.close() }
            } finally { image.close() }
        }
        fun nodes(): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return scene.semanticsOwners.flatMap { all(it.unmergedRootSemanticsNode) }
        }
        fun labelText(n: SemanticsNode): String =
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
                n.children.joinToString { labelText(it) }
        fun selected(label: String) = nodes().any {
            it.config.getOrNull(SemanticsProperties.Selected) == true && labelText(it).contains(label)
        }
        fun click(label: String) {
            val node = nodes().lastOrNull { node ->
                labelText(node).contains(label) && node.config.getOrNull(SemanticsActions.OnClick)?.action != null
            } ?: error("Clickable '$label' missing at $width x $height")
            assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scheduler.runCurrent()
        }
        try {
            scheduler.runCurrent()
            capture("01-menu-list")
            // Open using the production model to exercise the real transition/loading frame.
            model.openMenu(menus.first().id)
            capture("02-opening-menu")
            scheduler.advanceTimeBy(800)
            capture("03-menu-items")
            if (selectionFlow) {
                assertTrue(selected("Starters"), "First section must be selected on opening")
                click("More categories")
                capture("20-overflow-all-last")
                click("All")
                capture("21-all-selected")
                assertTrue(selected("All"))
                click("ADD NEW ITEM")
                capture("22-select-section-message")
                assertTrue(nodes().any { labelText(it).contains("Select a section to add an item.") })
                assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Add New Item" } })
                click("Starters")
                capture("23-real-section-selected")
                click("ADD NEW ITEM")
                capture("24-item-editor")
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Add New Item" } })
                return
            }
            if (deletionFlow) {
                click("Edit menu sections")
                capture("06-section-editor")
                click("Delete Starters")
                capture("07-preserve-confirmation")
                click("Delete")
                capture("08-deleted")
                scheduler.advanceTimeBy(1500)
                capture("09-sections-refreshed")
                click("Done")
                capture("10-items-refreshed")
                val refreshed = requireNotNull(model.state.value.selectedMenu)
                assertTrue(refreshed.sections.none { it.name == "Starters" })
                assertTrue(refreshed.sections.first { it.name == "Uncategorized" }.items.size == if (existingFallback) 6 else 3)
                assertTrue(selected("Uncategorized"), "Moved items must be immediately visible in their selected destination")
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Starters favourite 2" } }, "Moved item must be rendered without reopening")
                if (!permanentFlow) return
                click("Edit menu sections")
                capture("11-edit-fallback")
                click("Delete Uncategorized")
                capture("12-permanent-confirmation")
                assertTrue(nodes().none { labelText(it).contains("Also delete the items inside this section") })
                click("Delete")
                capture("13-fallback-deleted")
                scheduler.advanceTimeBy(1500)
                capture("14-sections-after-fallback")
                click("Done")
                capture("15-items-after-fallback")
                assertTrue(model.state.value.selectedMenu!!.sections.none { it.name == "Uncategorized" })
                click("Back to all menus")
                capture("16-list-before-menu-delete")
                click("Edit Uncategorized")
                capture("17-fallback-menu-editor")
                click("Delete Menu")
                capture("18-fallback-menu-confirmation")
                assertTrue(nodes().none { labelText(it).contains("Also delete everything inside this menu") })
                click("Yes, Delete")
                capture("19-fallback-menu-deleted")
                assertTrue(model.state.value.menus.none { it.code == "UNCATEGORIZED" })
                return
            }
            click("Starters")
            capture("04-filtered-items")
            click("Back to all menus")
            capture("05-back-to-list")
            assertTrue(model.state.value.selectedMenu == null)
        } finally {
            scene.close()
            model.onDispose()
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private fun sampleMenu(): Menu {
        val sections = listOf("Starters", "Main dishes", "Pasta", "Desserts", "Drinks", "Seasonal", "Uncategorized")
            .mapIndexed { index, name ->
                MenuSection("section-$index", name, null, true, index, (0..2).map { itemIndex ->
                    MenuItem("item-$index-$itemIndex", "SKU-$index-$itemIndex",
                        if (itemIndex == 0) "Roasted vegetables with fresh herbs and homemade sauce" else "$name favourite ${itemIndex + 1}",
                        "Freshly prepared with seasonal ingredients. A longer description to check wrapping.",
                        12.5 + itemIndex, null, itemIndex != 2, itemIndex,
                        listOf("Tomato", "Basil", "Olive oil"), emptyList(), emptyList())
                })
            }
        return Menu("menu-0", MenuRestaurant("review-restaurant", "REVIEW", "Review Restaurant"),
            "DINNER", "Dinner", "Seasonal dishes", true, 0, null, null, null, null, "#A5B58C", 21,
            null, null, null, null, sections)
    }
}
