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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
        // Tables → Start order: the table and its guests, for Orders to open a new order on.
        var startOrder by remember { mutableStateOf<Pair<String, Int>?>(null) }
        // Tables → Start order on an order with no dishes yet: Orders opens that order's items.
        var openOrderItems by remember { mutableStateOf(false) }
        var showPaymentScreen by remember { mutableStateOf(false) }
        // The order being paid for on the payment screen.
        var paymentOrderId by remember { mutableStateOf<String?>(null) }
        var focusTableNumber by remember { mutableStateOf<String?>(null) }
        // Whose booking the table shown from Reservations is, on its pill.
        var focusTableNote by remember { mutableStateOf<String?>(null) }
        var freedTableNumber by remember { mutableStateOf<String?>(null) }
        var historyOrderId by remember { mutableStateOf<String?>(null) }
        // Tables → a table's History button: History searched for that table.
        var historyTable by remember { mutableStateOf<String?>(null) }
        var focusReservationId by remember { mutableStateOf<String?>(null) }
        // Kitchen Status → "Go to order": the order Orders opens with.
        var focusOrderId by remember { mutableStateOf<String?>(null) }
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
            // On a phone the bar with back, notifications and the profile sits on top, as in Inventory and the Admin Hub,
            // and slides away while the page scrolls down (user, 2026-11-01).
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(isPhoneLayout)
            LaunchedEffect(selected) { hideBar.show() }

            Column(
                Modifier.fillMaxSize().then(if (isPhoneLayout) Modifier.safeDrawingPadding() else Modifier)
                    .nestedScroll(hideBar.connection)
            ) {
                if (isPhoneLayout) com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    com.saporini.mobile_desktop.workspace.ui.PickerTopBar(currentUser, loggingOut = false, onLogout = { sessionManager.signOut() },
                        compact = true, onBack = backToWorkspaces)
                }
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
                            onOrderRequested = { orderId ->
                                focusOrderId = orderId
                                selected = PosSection.ORDERS
                            },
                            onOrderItemsRequested = { orderId ->
                                focusOrderId = orderId
                                openOrderItems = true
                                selected = PosSection.ORDERS
                            },
                            onStartOrderRequested = { tableId, guests ->
                                startOrder = tableId to guests
                                selected = PosSection.ORDERS
                            },
                            onGoToOrders = { selected = PosSection.ORDERS },
                            onPaymentRequested = { orderId ->
                                selected = PosSection.ORDERS
                                paymentOrderId = orderId
                                showPaymentScreen = true
                            },
                            onReservationRequested = { reservationId ->
                                focusReservationId = reservationId
                                selected = PosSection.RESERVATIONS
                            },
                            onOrderInHistory = { orderId ->
                                historyOrderId = orderId
                                selected = PosSection.HISTORY
                            },
                            onTableHistory = { tableNumber ->
                                historyTable = tableNumber
                                selected = PosSection.HISTORY
                            },
                            focusTableNumber = focusTableNumber,
                            focusTableNote = focusTableNote,
                            onFocusHandled = { focusTableNumber = null; focusTableNote = null },
                            freedTableNumber = freedTableNumber,
                            onFreedHandled = { freedTableNumber = null }
                        )
                        PosSection.ORDERS -> OrdersScreen(
                            modifier = Modifier.weight(1f),
                            focusOrderId = focusOrderId,
                            openItems = openOrderItems,
                            onFocusHandled = { focusOrderId = null; openOrderItems = false },
                            startOrder = startOrder,
                            onStartOrderHandled = { startOrder = null },
                            onOpenHistory = { selected = PosSection.HISTORY },
                            onTableFreed = { number ->
                                freedTableNumber = number
                                selected = PosSection.TABLES
                            },
                            onPaymentRequested = { orderId ->
                                selected = PosSection.ORDERS
                                paymentOrderId = orderId
                                showPaymentScreen = true
                            }
                        )
                        PosSection.RESERVATIONS -> ReservationsScreen(
                            modifier = Modifier.weight(1f),
                            canManageAreas = canEditTableLayout,
                            focusReservationId = focusReservationId,
                            onFocusHandled = { focusReservationId = null },
                            onGoToTable = { tableNumber, guest ->
                                focusTableNumber = tableNumber
                                focusTableNote = guest
                                selected = PosSection.TABLES
                            },
                            onGoToOrder = { orderId ->
                                focusOrderId = orderId
                                selected = PosSection.ORDERS
                            }
                        )
                        PosSection.MENU -> MenuScreen(Modifier.weight(1f))
                        PosSection.HISTORY -> OrdersScreen(
                            Modifier.weight(1f),
                            historyOnly = true,
                            focusOrderId = historyOrderId,
                            onFocusHandled = { historyOrderId = null },
                            historySearch = historyTable,
                            onHistorySearchHandled = { historyTable = null },
                            onPaymentRequested = { orderId ->
                                selected = PosSection.ORDERS
                                paymentOrderId = orderId
                                showPaymentScreen = true
                            },
                            onOrderReopened = { orderId ->
                                focusOrderId = orderId
                                selected = PosSection.ORDERS
                            }
                        )
                        PosSection.KITCHEN_STATUS -> KitchenStatusScreen(Modifier.weight(1f), onGoToOrder = { orderId ->
                            focusOrderId = orderId
                            selected = PosSection.ORDERS
                        })
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

        }
    }
}
