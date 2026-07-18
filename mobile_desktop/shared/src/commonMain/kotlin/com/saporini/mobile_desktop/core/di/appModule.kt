package com.saporini.mobile_desktop.core.di

import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreenModel
import com.saporini.mobile_desktop.core.network.createHttpClient
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

private val appModule = module {
    single { SessionManager() }
    single { createHttpClient(get()) }
    single { AuthRepository(client = get()) }
    factory { LoginScreenModel(get(), get()) }
    factory { WorkspaceScreenModel(get(), get()) }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
