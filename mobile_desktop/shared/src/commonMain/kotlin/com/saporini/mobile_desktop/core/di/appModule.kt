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

private val appModule = module {
    single { SessionManager() }
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
            sessionManager = get()
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
