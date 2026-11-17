package com.saporini.mobile_desktop.core.network

import kotlinx.serialization.Serializable

/** The server's error: its status and message, and for a refused sign-in why ([code]) and, when locked, until when. */
@Serializable
data class ApiErrorResponse(
    val status: Int,
    val message: String,
    val code: String? = null,
    val lockedUntil: String? = null
)
