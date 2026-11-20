package com.saporini.mobile_desktop.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.ui.DeviceKind
import com.saporini.mobile_desktop.admin.devices.ui.DevicesContent
import com.saporini.mobile_desktop.admin.ui.AdminBottomBar
import com.saporini.mobile_desktop.admin.ui.AdminSection
import com.saporini.mobile_desktop.admin.ui.AdminTopBar
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.pos.ui.PosSection
import com.saporini.mobile_desktop.pos.ui.shell.PosBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.PosTopBar
import com.saporini.mobile_desktop.pos.shifts.PlanShiftsDialog
import com.saporini.mobile_desktop.pos.shifts.ShiftContent
import com.saporini.mobile_desktop.pos.shifts.ShiftScreenModel
import com.saporini.mobile_desktop.pos.shifts.ShiftTimesDialog
import com.saporini.mobile_desktop.pos.shifts.weekStart
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.time.Clock

// Admin Hub at every size the app runs on: phones (360, 412), small tablets (600, 768), tablet (1024) and desktop
// (1440). The bar, Shifts and Devices, into build/reports/gallery/size-*.png.
private val Sizes = listOf(360 to 800, 412 to 915, 600 to 960, 768 to 1024, 1024 to 1366, 1440 to 960)

class AdminResponsiveGalleryTest {
    @Test
    fun barAtEverySize() = gallery {
        // A manager: Shifts, Salary and Users are fixed, then Settings or the last pick, then More.
        org.koin.core.context.startKoin { modules(org.koin.dsl.module { single { shiftSession(waiter = false) } }) }
        try {
            Sizes.forEach { (width, _) ->
                render("size-bar-$width", width, 160, required = listOf("Users", "Salary")) {
                    Column {
                        if (isPhoneWindow()) { Spacer(Modifier.height(60.dp)); AdminBottomBar(AdminSection.DEVICES, {}, {}, {}) }
                        else AdminTopBar(AdminSection.DEVICES, {}, {}, {})
                    }
                }
                render("size-bar-pos-$width", width, 160, required = listOf("Tables")) {
                    Column {
                        if (isPhoneWindow()) { Spacer(Modifier.height(60.dp)); PosBottomBar(PosSection.TABLES, {}, {}, {}) }
                        else PosTopBar(PosSection.TABLES, {}, {}, logo = {}, onBackToWorkspaces = {})
                    }
                }
            }
        } finally {
            org.koin.core.context.stopKoin()
        }
    }

    @Test
    fun shiftsAtEverySize() = gallery {
        fillUsualShifts(withRoles = true)
        val repository = GalleryShifts()
        val model = ShiftScreenModel(repository, shiftSession(waiter = false))
        model.start(true); settle()
        model.date(model.state.value.date, true); settle()
        val today = Clock.System.now().toLocalDateTime(model.state.value.zone).date
        Sizes.forEach { (width, height) ->
            render("size-shifts-$width", width, height, required = listOf("Shifts")) {
                val state by model.state.collectAsState()
                ShiftContent(state, true, model)
            }
            settle()
            render("size-shifts-day-$width", width, height, required = listOf("No shift")) {
                val state by model.state.collectAsState()
                ShiftContent(state, true, model, openDay = today)
            }
            render("size-shifts-planner-$width", width, height, required = listOf("Plan shifts")) {
                val state by model.state.collectAsState()
                PlanShiftsDialog(state, repository.staff.filter { it.id != "owner" }, Clock.System.now(), today, initialTemplate = "morning", onDismiss = {})
            }
            render("size-shifts-times-$width", width, height) { ShiftTimesDialog {} }
        }
        model.onDispose()
    }

    @Test
    fun devicesAtEverySize() = gallery {
        val model = DevicesScreenModel(GalleryDevices(), gallerySession())
        model.setActive(true); settle()
        Sizes.forEach { (width, height) ->
            render("size-devices-$width", width, height, required = listOf("Devices", "Tills")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model)
            }
            render("size-devices-kind-$width", width, height, required = listOf("Tablets & screens")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model, startKind = DeviceKind.TABLET)
            }
            render("size-devices-add-$width", width, height, required = listOf("What are you adding?")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model, startAdding = true)
            }
        }
        model.select("d1"); settle()
        Sizes.forEach { (width, height) ->
            render("size-devices-detail-$width", width, height, required = listOf("Pairing codes")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model, startOpen = "d1")
            }
        }
        model.select("d5"); settle()
        Sizes.forEach { (width, height) ->
            render("size-devices-confirm-$width", width, height, required = listOf("Confirm Card terminal bar")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model, startConfirming = "d5")
            }
        }
        model.onDispose()
    }
}
