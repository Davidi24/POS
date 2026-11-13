package com.saporini.mobile_desktop.pos.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.BottomToast
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.ui.TicketsSection
import com.saporini.mobile_desktop.kds.ui.WaiterBoard
import kotlinx.coroutines.delay
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun KitchenStatusScreen(modifier: Modifier = Modifier, onGoToOrder: (orderId: String) -> Unit = {}) {
    val model = org.koin.compose.koinInject<KdsScreenModel>()
    val state by model.state.collectAsState()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(model, owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) model.setActive(true)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) model.setActive(false)
        }
        owner.lifecycle.addObserver(observer)
        model.setActive(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); model.onDispose() }
    }
    KitchenStatusContent(state, model, onGoToOrder, modifier)
}

/**
 * POS → Kitchen Status: the Kitchen display's board as the waiter sees it (user, 2026-10-31): the same bar, columns and
 * cards, one card per order, without the kitchen's buttons. An order is in Ready to serve once one of its dishes is
 * ready, and its "Go to order" opens it in Orders, where it's served.
 */
@Composable
internal fun KitchenStatusContent(
    state: KdsState,
    model: KdsScreenModel,
    onGoToOrder: (orderId: String) -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Instant = { Clock.System.now() }
) {
    // Minutes on the cards move on by themselves.
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = clock() } }
    // The late limits come from Admin Hub settings.
    LaunchedEffect(state.scope, state.canRead) { if (state.canRead) model.loadPosTiming() }
    LaunchedEffect(state.notice) { if (state.notice != null) { delay(2_500); if (state.actionError == null) model.clearMessages() } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        // The board runs down to the bottom edge, like the kitchen's.
        val board = state.loaded && state.canRead && !state.needsStationSetup
        val side = if (size.isPhone) 14.dp else 22.dp
        val top = if (size.isPhone) 12.dp else 18.dp
        Column(Modifier.fillMaxSize().padding(start = side, end = side, top = top, bottom = if (board) 0.dp else top),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.actionError?.let { MessageBar(it.message, MessageKind.ERROR, onDismiss = model::clearMessages) }
            BottomToast(state.notice, onDismiss = model::clearMessages)
            if (state.loaded && state.stale && state.error != null) {
                MessageBar("Can't reach the kitchen right now. The orders below may be a little behind.", MessageKind.WARNING)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !state.canRead -> Centered {
                        OverviewEmpty("No access to the kitchen", "Ask a manager to give your role “View Kitchen Display”. Then sign in again.", Icons.Outlined.Lock)
                    }
                    !state.loaded && state.error != null -> Centered {
                        OverviewEmpty("Couldn't load the kitchen", state.error.message, Icons.Outlined.CloudOff, action = { RetryText(onClick = { model.refresh() }) })
                    }
                    !state.loaded -> OverviewPageSkeleton(size, sidePanel = false)
                    state.needsStationSetup -> Centered {
                        OverviewEmpty("The kitchen isn't set up yet", "Once kitchen stations are added, the food for your tables shows up here.",
                            Icons.Outlined.Restaurant, image = Res.drawable.settings_orders_kitchen)
                    }
                    else -> TicketsSection(state, model, size, now, onStations = null, pageSide = side, pageTop = top,
                        waiter = WaiterBoard(state.orderTickets(), state.scope?.userId, onGoToOrder))
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}
