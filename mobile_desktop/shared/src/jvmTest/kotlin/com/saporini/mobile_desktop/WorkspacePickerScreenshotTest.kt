@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cafe.adriel.voyager.navigator.Navigator
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.pos.ui.PosScreen
import com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen
import com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertSame

// Workspace picker at desktop and phone sizes, with three and with two workspaces.
// Output: build/reports/app-ui/workspace/.
class WorkspacePickerScreenshotTest {
    private val everything = listOf("POS_ACCESS", "KDS_ACCESS", "ADMIN_ACCESS")

    @Test fun desktopThree() = capture(1280, 800, everything, "desktop-3")
    @Test fun desktopTwo() = capture(1280, 800, listOf("POS_ACCESS", "ADMIN_ACCESS"), "desktop-2")
    @Test fun phoneThree() = capture(390, 844, everything, "phone-3")
    @Test fun phoneTwo() = capture(390, 844, listOf("POS_ACCESS", "KDS_ACCESS"), "phone-2")

    @Test fun superAdminDesktop() = capture(1280, 800, emptyList(), "desktop-4-super-admin", roles = listOf("SUPER_ADMIN"))
    @Test fun superAdminNarrowDesktop() = capture(1024, 900, emptyList(), "desktop-1024-4-super-admin", roles = listOf("SUPER_ADMIN"))
    @Test fun superAdminPhone() = capture(390, 844, emptyList(), "phone-4-super-admin", roles = listOf("SUPER_ADMIN"))

    @Test fun onlySuperAdminSeesRestaurants() {
        val manager = user(listOf("POS_ACCESS", "KDS_ACCESS", "ADMIN_ACCESS", "USERS_READ"))
        kotlin.test.assertFalse(com.saporini.mobile_desktop.core.session.Workspace.RESTAURANTS in
            com.saporini.mobile_desktop.core.session.accessibleWorkspaces(manager))
    }

    @Test fun waiterWithOnlyPosSkipsThePicker() {
        assertSame(PosScreen, resolveStartScreen(user(listOf("POS_ACCESS", "ORDER_CREATE"))))
    }

    @Test fun superAdminGetsEveryWorkspace() {
        val superAdmin = user(emptyList()).copy(roles = listOf("SUPER_ADMIN"))
        kotlin.test.assertEquals(com.saporini.mobile_desktop.core.session.Workspace.entries.toSet(),
            com.saporini.mobile_desktop.core.session.accessibleWorkspaces(superAdmin))
        assertSame(WorkspacePickerScreen, resolveStartScreen(superAdmin))
    }

    @Test fun twoWorkspacesShowThePicker() {
        assertSame(WorkspacePickerScreen, resolveStartScreen(user(listOf("POS_ACCESS", "KDS_ACCESS"))))
    }

    @Test fun managerOpensPosAndAdminButNotKds() {
        // Managers keep KDS_READ for kitchen status inside POS, but that alone must not open the KDS workspace.
        val manager = user(listOf("POS_ACCESS", "ADMIN_ACCESS", "KDS_READ", "ORDER_CREATE", "USERS_READ"))
        kotlin.test.assertEquals(
            setOf(com.saporini.mobile_desktop.core.session.Workspace.POS, com.saporini.mobile_desktop.core.session.Workspace.ADMIN),
            com.saporini.mobile_desktop.core.session.accessibleWorkspaces(manager)
        )
    }

    @Test fun kitchenGoesStraightToKds() {
        val kitchen = user(listOf("KDS_ACCESS", "KDS_READ", "KDS_UPDATE")).copy(roles = listOf("KITCHEN"))
        assertSame(com.saporini.mobile_desktop.kds.ui.KdsScreen, resolveStartScreen(kitchen))
    }

    private fun user(permissions: List<String>) = CurrentUserResponse(
        id = "user-1", restaurantId = "r-1", defaultBranchId = "b-1", email = "david.keci@saporini.al",
        username = "david", firstName = "David", lastName = "Keci", isActive = true, emailVerified = true,
        phoneVerified = true, roles = listOf("MANAGER"), permissions = permissions
    )

    private fun capture(width: Int, height: Int, permissions: List<String>, name: String, roles: List<String> = listOf("MANAGER")) {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val session = SessionManager().apply { signIn(user(permissions).copy(roles = roles)) }
        val auth = AuthRepository(HttpClient(MockEngine { respondOk() }))
        startKoin { modules(module { single { session }; factory { WorkspaceScreenModel(auth, session) } }) }
        val scene = ImageComposeScene(width, height, density = Density(1f), coroutineContext = dispatcher) {
            SaporiniTheme { Navigator(WorkspacePickerScreen) }
        }
        try {
            repeat(6) { scheduler.advanceTimeBy(100); scheduler.runCurrent(); scene.render(scheduler.currentTime * 1_000_000L).close(); Thread.sleep(40) }
            val image = scene.render(scheduler.currentTime * 1_000_000L)
            val output = File("build/reports/app-ui/workspace").apply { mkdirs() }
            try {
                val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { File(output, "$name.png").writeBytes(png.bytes) } finally { png.close() }
            } finally { image.close() }
        } finally {
            scene.close(); stopKoin(); Dispatchers.resetMain()
        }
    }
}
