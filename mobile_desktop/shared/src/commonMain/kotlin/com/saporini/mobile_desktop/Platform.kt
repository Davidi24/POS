package com.saporini.mobile_desktop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform