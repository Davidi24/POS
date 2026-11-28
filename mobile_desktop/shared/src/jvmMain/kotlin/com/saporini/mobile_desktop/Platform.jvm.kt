package com.saporini.mobile_desktop

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val system: String = System.getProperty("os.name") ?: "Computer"
    override val systemVersion: String? = System.getProperty("os.version")
}

actual fun getPlatform(): Platform = JVMPlatform()