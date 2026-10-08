package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import kotlin.time.Clock
import kotlin.time.Instant

/** The Kitchen Display's content for the chosen tab, with the messages every tab shares. */
@Composable
internal fun KdsSectionContent(
    state: KdsState,
    model: KdsScreenModel,
    section: KdsSection,
    modifier: Modifier = Modifier,
    clock: () -> Instant = { Clock.System.now() },
    // Food ordered with bookings has its own model, made while the Upcoming tab shows.
    preOrderModel: @Composable () -> PreOrderScreenModel = { koinInject() }
) {
    // Ticket timers move on by themselves.
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = clock() } }
    // The late limits come from Admin Hub settings.
    LaunchedEffect(state.scope, state.canRead) { if (state.canRead) model.loadPosTiming() }
    // "Ticket updated." fades after a moment; problems stay until closed.
    LaunchedEffect(state.notice) { if (state.notice != null) { delay(2_500); if (state.actionError == null) model.clearMessages() } }
    var stations by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.needsReconciliation) ReconcileBar(state, model)
            state.actionError?.takeIf { !state.needsReconciliation }?.let { MessageBar(it.message, MessageKind.ERROR, onDismiss = model::clearMessages) }
            state.notice?.let { MessageBar(it, MessageKind.SUCCESS, onDismiss = model::clearMessages) }
            if (state.loaded && state.stale && state.error != null) {
                MessageBar("Can't reach the kitchen server right now. The tickets below may be a little behind.", MessageKind.WARNING)
            }
            val body = Modifier.fillMaxWidth().weight(1f)
            Box(body) {
                when {
                    !state.canRead -> Centered {
                        OverviewEmpty("No access to the kitchen screen", "Ask a manager to give your role “View Kitchen Display”, then sign in again.", Icons.Outlined.Lock)
                    }
                    !state.loaded && state.error != null -> Centered {
                        OverviewEmpty("Couldn't load the kitchen", state.error.message, Icons.Outlined.CloudOff, action = { RetryText(onClick = { model.refresh() }) })
                    }
                    !state.loaded -> OverviewPageSkeleton(size, sidePanel = false)
                    state.needsStationSetup && section != KdsSection.MENU -> Centered {
                        OverviewEmpty("The kitchen isn't set up yet", "Add the kitchen's stations (grill, fryer, pass…) and new orders will appear here.",
                            Icons.Outlined.Restaurant, image = Res.drawable.settings_orders_kitchen,
                            action = if (state.canConfigure) ({ KitButton("Set up stations", { stations = true }) }) else null)
                    }
                    else -> when (section) {
                        KdsSection.TICKETS -> TicketsSection(state, model, size, now, if (state.canConfigure) ({ stations = true }) else null)
                        KdsSection.UPCOMING -> UpcomingSection(state, model, size, now, preOrderModel())
                        KdsSection.MENU -> MenuSection(state, model, size)
                        KdsSection.HISTORY -> HistorySection(state, model, size)
                    }
                }
            }
        }
    }
    if (state.selectedTicketId != null) TicketDetailDialog(state, model)
    if (stations) StationsDialog(state, model) { stations = false }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}

/** After a change whose answer got lost: refresh, look at the tickets, then confirm before changing more. */
@Composable
private fun ReconcileBar(state: KdsState, model: KdsScreenModel) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.WarningAmber, null, tint = Kit.Amber)
        Text("A change may not have been saved. Refresh, check the tickets, then tap “Looks right” to carry on.", Modifier.weight(1f),
            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        KitButton("Refresh", { model.refresh() }, style = ButtonStyle.SECONDARY, loading = state.loading)
        KitButton("Looks right", model::acknowledgeReconciliation, enabled = !state.stale && state.loaded)
    }
}
