@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.pos.shifts

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class ShiftScreenshotTest {
    @Test fun desktopManager() = render(1280,900,true)
    @Test fun phoneManager() = render(390,844,true)
    @Test fun desktopWaiter() = render(1280,900,false)
    @Test fun phoneWaiter() = render(390,844,false)
    @Test fun desktopScheduleDialog() = render(1280,900,true,true)
    @Test fun phoneScheduleDialog() = render(390,844,true,true)
    private fun render(width:Int,height:Int,manager:Boolean,editor:Boolean=false) {
        val dispatcher=StandardTestDispatcher(); Dispatchers.setMain(dispatcher)
        val model=ShiftScreenModel(FakeShifts(),SessionManager())
        val people=listOf(ShiftStaff("u","Alex Morgan",listOf("Waiter")),ShiftStaff("u2","Jamie Rivera",listOf("Waiter")),ShiftStaff("u3","Sam Lee",listOf("Kitchen")))
        val items=(0..6).flatMap { day -> people.mapIndexed { index, person ->
            ShiftItem("s-$day-$index",0,person.id,person.name,if(day==6 && index==0) "OPEN" else if(day<6) "CLOSED" else "SCHEDULED",
                "2026-09-${21+day}T${if(index==2) "14" else "08"}:00:00Z","2026-09-${21+day}T${if(index==2) "22" else "16"}:00:00Z",
                if(day<6 || index==0) "2026-09-${21+day}T08:00:00Z" else null,
                if(day<6) "2026-09-${21+day}T16:00:00Z" else null,
                workedMinutes=if(day<6) 450 else if(index==0) 120 else 0,breakMinutes=if(day<6) 30 else 0,notes="Main dining room")
        } }
        val current=items.first { it.status=="OPEN" }
        val state=ShiftState(date=LocalDate(2026,9,27),board=ShiftBoard("Europe/Berlin","2026-09-27T10:00:00Z",if(manager) items else items.filter { it.userId=="u" },current,people),loading=false,userId="u",permissions=setOf("SHIFT_SELF","SHIFT_READ","SHIFT_MANAGE"),ready=true)
        val scene=ImageComposeScene(width,height,density=Density(1f),coroutineContext=dispatcher) { SaporiniTheme { ShiftContent(state,manager,model) } }
        try {
            repeat(8) { dispatcher.scheduler.advanceTimeBy(120); dispatcher.scheduler.runCurrent(); scene.render(dispatcher.scheduler.currentTime*1_000_000).close(); Thread.sleep(30) }
            if(editor) {
                val position=if(width>900) Offset(1160f,40f) else Offset(322f,35f)
                scene.sendPointerEvent(PointerEventType.Press,position)
                scene.sendPointerEvent(PointerEventType.Release,position)
                repeat(8) { dispatcher.scheduler.advanceTimeBy(120); dispatcher.scheduler.runCurrent(); scene.render(dispatcher.scheduler.currentTime*1_000_000).close(); Thread.sleep(30) }
            }
            val image=scene.render(dispatcher.scheduler.currentTime*1_000_000)
            try { val png=requireNotNull(image.encodeToData(EncodedImageFormat.PNG)); try { val file=File("build/reports/shifts/${if(editor) "editor" else if(manager) "manager" else "waiter"}-$width.png"); file.parentFile.mkdirs(); file.writeBytes(png.bytes) } finally { png.close() } } finally { image.close() }
        } finally { scene.close(); model.onDispose(); Dispatchers.resetMain() }
    }
}
