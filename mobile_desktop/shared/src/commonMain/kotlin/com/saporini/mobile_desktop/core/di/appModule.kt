package com.saporini.mobile_desktop.core.di

import com.saporini.mobile_desktop.pos.shifts.toCentsOrNull
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
        // The server picks the menus that are on now, including the event-night rule.
        com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderCatalogRepository(api = get())
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
    factory {
        val platform = get<com.saporini.mobile_desktop.platform.PlatformRepository>()
        val menus = get<MenuRepository>()
        val history = get<com.saporini.mobile_desktop.pos.history.OrderHistoryRepository>()
        val payments = get<com.saporini.mobile_desktop.pos.payment.data.PaymentRepository>()
        val session = get<com.saporini.mobile_desktop.core.session.SessionManager>()
        com.saporini.mobile_desktop.statistics.StatisticsScreenModel(repository = get(), session = session, dayOrdersOf = { restaurantId, branchId, from, to ->
            // The orders of a day (the Day window) or of a month at most (Sales details' Download all): the branch picked, or every
            // branch; at most 6,000 a branch (60 pages).
            val userId = session.currentUser.value?.id.orEmpty()
            val branchIds = branchId?.let { listOf(it) } ?: platform.branches(restaurantId).filter { it.isActive }.map { it.id }
            branchIds.flatMap { b ->
                val scope = com.saporini.mobile_desktop.pos.orders.ui.OrdersScope(userId, restaurantId, b)
                buildList {
                    var page = 0
                    while (page < 60) {
                        val answer = history.page(scope, com.saporini.mobile_desktop.pos.history.HistoryFilter(from = from, to = to), page, 100)
                        addAll(answer.items)
                        if (!answer.hasNext) break
                        page++
                    }
                }
            }.map { o ->
                com.saporini.mobile_desktop.statistics.StatsOrder(o.id, o.branchId, o.orderNumber, o.openedAt, o.closedAt, o.tableNumber ?: o.tableName,
                    o.orderType.name, o.createdBy, o.guestCount, o.itemCount, o.total.value.toCentsOrNull() ?: 0L, o.status.name, unpaid = o.unpaidAt != null)
            }
        }, receiptOf = { restaurantId, orderId -> payments.receipt(restaurantId, orderId) }, branchesOf = { restaurantId ->
            platform.branches(restaurantId).filter { it.isActive }.map { com.saporini.mobile_desktop.statistics.StatsBranch(it.id, it.name) }
        }, photosOf = { restaurantId ->
            // Every menu's dishes that have a photo, by id and by name. The menu list carries no dishes, so each menu is read
            // with its sections and dishes (once per restaurant).
            menus.getMenus(restaurantId = restaurantId, size = 100).items
                .flatMap { menu -> runCatching { menus.getMenu(menu.id, includeSections = true, includeItems = true).sections }.getOrDefault(emptyList()) }
                .flatMap { it.items }
                .mapNotNull { dish -> dish.imageUrl?.takeIf { it.isNotBlank() }?.let { url -> dish to url } }
                .flatMap { (dish, url) -> listOf(dish.id to url, dish.name to url) }.toMap()
        }, restaurantNameOf = { restaurantId -> platform.restaurant(restaurantId).name },
            addedOn = { restaurantId -> platform.restaurant(restaurantId).createdAt?.take(10)?.let { kotlinx.datetime.LocalDate.parse(it) } }, placesOf = { restaurantId ->
            // Every branch's tables by floor, grouped by the place they're in (Tables → Edit → Create places).
            val tablesApi = get<TableLayoutApi>()
            platform.branches(restaurantId).filter { it.isActive }.associate { branch ->
                val names = runCatching { tablesApi.getTableCategories(restaurantId, branch.id) }.getOrDefault(emptyList()).associate { it.id to it.name }
                branch.id to runCatching { tablesApi.getTables(restaurantId, branch.id) }.getOrDefault(emptyList()).filter { it.active && it.floor != null }
                    .groupBy({ it.floor!! }, { table -> table.categoryId?.let(names::get) })
                    .mapValues { (_, places) -> places.groupingBy { it }.eachCount().map { (name, count) -> com.saporini.mobile_desktop.statistics.StatsPlace(name, count) } }
            }
        })
    }
    single<com.saporini.mobile_desktop.fraud.data.FraudRepository> { com.saporini.mobile_desktop.fraud.data.FraudApi(client = get()) }
    factory { com.saporini.mobile_desktop.fraud.FraudScreenModel(repository = get(), session = get()) }
    // Admin Hub: staff, roles, inventory, devices and the settings log (state only; screens come later).
    single<com.saporini.mobile_desktop.admin.people.PeopleRepository> { com.saporini.mobile_desktop.admin.people.PeopleApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.people.StaffScreenModel(repository = get(), session = get()) }
    factory { com.saporini.mobile_desktop.admin.people.RolesScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.admin.inventory.InventoryRepository> { com.saporini.mobile_desktop.admin.inventory.InventoryApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel(repository = get(), session = get(), pendingMoveStorage = TokenStore) }
    single<com.saporini.mobile_desktop.admin.devices.DevicesRepository> { com.saporini.mobile_desktop.admin.devices.DevicesApi(client = get()) }
    factory {
        val auth = get<com.saporini.mobile_desktop.auth.data.repository.AuthRepository>()
        com.saporini.mobile_desktop.admin.devices.DevicesScreenModel(repository = get(), session = get(), connectHere = { auth.connectTill(it) })
    }
    single<com.saporini.mobile_desktop.admin.audit.AuditLogRepository> { com.saporini.mobile_desktop.admin.audit.AuditLogApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.audit.AuditLogScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.admin.inventory.RecipesRepository> { com.saporini.mobile_desktop.admin.inventory.RecipesApi(client = get()) }
    factory { com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel(repository = get(), session = get()) }
    single<com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderRepository> { com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderApi(client = get()) }
    factory { com.saporini.mobile_desktop.pos.reservations.preorder.PreOrderScreenModel(repository = get(), session = get()) }

    // Super admins: the restaurant list, the Restaurant app (also the owner's Admin Hub → Restaurant info) and Co-Super-Admins.
    single<com.saporini.mobile_desktop.platform.PlatformRepository> { com.saporini.mobile_desktop.platform.PlatformApi(client = get()) }
    factory { com.saporini.mobile_desktop.platform.RestaurantListScreenModel(repository = get(), people = get(), session = get()) }
    factory { com.saporini.mobile_desktop.platform.BranchListScreenModel(repository = get(), session = get()) }
    factory { com.saporini.mobile_desktop.platform.RestaurantApplicationsScreenModel(repository = get(), session = get()) }
    factory { com.saporini.mobile_desktop.platform.CoSuperAdminsScreenModel(people = get(), platform = get(), session = get()) }
    factory { com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel(repository = get(), people = get(), session = get()) }

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
