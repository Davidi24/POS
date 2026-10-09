package com.saporini.mobile_desktop.pos.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.kitchen.KitchenStatusScreen
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreen
import com.saporini.mobile_desktop.pos.orders.OrdersScreen
import com.saporini.mobile_desktop.pos.payment.ui.TakePaymentScreen
import com.saporini.mobile_desktop.pos.reservations.ReservationsScreen
import com.saporini.mobile_desktop.pos.sales.MySalesScreen
import com.saporini.mobile_desktop.pos.tables.ui.AddItemModal
import com.saporini.mobile_desktop.pos.tables.ui.TablesScreen
import com.saporini.mobile_desktop.pos.ui.shell.PosBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.PosTopBar
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.painterResource

object PosScreen : Screen {

    @Composable
    override fun Content() {
        var selected by remember { mutableStateOf(PosSection.TABLES) }
        var showAddItemModal by remember { mutableStateOf(false) }
        var showPaymentScreen by remember { mutableStateOf(false) }
        // The order being paid for on the payment screen.
        var paymentOrderId by remember { mutableStateOf<String?>(null) }
        var focusTableNumber by remember { mutableStateOf<String?>(null) }
        var focusReservationId by remember { mutableStateOf<String?>(null) }
        val notificationCenter = org.koin.compose.getKoin().getOrNull<com.saporini.mobile_desktop.notifications.NotificationCenter>()
        // A clicked notification that points at a reservation opens it in Reservations.
        val openRequest = notificationCenter?.openRequest?.collectAsState()?.value
        LaunchedEffect(openRequest) {
            val request = openRequest ?: return@LaunchedEffect
            if (request.referenceType == "RESERVATION") {
                selected = PosSection.RESERVATIONS
                showPaymentScreen = false
                focusReservationId = request.referenceId
            }
            notificationCenter.openHandled()
        }
        // New notifications that arrive while the app is open pop up briefly in the bottom-left corner.
        var arrivals by remember { mutableStateOf<List<com.saporini.mobile_desktop.notifications.StaffNotification>>(emptyList()) }
        LaunchedEffect(notificationCenter) {
            notificationCenter?.arrivals?.collect { notification ->
                arrivals = (arrivals.filterNot { it.id == notification.id } + notification).takeLast(3)
            }
        }
        val sessionManager = koinInject<SessionManager>()
        val currentUser by sessionManager.currentUser.collectAsState()
        val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current
        // Users who can open more than one workspace get a way back to the picker.
        val backToWorkspaces: (() -> Unit)? =
            if ((currentUser?.let { com.saporini.mobile_desktop.core.session.accessibleWorkspaces(it) }?.size ?: 0) > 1) {
                { if (navigator?.pop() != true) navigator?.replaceAll(com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen) }
            } else null
        val canEditTableLayout =
            "SETTINGS_UPDATE" in currentUser?.permissions.orEmpty() ||
                "MANAGER" in currentUser?.roles.orEmpty()

        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
            val isPhoneLayout = isPhoneWindow()

            Column(Modifier.fillMaxSize().then(if (isPhoneLayout) Modifier.safeDrawingPadding() else Modifier)) {
                if (!isPhoneLayout) {
                    PosTopBar(
                        selected = selected,
                        onSelect = {
                            selected = it
                            showPaymentScreen = false
                        },
                        onLogout = { sessionManager.signOut() },
                        onBackToWorkspaces = backToWorkspaces,
                        logo = {
                            Image(
                                painter = painterResource(Res.drawable.pos_simple_logo),
                                contentDescription = "Saporini",
                                modifier = Modifier.size(82.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    )
                }
                val payingFor = paymentOrderId
                if (showPaymentScreen && payingFor != null) {
                    TakePaymentScreen(
                        orderId = payingFor,
                        modifier = Modifier.weight(1f),
                        onBack = { showPaymentScreen = false }
                    )
                } else {
                    when (selected) {
                        PosSection.TABLES -> TablesScreen(
                            modifier = Modifier.weight(1f),
                            canEditLayout = canEditTableLayout,
                            onAddItemsRequested = { showAddItemModal = true },
                            onGoToOrders = { selected = PosSection.ORDERS },
                            focusTableNumber = focusTableNumber,
                            onFocusHandled = { focusTableNumber = null }
                        )
                        PosSection.ORDERS -> OrdersScreen(
                            modifier = Modifier.weight(1f),
                            onPaymentRequested = { orderId ->
                                selected = PosSection.ORDERS
                                paymentOrderId = orderId
                                showPaymentScreen = true
                            }
                        )
                        PosSection.RESERVATIONS -> ReservationsScreen(
                            modifier = Modifier.weight(1f),
                            focusReservationId = focusReservationId,
                            onFocusHandled = { focusReservationId = null },
                            onGoToTable = { tableNumber ->
                                focusTableNumber = tableNumber
                                selected = PosSection.TABLES
                            }
                        )
                        PosSection.MENU -> MenuScreen(Modifier.weight(1f))
                        PosSection.HISTORY -> OrdersScreen(Modifier.weight(1f), historyOnly = true)
                        PosSection.KITCHEN_STATUS -> KitchenStatusScreen(Modifier.weight(1f))
                        PosSection.SHIFT -> com.saporini.mobile_desktop.pos.shifts.ShiftScreen(Modifier.weight(1f))
                        PosSection.MY_SALES -> MySalesScreen(Modifier.weight(1f), onShiftRequested = { selected = PosSection.SHIFT })
                        else -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(selected.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isPhoneLayout) {
                    PosBottomBar(
                        selected = selected,
                        onSelect = {
                            selected = it
                            showPaymentScreen = false
                        },
                        onBackToWorkspaces = backToWorkspaces,
                        onLogout = { sessionManager.signOut() }
                    )
                }
            }

            if (arrivals.isNotEmpty()) {
                com.saporini.mobile_desktop.notifications.NotificationArrivalQueue(
                    notifications = arrivals,
                    onDismiss = { id -> arrivals = arrivals.filterNot { it.id == id } },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = if (isPhoneLayout) 96.dp else 28.dp)
                )
            }

            if (showAddItemModal) {
                AddItemModal(
                    onDismiss = { showAddItemModal = false },
                    onAddToOrder = { showAddItemModal = false }
                )
            }
        }
    }
}
