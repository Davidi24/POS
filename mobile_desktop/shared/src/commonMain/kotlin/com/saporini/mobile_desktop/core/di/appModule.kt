package com.saporini.mobile_desktop.core.di

import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreenModel
import com.saporini.mobile_desktop.core.network.createHttpClient
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.pos.tables.data.api.TableLayoutApi
import com.saporini.mobile_desktop.pos.tables.data.repository.DefaultTableLayoutRepository
import com.saporini.mobile_desktop.pos.tables.domain.repository.TableLayoutRepository
import com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import com.saporini.mobile_desktop.pos.tables.ui.TablesScreenModel
import com.saporini.mobile_desktop.pos.menu.data.api.MenuApi
import com.saporini.mobile_desktop.pos.menu.data.repository.DefaultMenuRepository
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreenModel
import com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi
import com.saporini.mobile_desktop.pos.reservations.data.repository.DefaultReservationRepository
import com.saporini.mobile_desktop.pos.reservations.domain.repository.ReservationRepository
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel

//to be understood
private val appModule = module {
    single { SessionManager() }
    single<com.saporini.mobile_desktop.kds.data.KdsRepository> { com.saporini.mobile_desktop.kds.data.KdsApi(get()) }
    single<com.saporini.mobile_desktop.kds.data.KdsMenuRepository> { com.saporini.mobile_desktop.kds.data.DefaultKdsMenuRepository(get()) }
    factory { com.saporini.mobile_desktop.kds.KdsScreenModel(get(), get(), get()) }
    single<com.saporini.mobile_desktop.pos.shifts.ShiftRepository> { com.saporini.mobile_desktop.pos.shifts.ShiftApi(get()) }
    factory { com.saporini.mobile_desktop.pos.shifts.ShiftScreenModel(get(), get()) }
    single { createHttpClient(get()) }
    single { AuthRepository(client = get()) }
    single { TableLayoutApi(client = get()) }
    single<TableLayoutRepository> {
        DefaultTableLayoutRepository(
            api = get(),
            sessionManager = get()
        )
    }
    factory { LoginScreenModel(get(), get()) }
    factory { WorkspaceScreenModel(get(), get()) }

    factory {
        TablesScreenModel(
            repository = get(),
            orderRepository = get(),
            sessionManager = get()
        )
    }

    single { com.saporini.mobile_desktop.pos.orders.data.api.OrderCatalogApi(client = get()) }
    single<com.saporini.mobile_desktop.pos.orders.domain.repository.OrderCatalogRepository> {
        val reservations = get<ReservationApi>()
        com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderCatalogRepository(
            api = get(),
            // Tonight's event set to "only the special menu": orders offer just that menu.
            onlyMenuToday = { restaurantId ->
                // The restaurant's day starts at 06:00, so 01:00 still belongs to last night's event.
                val serviceDate = com.saporini.mobile_desktop.pos.reservations.serviceDate(
                    kotlin.time.Clock.System.now(),
                    com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationPolicy()
                )
                reservations.getEventOn(restaurantId, serviceDate.toString())
                    ?.takeIf { it.specialMenuOnly == true && it.active != false }
                    ?.menuId
            }
        )
    }
    single<com.saporini.mobile_desktop.pos.sales.MySalesRepository> { com.saporini.mobile_desktop.pos.sales.ApiMySalesRepository(get(), get()) }
    factory { com.saporini.mobile_desktop.pos.sales.MySalesScreenModel(get(), get()) }
    single<com.saporini.mobile_desktop.pos.history.OrderHistoryRepository> { com.saporini.mobile_desktop.pos.history.ApiOrderHistoryRepository(get(), get()) }
    factory { com.saporini.mobile_desktop.pos.history.OrderHistoryScreenModel(get(), get()) }
    factory(org.koin.core.qualifier.named("pos-history")) {
        com.saporini.mobile_desktop.pos.orders.ui.OrdersScreenModel(repository = get(), sessionManager = get(), historyRepository = get(), historyOnly = true)
    }
    single { com.saporini.mobile_desktop.pos.orders.data.api.OrderApi(client = get()) }
    single<com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository> {
        com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderRepository(api = get())
    }
    factory {
        com.saporini.mobile_desktop.pos.orders.ui.OrdersScreenModel(
            repository = get(), sessionManager = get(), catalogRepository = get(), tableRepository = get()
        )
    }


    single { ReservationApi(client = get()) }

    single<ReservationRepository> {
        DefaultReservationRepository(api = get())
    }

    factory {
        ReservationsScreenModel(
            repository = get(),
            sessionManager = get(),
            tableLayoutRepository = get()
        )
    }

    single<com.saporini.mobile_desktop.pos.payment.data.PaymentRepository> { com.saporini.mobile_desktop.pos.payment.data.PaymentApi(client = get()) }
    factory { com.saporini.mobile_desktop.pos.payment.PaymentScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.statistics.data.StatisticsRepository> { com.saporini.mobile_desktop.statistics.data.StatisticsApi(client = get()) }
    factory { com.saporini.mobile_desktop.statistics.StatisticsScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.fraud.data.FraudRepository> { com.saporini.mobile_desktop.fraud.data.FraudApi(client = get()) }
    factory { com.saporini.mobile_desktop.fraud.FraudScreenModel(repository = get(), session = get()) }
    // Admin Hub: staff, roles, inventory, devices and the settings log (state only; screens come later).
    single<com.saporini.mobile_desktop.admin.people.PeopleRepository> { com.saporini.mobile_desktop.admin.people.PeopleApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.people.StaffScreenModel(repository = get(), session = get()) }
    factory { com.saporini.mobile_desktop.admin.people.RolesScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.admin.inventory.InventoryRepository> { com.saporini.mobile_desktop.admin.inventory.InventoryApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel(repository = get(), session = get(), pendingMoveStorage = TokenStore) }
    single<com.saporini.mobile_desktop.admin.devices.DevicesRepository> { com.saporini.mobile_desktop.admin.devices.DevicesApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.devices.DevicesScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.admin.audit.AuditLogRepository> { com.saporini.mobile_desktop.admin.audit.AuditLogApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.audit.AuditLogScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.admin.inventory.RecipesRepository> { com.saporini.mobile_desktop.admin.inventory.RecipesApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRepository> { com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderApi(client = get()) }
    factory { com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel(repository = get(), session = get()) }

    single { com.saporini.mobile_desktop.notifications.NotificationApi(client = get()) }
    single { com.saporini.mobile_desktop.notifications.NotificationCenter(api = get(), sessionManager = get()) }

    single { MenuApi(client = get()) }
    single { com.saporini.mobile_desktop.admin.settings.SettingsApi(client = get()) }

    single<MenuRepository> {
        DefaultMenuRepository(api = get())
    }

    factory {
        MenuScreenModel(
            repository = get(),
            sessionManager = get(),
            recipesRepository = get()
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
