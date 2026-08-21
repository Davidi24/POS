package com.saporini.mobile_desktop.pos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.kitchen.KitchenStatusScreen
import com.saporini.mobile_desktop.pos.menu.MenuScreen
import com.saporini.mobile_desktop.pos.orders.OrdersScreen
import com.saporini.mobile_desktop.pos.payment.PaymentScreen
import com.saporini.mobile_desktop.pos.reservations.ReservationsScreen
import com.saporini.mobile_desktop.pos.sales.MySalesScreen
import com.saporini.mobile_desktop.pos.tables.ui.AddItemModal
import com.saporini.mobile_desktop.pos.tables.ui.TablesScreen
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
        val sessionManager = koinInject<SessionManager>()
        val currentUser by sessionManager.currentUser.collectAsState()
        val canEditTableLayout =
            "SETTINGS_UPDATE" in currentUser?.permissions.orEmpty() ||
                "MANAGER" in currentUser?.roles.orEmpty()

        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize()) {
                PosTopBar(
                    selected = selected,
                    onSelect = {
                        selected = it
                        showPaymentScreen = false
                    },
                    onLogout = { sessionManager.signOut() },
                    logo = {
                        Image(
                            painter = painterResource(Res.drawable.pos_simple_logo),
                            contentDescription = "Saporini",
                            modifier = Modifier.size(82.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                )
                if (showPaymentScreen) {
                    PaymentScreen(
                        modifier = Modifier.weight(1f),
                        onBack = { showPaymentScreen = false }
                    )
                } else {
                    when (selected) {
                        PosSection.TABLES -> TablesScreen(
                            modifier = Modifier.weight(1f),
                            canEditLayout = canEditTableLayout,
                            onAddItemsRequested = { showAddItemModal = true }
                        )
                        PosSection.ORDERS -> OrdersScreen(
                            modifier = Modifier.weight(1f),
                            onPaymentRequested = {
                                selected = PosSection.ORDERS
                                showPaymentScreen = true
                            }
                        )
                        PosSection.RESERVATIONS -> ReservationsScreen(Modifier.weight(1f))
                        PosSection.MENU -> MenuScreen(Modifier.weight(1f))
                        PosSection.KITCHEN_STATUS -> KitchenStatusScreen(Modifier.weight(1f))
                        PosSection.MY_SALES -> MySalesScreen(Modifier.weight(1f))
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(selected.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
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
