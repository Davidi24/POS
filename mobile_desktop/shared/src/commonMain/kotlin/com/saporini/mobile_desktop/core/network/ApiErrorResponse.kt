package com.saporini.mobile_desktop.core.network

import kotlinx.serialization.Serializable

/**
 * The server's error: its status and message, and for a refused sign-in why ([code]), when locked until when, for an ended
 * sign-in why it ended ([reason]), and at the device limit the person's signed-in devices ([devices]).
 */
@Serializable
data class ApiErrorResponse(
    val status: Int,
    val message: String,
    val code: String? = null,
    val lockedUntil: String? = null,
    val reason: String? = null,
    val devices: List<SignedInDevice>? = null
)

/** One of the person's signed-in devices: what it's called, when it was last used and when it signed in. */
@Serializable
data class SignedInDevice(
    val id: String,
    val name: String? = null,
    val lastUsedAt: String? = null,
    val signedInAt: String? = null,
    // What it runs, where its address is, and whether it's on the network of this sign-in (2026-11-25).
    val system: String? = null,
    val place: String? = null,
    val sameNetwork: Boolean = false
)
