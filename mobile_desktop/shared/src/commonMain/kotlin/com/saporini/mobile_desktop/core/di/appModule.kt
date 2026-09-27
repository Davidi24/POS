package com.saporini.mobile_desktop.core.di

import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreenModel
import com.saporini.mobile_desktop.core.network.createHttpClient
import com.saporini.mobile_desktop.core.session.SessionManager
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
        com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderCatalogRepository(api = get())
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
            sessionManager = get()
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}

