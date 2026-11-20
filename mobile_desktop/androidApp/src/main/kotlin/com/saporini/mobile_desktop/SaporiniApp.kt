package com.saporini.mobile_desktop

import android.app.Application
import com.saporini.mobile_desktop.core.di.initKoin

class SaporiniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
