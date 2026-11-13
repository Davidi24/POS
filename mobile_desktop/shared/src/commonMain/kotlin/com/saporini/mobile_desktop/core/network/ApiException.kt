package com.saporini.mobile_desktop.core.network

class ApiException(
    val status: Int,
    override val message: String
) : Exception(message)